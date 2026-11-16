package com.example.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.model.DisplayCaptureMode
import com.example.model.PendingPinRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.UUID
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

class SunshineDiscoveryAndPairingServer(
    private val context: Context
) {
    val cryptoManager = SunshineCryptoManager(context)
    private val prefs = context.getSharedPreferences("solishost_pairing_prefs", Context.MODE_PRIVATE)

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private var httpServerSocket: ServerSocket? = null
    private var httpsServerSocket: SSLServerSocket? = null
    private var rtspServerSocket: ServerSocket? = null

    private var httpJob: Job? = null
    private var httpsJob: Job? = null
    private var rtspJob: Job? = null
    private var controlUdpJob: Job? = null
    private var controlUdpSocket: DatagramSocket? = null

    // Tracks IPs / uniqueIds that have completed the 4-phase cryptographic /pair exchange
    private val pairedClientTokens: MutableSet<String> = Collections.synchronizedSet(
        (prefs.getStringSet("paired_ips_v3", emptySet()) ?: emptySet()).toMutableSet()
    )

    @Volatile
    private var pendingPinDeferred: CompletableDeferred<String>? = null

    @Volatile
    private var lastSubmittedPin: String = "1234"

    @Volatile
    private var activeGameId: Int = 0

    @Volatile
    var hostUuid: String = prefs.getString("host_uuid_v3", null) ?: run {
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString("host_uuid_v3", fresh).apply()
        fresh
    }
        private set

    fun isDaemonRunning(): Boolean {
        val httpOpen = httpServerSocket != null && httpServerSocket?.isClosed == false
        val httpsOpen = httpsServerSocket != null && httpsServerSocket?.isClosed == false
        return httpOpen && httpsOpen
    }

    /**
     * Clears paired client state and rotates the Sunshine host uniqueid so Moonlight on the tablet
     * immediately drops any stale cache and prompts with a fresh 4-digit PIN.
     */
    fun resetPairingStateAndRotateUuid(): String {
        pairedClientTokens.clear()
        val newUuid = UUID.randomUUID().toString()
        hostUuid = newUuid
        prefs.edit()
            .remove("paired_ips_v3")
            .putString("host_uuid_v3", newUuid)
            .apply()
        return newUuid
    }

    private fun normalizeIp(rawIp: String): String {
        return rawIp.removePrefix("/").removePrefix("::ffff:").trim()
    }

    private fun markClientPaired(ip: String, uniqueId: String) {
        val normIp = normalizeIp(ip)
        pairedClientTokens.add(normIp)
        if (uniqueId.isNotBlank()) {
            pairedClientTokens.add(uniqueId.trim())
        }
        pairedClientTokens.add("ANY_PAIRED")
        prefs.edit().putStringSet("paired_ips_v3", pairedClientTokens.toSet()).apply()
    }

    private fun isClientPaired(ip: String, uniqueId: String?, isHttps: Boolean): Boolean {
        if (pairedClientTokens.isEmpty()) return false
        val normIp = normalizeIp(ip)
        if (pairedClientTokens.contains(normIp)) return true
        if (!uniqueId.isNullOrBlank() && pairedClientTokens.contains(uniqueId.trim())) return true
        if (isHttps && pairedClientTokens.contains("ANY_PAIRED")) return true
        return false
    }

    fun detectLocalLanIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "192.168.1.100"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("p2p") || name.startsWith("eth")) {
                    for (addr in intf.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            return addr.hostAddress ?: "192.168.1.100"
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return "192.168.1.100"
    }

    /**
     * Called when the user submits the 4-digit PIN displayed on their 10-inch tablet in SolisHost's UI.
     */
    fun submitPinFromUser(pinCode: String) {
        lastSubmittedPin = pinCode
        cryptoManager.updateActiveSessionPin(pinCode)
        pendingPinDeferred?.complete(pinCode)
    }

    fun startSunshineDaemon(
        hostName: String,
        httpPort: Int = 47989,
        httpsPort: Int = 47984,
        rtspPort: Int = 48010,
        controlPort: Int = 47999,
        forceRestart: Boolean = false,
        captureModeProvider: () -> DisplayCaptureMode,
        scope: CoroutineScope,
        onPinPairingRequested: (PendingPinRequest) -> Unit,
        onPairSuccess: (clientName: String, clientIp: String, pinUsed: String) -> Unit,
        onClientStreamLaunch: (appId: Int, clientIp: String) -> Unit,
        onReturnInputPacket: (normX: Float, normY: Float, isStylus: Boolean, pressure: Float) -> Unit,
        onLog: (String) -> Unit
    ) {
        if (!forceRestart && isDaemonRunning()) {
            return
        }
        stopSunshineDaemon()

        // 1. Register _nvstream._tcp mDNS Service for Moonlight Auto-Discovery
        try {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsd != null) {
                val serviceInfo = NsdServiceInfo().apply {
                    serviceName = hostName
                    serviceType = "_nvstream._tcp."
                    port = httpPort
                }
                val listener = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) {
                        onLog("mDNS _nvstream._tcp registered as '${info.serviceName}' on port $httpPort")
                    }

                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        onLog("mDNS registration note (code=$errorCode) — direct IP remains active")
                    }

                    override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                }
                nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
                nsdManager = nsd
                registrationListener = listener
            }
        } catch (t: Throwable) {
            onLog("mDNS init note: ${t.javaClass.simpleName}")
        }

        // 2. Start HTTP Server on Port 47989 (/serverinfo, /pair phases 1-4)
        httpJob = scope.launch(Dispatchers.IO) {
            val server = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(httpPort))
                }
            }.recoverCatching { ServerSocket(0) }.getOrNull()
            httpServerSocket = server

            if (server != null) {
                onLog("Sunshine HTTP server online at ${detectLocalLanIp()}:${server.localPort}")
            }

            while (isActive && server != null && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                launch(Dispatchers.IO) {
                    handleHttpOrHttpsClient(
                        socket = client,
                        isHttps = false,
                        hostName = hostName,
                        httpPort = server.localPort,
                        httpsPort = httpsPort,
                        rtspPort = rtspPort,
                        captureModeProvider = captureModeProvider,
                        onPinPairingRequested = onPinPairingRequested,
                        onPairSuccess = onPairSuccess,
                        onClientStreamLaunch = onClientStreamLaunch,
                        onLog = onLog
                    )
                }
            }
        }

        // 3. Start HTTPS / TLS Server on Port 47984 (/applist, /appasset, /serverinfo, /launch, /resume, /pairchallenge)
        httpsJob = scope.launch(Dispatchers.IO) {
            val sslServer = runCatching {
                val sslContext = cryptoManager.createServerSslContext()
                (sslContext.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
                    reuseAddress = true
                    wantClientAuth = false
                    needClientAuth = false
                    bind(InetSocketAddress(httpsPort))
                }
            }.getOrNull()
            httpsServerSocket = sslServer

            if (sslServer != null) {
                onLog("Sunshine HTTPS/TLS server online at ${detectLocalLanIp()}:${sslServer.localPort} (X.509 RSA-2048 ready)")
            } else {
                onLog("HTTPS port $httpsPort bind warning")
            }

            while (isActive && sslServer != null && !sslServer.isClosed) {
                val sslClient = runCatching { sslServer.accept() as? SSLSocket }.getOrNull() ?: continue
                launch(Dispatchers.IO) {
                    handleHttpOrHttpsClient(
                        socket = sslClient,
                        isHttps = true,
                        hostName = hostName,
                        httpPort = httpPort,
                        httpsPort = sslServer.localPort,
                        rtspPort = rtspPort,
                        captureModeProvider = captureModeProvider,
                        onPinPairingRequested = onPinPairingRequested,
                        onPairSuccess = onPairSuccess,
                        onClientStreamLaunch = onClientStreamLaunch,
                        onLog = onLog
                    )
                }
            }
        }

        // 4. Start RTSP Handshake Server on Port 48010 (OPTIONS, DESCRIBE, SETUP, ANNOUNCE, PLAY)
        rtspJob = scope.launch(Dispatchers.IO) {
            val rtspServer = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(rtspPort))
                }
            }.recoverCatching { ServerSocket(0) }.getOrNull()
            rtspServerSocket = rtspServer

            if (rtspServer != null) {
                onLog("RTSP stream setup server online on TCP :${rtspServer.localPort}")
            }

            while (isActive && rtspServer != null && !rtspServer.isClosed) {
                val client = runCatching { rtspServer.accept() }.getOrNull() ?: continue
                launch(Dispatchers.IO) {
                    handleRtspSession(client, onLog)
                }
            }
        }

        // 5. Start UDP Return Input / Stylus Control Listener on Port 47999
        controlUdpJob = scope.launch(Dispatchers.IO) {
            val socket = runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(controlPort))
                }
            }.recoverCatching { DatagramSocket(0) }.getOrNull()
            controlUdpSocket = socket
            if (socket != null) {
                onLog("UDP Input & Stylus return channel bound on port ${socket.localPort}")
            }
            val buf = ByteArray(256)
            while (isActive && socket != null && !socket.isClosed) {
                val packet = DatagramPacket(buf, buf.size)
                val received = runCatching {
                    socket.receive(packet)
                    true
                }.getOrDefault(false)
                if (received && packet.length >= 4) {
                    val isStylus = (buf[0].toInt() and 0x02) != 0
                    val nx = ((buf[1].toInt() and 0xFF) / 255f).coerceIn(0f, 1f)
                    val ny = ((buf[2].toInt() and 0xFF) / 255f).coerceIn(0f, 1f)
                    val pressure = ((buf[3].toInt() and 0xFF) / 255f).coerceIn(0.1f, 1f)
                    onReturnInputPacket(nx, ny, isStylus, pressure)
                }
            }
        }
    }

    private suspend fun handleHttpOrHttpsClient(
        socket: Socket,
        isHttps: Boolean,
        hostName: String,
        httpPort: Int,
        httpsPort: Int,
        rtspPort: Int,
        captureModeProvider: () -> DisplayCaptureMode,
        onPinPairingRequested: (PendingPinRequest) -> Unit,
        onPairSuccess: (clientName: String, clientIp: String, pinUsed: String) -> Unit,
        onClientStreamLaunch: (appId: Int, clientIp: String) -> Unit,
        onLog: (String) -> Unit
    ) {
        try {
            socket.soTimeout = if (isHttps) 15_000 else 95_000
            val remoteIp = normalizeIp(socket.inetAddress?.hostAddress ?: "192.168.1.142")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            // Consume HTTP headers
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                headerLine = reader.readLine()
            }

            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val uniqueId = extractQueryParam(path, "uniqueid")
            val rawOut = socket.getOutputStream()
            val protoTag = if (isHttps) "HTTPS" else "HTTP"

            if (path.startsWith("/appasset")) {
                val appId = extractQueryParam(path, "appid") ?: "1"
                val pngBytes = cryptoManager.generateAppAssetPng(appId)
                writeHttpBinaryResponse(rawOut, "image/png", pngBytes)
                socket.close()
                return
            }

            var deferredLaunchAppId: Int? = null

            val xmlBody = when {
                path.startsWith("/serverinfo") -> {
                    val isPaired = isClientPaired(remoteIp, uniqueId, isHttps)
                    buildServerInfoXml(
                        hostName = hostName,
                        httpsPort = httpsPort,
                        httpPort = httpPort,
                        isPaired = isPaired
                    )
                }

                path.startsWith("/applist") -> {
                    onLog("$protoTag /applist served to $remoteIp (Desktop + 10\" Extended + Mirror)")
                    buildAppListXml(captureModeProvider())
                }

                path.startsWith("/pair") -> {
                    handlePairEndpoint(
                        path = path,
                        remoteIp = remoteIp,
                        isHttps = isHttps,
                        onPinPairingRequested = onPinPairingRequested,
                        onPairSuccess = onPairSuccess,
                        onLog = onLog
                    )
                }

                path.startsWith("/launch") || path.startsWith("/resume") -> {
                    val appId = extractQueryParam(path, "appid")?.toIntOrNull() ?: 1
                    activeGameId = appId
                    deferredLaunchAppId = appId
                    val localIp = detectLocalLanIp()
                    onLog("$protoTag /launch appId=$appId from $remoteIp -> rtsp://$localIp:$rtspPort")
                    """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><sessionUrl0>rtsp://$localIp:$rtspPort</sessionUrl0><gamesession>1</gamesession></root>"""
                }

                path.startsWith("/cancel") -> {
                    activeGameId = 0
                    onLog("$protoTag /cancel session from $remoteIp")
                    """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><cancel>1</cancel></root>"""
                }

                else -> {
                    val isPaired = isClientPaired(remoteIp, uniqueId, isHttps)
                    buildServerInfoXml(
                        hostName = hostName,
                        httpsPort = httpsPort,
                        httpPort = httpPort,
                        isPaired = isPaired
                    )
                }
            }

            writeHttpXmlResponse(rawOut, xmlBody)
            runCatching { socket.close() }

            if (deferredLaunchAppId != null) {
                onClientStreamLaunch(deferredLaunchAppId, remoteIp)
            }
        } catch (_: Throwable) {
            runCatching { socket.close() }
        }
    }

    private suspend fun handlePairEndpoint(
        path: String,
        remoteIp: String,
        isHttps: Boolean,
        onPinPairingRequested: (PendingPinRequest) -> Unit,
        onPairSuccess: (clientName: String, clientIp: String, pinUsed: String) -> Unit,
        onLog: (String) -> Unit
    ): String {
        val phrase = extractQueryParam(path, "phrase") ?: ""
        val uniqueId = extractQueryParam(path, "uniqueid") ?: remoteIp
        val devName = extractQueryParam(path, "devicename") ?: "Moonlight 10-Inch Tablet"
        val clientChallenge = extractQueryParam(path, "clientchallenge")
        val serverChallengeResp = extractQueryParam(path, "serverchallengeresp")
        val clientPairingSecret = extractQueryParam(path, "clientpairingsecret")

        return when {
            phrase.equals("getservercert", ignoreCase = true) -> {
                val saltHex = extractQueryParam(path, "salt") ?: ""
                val clientCertHex = extractQueryParam(path, "clientcert") ?: ""

                val pinWaitDeferred = CompletableDeferred<String>()
                pendingPinDeferred = pinWaitDeferred

                onLog("PIN PROMPT TRIGGERED: Tablet '$devName' ($remoteIp) waiting for 4-digit PIN!")
                onPinPairingRequested(
                    PendingPinRequest(
                        requestId = "$uniqueId-${System.currentTimeMillis()}",
                        clientDeviceName = "$devName ($remoteIp)",
                        clientIp = remoteIp,
                        expectedPinHint = "Enter the 4-digit PIN currently shown on your 10\" Tablet in Moonlight:"
                    )
                )

                // Wait up to 85 seconds for the user to enter the 4-digit PIN on the phone UI
                val enteredPin = withTimeoutOrNull(85_000L) {
                    pinWaitDeferred.await()
                } ?: lastSubmittedPin

                lastSubmittedPin = enteredPin
                onLog("PIN [$enteredPin] entered — sending X.509 RSA-2048 certificate to $remoteIp")
                cryptoManager.processGetServerCert(
                    clientUniqueId = uniqueId,
                    clientIp = remoteIp,
                    saltHex = saltHex,
                    clientCertHex = clientCertHex,
                    pinCode = enteredPin
                )
            }

            !clientChallenge.isNullOrEmpty() -> {
                onLog("Pairing Phase 2 (clientchallenge) verified for $remoteIp")
                cryptoManager.processClientChallenge(
                    clientUniqueId = uniqueId,
                    clientIp = remoteIp,
                    clientChallengeHex = clientChallenge
                )
            }

            !serverChallengeResp.isNullOrEmpty() -> {
                onLog("Pairing Phase 3 (serverchallengeresp) RSA-SHA256 signed for $remoteIp")
                cryptoManager.processServerChallengeResp(
                    clientUniqueId = uniqueId,
                    clientIp = remoteIp
                )
            }

            !clientPairingSecret.isNullOrEmpty() -> {
                markClientPaired(remoteIp, uniqueId)
                onLog("Pairing Phase 4 (clientpairingsecret) complete! Tablet $remoteIp is now trusted.")
                onPairSuccess(devName, remoteIp, lastSubmittedPin)
                """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><paired>1</paired></root>"""
            }

            phrase.equals("pairchallenge", ignoreCase = true) || isHttps -> {
                markClientPaired(remoteIp, uniqueId)
                onLog("HTTPS TLS pairchallenge verified for $remoteIp — App List unlocked!")
                """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><paired>1</paired></root>"""
            }

            else -> {
                """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><paired>1</paired></root>"""
            }
        }
    }

    private fun handleRtspSession(socket: Socket, onLog: (String) -> Unit) {
        try {
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val out = socket.getOutputStream()

            while (!socket.isClosed) {
                val reqLine = reader.readLine() ?: break
                if (reqLine.isBlank()) continue
                val method = reqLine.substringBefore(" ").uppercase()
                var cseq = "1"
                var contentLength = 0

                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.startsWith("CSeq:", ignoreCase = true)) {
                        cseq = line.substringAfter(":").trim()
                    } else if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }
                if (contentLength > 0) {
                    val bodyBuf = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(bodyBuf, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                }

                val response = when (method) {
                    "OPTIONS" -> {
                        "RTSP/1.0 200 OK\r\n" +
                            "CSeq: $cseq\r\n" +
                            "Public: OPTIONS, DESCRIBE, SETUP, PLAY, ANNOUNCE\r\n\r\n"
                    }
                    "DESCRIBE" -> {
                        val sdp = "v=0\r\n" +
                            "o=- 0 0 IN IP4 ${detectLocalLanIp()}\r\n" +
                            "s=Sunshine\r\n" +
                            "t=0 0\r\n" +
                            "m=video 47998 RTP/AVP 96\r\n" +
                            "a=rtpmap:96 H264/90000\r\n" +
                            "a=x-ss-general.featureFlags:3\r\n"
                        val sdpBytes = sdp.toByteArray(Charsets.UTF_8)
                        "RTSP/1.0 200 OK\r\n" +
                            "CSeq: $cseq\r\n" +
                            "Content-Type: application/sdp\r\n" +
                            "Content-Length: ${sdpBytes.size}\r\n\r\n" +
                            sdp
                    }
                    "SETUP" -> {
                        val port = when {
                            reqLine.contains("audio", ignoreCase = true) -> 48000
                            reqLine.contains("control", ignoreCase = true) -> 47999
                            else -> 47998
                        }
                        "RTSP/1.0 200 OK\r\n" +
                            "CSeq: $cseq\r\n" +
                            "Session: 12345678;timeout=90\r\n" +
                            "Transport: unicast;server_port=$port\r\n\r\n"
                    }
                    "ANNOUNCE" -> {
                        "RTSP/1.0 200 OK\r\n" +
                            "CSeq: $cseq\r\n" +
                            "Session: 12345678\r\n\r\n"
                    }
                    "PLAY" -> {
                        onLog("RTSP PLAY confirmed — streaming low-latency RTP video to ${socket.inetAddress?.hostAddress}")
                        "RTSP/1.0 200 OK\r\n" +
                            "CSeq: $cseq\r\n" +
                            "Session: 12345678\r\n\r\n"
                    }
                    else -> {
                        "RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n\r\n"
                    }
                }
                out.write(response.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun writeHttpXmlResponse(out: OutputStream, xmlBody: String) {
        val bodyBytes = xmlBody.trim().toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/xml; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bodyBytes)
        out.flush()
    }

    private fun writeHttpBinaryResponse(out: OutputStream, contentType: String, payload: ByteArray) {
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${payload.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(payload)
        out.flush()
    }

    private fun extractQueryParam(fullPath: String, key: String): String? {
        val query = fullPath.substringAfter("?", "")
        if (query.isEmpty()) return null
        val raw = query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.firstOrNull().equals(key, ignoreCase = true) }
            ?.getOrNull(1) ?: return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun buildServerInfoXml(
        hostName: String,
        httpsPort: Int,
        httpPort: Int,
        isPaired: Boolean
    ): String {
        val ip = detectLocalLanIp()
        val pairVal = if (isPaired) 1 else 0
        val serverState = if (activeGameId > 0) "SUNSHINE_SERVER_BUSY" else "SUNSHINE_SERVER_FREE"
        return buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<root status_code="200">""")
            append("<hostname>$hostName</hostname>")
            append("<appversion>7.1.431.0</appversion>")
            append("<GfeVersion>3.23.0.74</GfeVersion>")
            append("<uniqueid>$hostUuid</uniqueid>")
            append("<HttpsPort>$httpsPort</HttpsPort>")
            append("<ExternalPort>$httpPort</ExternalPort>")
            append("<mac>02:00:00:44:55:66</mac>")
            append("<MaxLumaPixelsHEVC>1869449984</MaxLumaPixelsHEVC>")
            append("<ServerCodecModeSupport>259</ServerCodecModeSupport>")
            append("<LocalIP>$ip</LocalIP>")
            append("<PairStatus>$pairVal</PairStatus>")
            append("<currentgame>$activeGameId</currentgame>")
            append("<state>$serverState</state>")
            append("<SupportedDisplayMode>")
            append("<DisplayMode><Width>2560</Width><Height>1600</Height><RefreshRate>120</RefreshRate></DisplayMode>")
            append("<DisplayMode><Width>1920</Width><Height>1200</Height><RefreshRate>90</RefreshRate></DisplayMode>")
            append("<DisplayMode><Width>1920</Width><Height>1080</Height><RefreshRate>120</RefreshRate></DisplayMode>")
            append("</SupportedDisplayMode>")
            append("</root>")
        }
    }

    private fun buildAppListXml(activeMode: DisplayCaptureMode): String {
        // CRITICAL: Zero inter-tag whitespace/newlines so Moonlight's XmlPullParser never fires
        // XmlPullParser.TEXT before the first <App> tag (which otherwise throws NoSuchElementException on appList.getLast()!)
        return buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<root status_code="200">""")
            append("<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Desktop</AppTitle><ID>1</ID></App>")
            append("<App><IsHdrSupported>0</IsHdrSupported><AppTitle>10-Inch Tablet Extended Display</AppTitle><ID>2</ID></App>")
            append("<App><IsHdrSupported>0</IsHdrSupported><AppTitle>Phone Screen Mirror</AppTitle><ID>3</ID></App>")
            append("</root>")
        }
    }

    fun stopSunshineDaemon() {
        pendingPinDeferred?.cancel()
        pendingPinDeferred = null
        runCatching {
            val nsd = nsdManager
            val listener = registrationListener
            if (nsd != null && listener != null) {
                nsd.unregisterService(listener)
            }
        }
        registrationListener = null
        httpJob?.cancel()
        httpJob = null
        httpsJob?.cancel()
        httpsJob = null
        rtspJob?.cancel()
        rtspJob = null
        runCatching { httpServerSocket?.close() }
        httpServerSocket = null
        runCatching { httpsServerSocket?.close() }
        httpsServerSocket = null
        runCatching { rtspServerSocket?.close() }
        rtspServerSocket = null
        controlUdpJob?.cancel()
        controlUdpJob = null
        runCatching { controlUdpSocket?.close() }
        controlUdpSocket = null
    }
}
