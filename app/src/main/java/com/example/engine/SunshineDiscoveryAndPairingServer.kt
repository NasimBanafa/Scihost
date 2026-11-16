package com.example.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.model.DisplayCaptureMode
import com.example.model.PendingPinRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.UUID

class SunshineDiscoveryAndPairingServer(
    private val context: Context
) {
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var httpServerSocket: ServerSocket? = null
    private var httpJob: Job? = null
    private var controlUdpJob: Job? = null
    private var controlUdpSocket: DatagramSocket? = null

    val hostUuid: String = UUID.nameUUIDFromBytes("SolisHost-Sunshine-Android".toByteArray()).toString()

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

    fun startSunshineDaemon(
        hostName: String,
        httpPort: Int = 47989,
        httpsPort: Int = 47984,
        controlPort: Int = 47999,
        captureModeProvider: () -> DisplayCaptureMode,
        scope: CoroutineScope,
        onPinPairingRequested: (PendingPinRequest) -> Unit,
        onReturnInputPacket: (normX: Float, normY: Float, isStylus: Boolean, pressure: Float) -> Unit,
        onLog: (String) -> Unit
    ) {
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
                        onLog("mDNS registration warning (code=$errorCode) — direct IP pairing remains active")
                    }

                    override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                }
                nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
                nsdManager = nsd
                registrationListener = listener
            }
        } catch (t: Throwable) {
            onLog("mDNS init note: ${t.javaClass.simpleName} (LAN HTTP endpoint still active)")
        }

        // 2. Start Sunshine-compatible GameStream HTTP endpoint (/serverinfo, /applist, /pair, /launch)
        httpJob = scope.launch(Dispatchers.IO) {
            val server = runCatching { ServerSocket(httpPort) }
                .recoverCatching { ServerSocket(0) }
                .getOrNull()
            httpServerSocket = server

            if (server != null) {
                onLog("Sunshine HTTP Control Server listening on ${detectLocalLanIp()}:${server.localPort} (HTTPS: $httpsPort)")
            }

            while (isActive && server != null && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                launch(Dispatchers.IO) {
                    runCatching {
                        client.soTimeout = 3000
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val requestLine = reader.readLine() ?: ""
                        val path = requestLine.split(" ").getOrNull(1) ?: "/"
                        val remoteIp = client.inetAddress?.hostAddress ?: "192.168.1.142"

                        val xmlBody = when {
                            path.startsWith("/serverinfo") -> buildServerInfoXml(
                                hostName = hostName,
                                httpsPort = httpsPort,
                                httpPort = server.localPort
                            )
                            path.startsWith("/applist") -> buildAppListXml(captureModeProvider())
                            path.startsWith("/pair") -> {
                                val devName = extractQueryParam(path, "devicename") ?: "Moonlight 10\" Tablet"
                                onPinPairingRequested(
                                    PendingPinRequest(
                                        requestId = UUID.randomUUID().toString().take(8),
                                        clientDeviceName = devName,
                                        clientIp = remoteIp,
                                        expectedPinHint = "Enter the 4-digit PIN shown on your 10\" Tablet"
                                    )
                                )
                                """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><paired>1</paired></root>"""
                            }
                            path.startsWith("/launch") || path.startsWith("/resume") -> {
                                onLog("Moonlight client ($remoteIp) requested stream launch via $path")
                                """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><sessionUrl0>rtsp://${detectLocalLanIp()}:48010</sessionUrl0><gamesession>1</gamesession></root>"""
                            }
                            else -> buildServerInfoXml(hostName, httpsPort, server.localPort)
                        }

                        val bodyBytes = xmlBody.toByteArray(Charsets.UTF_8)
                        val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)
                        writer.write("HTTP/1.1 200 OK\r\n")
                        writer.write("Content-Type: application/xml; charset=utf-8\r\n")
                        writer.write("Content-Length: ${bodyBytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(xmlBody)
                        writer.flush()
                        client.close()
                    }
                }
            }
        }

        // 3. Start UDP Return Input / Stylus Control Listener on port 47999
        controlUdpJob = scope.launch(Dispatchers.IO) {
            val socket = runCatching { DatagramSocket(controlPort) }
                .recoverCatching { DatagramSocket(0) }
                .getOrNull()
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

    private fun extractQueryParam(fullPath: String, key: String): String? {
        val query = fullPath.substringAfter("?", "")
        if (query.isEmpty()) return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.firstOrNull().equals(key, ignoreCase = true) }
            ?.getOrNull(1)
    }

    private fun buildServerInfoXml(hostName: String, httpsPort: Int, httpPort: Int): String {
        val ip = detectLocalLanIp()
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <root status_code="200">
                <hostname>$hostName</hostname>
                <appversion>7.1.431.0</appversion>
                <GfeVersion>3.23.0.74</GfeVersion>
                <uniqueid>$hostUuid</uniqueid>
                <HttpsPort>$httpsPort</HttpsPort>
                <ExternalPort>$httpPort</ExternalPort>
                <MaxLumaPixelsHEVC>1869449984</MaxLumaPixelsHEVC>
                <ServerCodecModeSupport>259</ServerCodecModeSupport>
                <LocalIP>$ip</LocalIP>
                <PairStatus>1</PairStatus>
                <currentgame>1</currentgame>
                <state>SUNSHINE_SERVER_FREE</state>
            </root>
        """.trimIndent()
    }

    private fun buildAppListXml(activeMode: DisplayCaptureMode): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <root status_code="200">
                <App>
                    <IsHdrSupported>1</IsHdrSupported>
                    <AppTitle>${DisplayCaptureMode.VIRTUAL_EXTENDED.sunshineAppTitle}</AppTitle>
                    <ID>1</ID>
                </App>
                <App>
                    <IsHdrSupported>1</IsHdrSupported>
                    <AppTitle>${DisplayCaptureMode.MIRROR_PRIMARY.sunshineAppTitle}</AppTitle>
                    <ID>2</ID>
                </App>
                <ActiveMode>${activeMode.name}</ActiveMode>
            </root>
        """.trimIndent()
    }

    fun stopSunshineDaemon() {
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
        runCatching { httpServerSocket?.close() }
        httpServerSocket = null
        controlUdpJob?.cancel()
        controlUdpJob = null
        runCatching { controlUdpSocket?.close() }
        controlUdpSocket = null
    }
}
