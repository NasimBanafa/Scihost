package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

data class PairingSessionState(
    val clientUniqueId: String,
    val clientIp: String,
    var saltBytes: ByteArray = ByteArray(16),
    var clientCertPemHex: String = "",
    var aesKeySha256: ByteArray = ByteArray(16),
    var decryptedClientChallenge: ByteArray = ByteArray(16),
    var serverSecret: ByteArray = ByteArray(16),
    var serverChallenge: ByteArray = ByteArray(16)
)

class SunshineCryptoManager(private val context: Context) {

    private val secureRandom = SecureRandom()
    val serverCertificate: X509Certificate
    val serverPrivateKey: PrivateKey
    val serverCertPem: String
    val serverCertPemHex: String

    private val sessions = ConcurrentHashMap<String, PairingSessionState>()

    @Volatile
    private var lastActiveSession: PairingSessionState? = null

    init {
        val certFile = File(context.filesDir, "solishost_sunshine_cert_v3.der")
        val keyFile = File(context.filesDir, "solishost_sunshine_key_v3.pkcs8")

        if (certFile.exists() && keyFile.exists()) {
            val loaded = runCatching {
                val certBytes = certFile.readBytes()
                val keyBytes = keyFile.readBytes()
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                Pair(cert, priv)
            }.getOrNull()

            if (loaded != null) {
                serverCertificate = loaded.first
                serverPrivateKey = loaded.second
            } else {
                val generated = generateSelfSignedX509Certificate()
                serverCertificate = generated.first
                serverPrivateKey = generated.second
                runCatching {
                    certFile.writeBytes(serverCertificate.encoded)
                    keyFile.writeBytes(serverPrivateKey.encoded)
                }
            }
        } else {
            val generated = generateSelfSignedX509Certificate()
            serverCertificate = generated.first
            serverPrivateKey = generated.second
            runCatching {
                certFile.writeBytes(serverCertificate.encoded)
                keyFile.writeBytes(serverPrivateKey.encoded)
            }
        }

        val base64Cert = Base64.encodeToString(serverCertificate.encoded, Base64.NO_WRAP)
            .chunked(64)
            .joinToString("\n")
        serverCertPem = "-----BEGIN CERTIFICATE-----\n$base64Cert\n-----END CERTIFICATE-----\n"
        serverCertPemHex = bytesToHex(serverCertPem.toByteArray(Charsets.UTF_8))
    }

    /**
     * Creates an SSLContext backed by a direct X509ExtendedKeyManager that unconditionally serves
     * our Sunshine X.509 certificate and RSA-2048 private key across all Android TLS versions.
     */
    fun createServerSslContext(): SSLContext {
        val directKeyManager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> =
                arrayOf("sunshine")

            override fun chooseClientAlias(
                keyType: Array<out String>?,
                issuers: Array<out Principal>?,
                socket: Socket?
            ): String = "sunshine"

            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> =
                arrayOf("sunshine")

            override fun chooseServerAlias(
                keyType: String?,
                issuers: Array<out Principal>?,
                socket: Socket?
            ): String = "sunshine"

            override fun chooseEngineServerAlias(
                keyType: String?,
                issuers: Array<out Principal>?,
                engine: SSLEngine?
            ): String = "sunshine"

            override fun getCertificateChain(alias: String?): Array<X509Certificate> =
                arrayOf(serverCertificate)

            override fun getPrivateKey(alias: String?): PrivateKey = serverPrivateKey
        }

        val trustAllClients = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        return SSLContext.getInstance("TLS").apply {
            init(arrayOf<KeyManager>(directKeyManager), trustAllClients, secureRandom)
        }
    }

    /**
     * Phase 1 of Moonlight pairing: stores client salt + cert, derives AES-128 key from (salt + 4-digit PIN),
     * and returns the server's PEM certificate in hex inside <plaincert>.
     */
    fun processGetServerCert(
        clientUniqueId: String,
        clientIp: String,
        saltHex: String,
        clientCertHex: String,
        pinCode: String
    ): String {
        val rawSalt = hexToBytes(saltHex)
        val saltBytes = ByteArray(16)
        System.arraycopy(rawSalt, 0, saltBytes, 0, minOf(rawSalt.size, 16))

        val aesKey = deriveAesKeySha256(saltBytes, pinCode)
        val session = PairingSessionState(
            clientUniqueId = clientUniqueId,
            clientIp = clientIp,
            saltBytes = saltBytes,
            clientCertPemHex = clientCertHex,
            aesKeySha256 = aesKey
        )
        sessions[clientUniqueId] = session
        sessions[clientIp] = session
        lastActiveSession = session

        return """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><paired>1</paired><plaincert>$serverCertPemHex</plaincert></root>"""
    }

    /**
     * Phase 2 of Moonlight pairing: decrypts clientchallenge, hashes (decryptedChallenge + serverCert.signature + serverSecret),
     * appends a 16-byte serverChallenge, encrypts with AES-128-ECB, and returns <challengeresponse>.
     */
    fun processClientChallenge(
        clientUniqueId: String,
        clientIp: String,
        clientChallengeHex: String
    ): String {
        val session = sessions[clientUniqueId]
            ?: sessions[clientIp]
            ?: lastActiveSession
            ?: PairingSessionState(
                clientUniqueId = clientUniqueId,
                clientIp = clientIp
            )
        val encryptedChallenge = hexToBytes(clientChallengeHex)
        val decryptedChallenge = aesEcbDecrypt(encryptedChallenge, session.aesKeySha256)
        session.decryptedClientChallenge = decryptedChallenge

        val serverSecret = ByteArray(16).also { secureRandom.nextBytes(it) }
        val serverChallenge = ByteArray(16).also { secureRandom.nextBytes(it) }
        session.serverSecret = serverSecret
        session.serverChallenge = serverChallenge
        lastActiveSession = session

        // GameStream Gen 7 (GfeVersion >= 3.20): SHA-256(decryptedClientChallenge || serverCert.signature || serverSecret)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(decryptedChallenge)
        md.update(serverCertificate.signature)
        md.update(serverSecret)
        val responseHash = md.digest() // 32 bytes

        val plainResponse = ByteArray(responseHash.size + 16)
        System.arraycopy(responseHash, 0, plainResponse, 0, responseHash.size)
        System.arraycopy(serverChallenge, 0, plainResponse, responseHash.size, 16)

        val encryptedResponse = aesEcbEncrypt(plainResponse, session.aesKeySha256)
        val responseHex = bytesToHex(encryptedResponse)

        return """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><paired>1</paired><challengeresponse>$responseHex</challengeresponse></root>"""
    }

    /**
     * Phase 3 of Moonlight pairing: signs serverSecret with our RSA-2048 PrivateKey (SHA256withRSA)
     * and returns <pairingsecret> = hex(serverSecret_16B || rsaSignature_256B).
     */
    fun processServerChallengeResp(
        clientUniqueId: String,
        clientIp: String
    ): String {
        val session = sessions[clientUniqueId] ?: sessions[clientIp] ?: lastActiveSession
        val serverSecret = session?.serverSecret ?: ByteArray(16).also { secureRandom.nextBytes(it) }

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(serverPrivateKey)
        signer.update(serverSecret)
        val signatureBytes = signer.sign()

        val pairingSecret = ByteArray(serverSecret.size + signatureBytes.size)
        System.arraycopy(serverSecret, 0, pairingSecret, 0, serverSecret.size)
        System.arraycopy(signatureBytes, 0, pairingSecret, serverSecret.size, signatureBytes.size)

        return """<?xml version="1.0" encoding="utf-8"?><root status_code="200"><paired>1</paired><pairingsecret>${bytesToHex(pairingSecret)}</pairingsecret></root>"""
    }

    fun updateActiveSessionPin(pinCode: String) {
        for (session in sessions.values) {
            session.aesKeySha256 = deriveAesKeySha256(session.saltBytes, pinCode)
        }
        lastActiveSession?.let {
            it.aesKeySha256 = deriveAesKeySha256(it.saltBytes, pinCode)
        }
    }

    private fun deriveAesKeySha256(salt16: ByteArray, pinCode: String): ByteArray {
        val pinBytes = pinCode.toByteArray(Charsets.US_ASCII)
        val combined = ByteArray(salt16.size + pinBytes.size)
        System.arraycopy(salt16, 0, combined, 0, salt16.size)
        System.arraycopy(pinBytes, 0, combined, salt16.size, pinBytes.size)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return digest.copyOfRange(0, 16)
    }

    private fun aesEcbEncrypt(plain: ByteArray, key16: ByteArray): ByteArray {
        return runCatching {
            val safeKey = if (key16.size == 16) key16 else key16.copyOf(16)
            val paddedLen = ((plain.size + 15) / 16) * 16
            val safePlain = if (plain.size == paddedLen) plain else plain.copyOf(paddedLen)
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(safeKey, "AES"))
            cipher.doFinal(safePlain)
        }.getOrElse { ByteArray(48) }
    }

    private fun aesEcbDecrypt(cipherBytes: ByteArray, key16: ByteArray): ByteArray {
        return runCatching {
            val safeKey = if (key16.size == 16) key16 else key16.copyOf(16)
            val blockLen = (cipherBytes.size / 16) * 16
            if (blockLen <= 0) return ByteArray(16)
            val safeCipher = if (cipherBytes.size == blockLen) cipherBytes else cipherBytes.copyOfRange(0, blockLen)
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(safeKey, "AES"))
            cipher.doFinal(safeCipher)
        }.getOrElse { ByteArray(16) }
    }

    /**
     * Generates a valid PNG poster image for Moonlight's App List (/appasset endpoint).
     */
    fun generateAppAssetPng(appId: String): ByteArray {
        return try {
            val width = 300
            val height = 400
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    Color.parseColor("#0B0D11"),
                    Color.parseColor("#1C2230"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (appId == "1") Color.parseColor("#FF9E2C") else Color.parseColor("#38BDF8")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRoundRect(16f, 16f, width - 16f, height - 16f, 24f, 24f, accentPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 26f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val titleLine1 = when (appId) {
                "1" -> "DESKTOP"
                "2" -> "10\" TABLET"
                else -> "PHONE PRIMARY"
            }
            val titleLine2 = when (appId) {
                "1" -> "10\" WORKSPACE"
                "2" -> "EXTENDED DISPLAY"
                else -> "SCREEN MIRROR"
            }
            canvas.drawText("SOLISHOST", width / 2f, 150f, textPaint)
            canvas.drawText(titleLine1, width / 2f, 210f, textPaint)
            canvas.drawText(titleLine2, width / 2f, 250f, textPaint)

            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            bmp.recycle()
            out.toByteArray()
        } catch (_: Throwable) {
            Base64.decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
                Base64.DEFAULT
            )
        }
    }

    /**
     * Pure Kotlin ASN.1 DER X.509v3 Self-Signed Certificate Builder (RSA 2048, SHA256withRSA)
     * including RFC 5280 BasicConstraints and KeyUsage extensions for BoringSSL TLS 1.2/1.3 compatibility.
     */
    private fun generateSelfSignedX509Certificate(): Pair<X509Certificate, PrivateKey> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, secureRandom)
        val kp = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + 10L * 365 * 86_400_000L)

        // OID 1.2.840.113549.1.1.11 (sha256WithRSAEncryption)
        val sigAlgOid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)
        val nullDer = byteArrayOf(0x05, 0x00)
        val algIdSeq = derSequence(sigAlgOid + nullDer)

        // Version v3: [0] EXPLICIT INTEGER 2
        val versionExplicit = derTag(0xA0, byteArrayOf(0x02, 0x01, 0x02))
        val serialBytes = BigInteger(64, secureRandom).abs().max(BigInteger.ONE).toByteArray()
        val serialDer = derTag(0x02, serialBytes)

        // Name: CN=NVIDIA GameStream Server
        val cnOid = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
        val cnVal = derUtf8String("NVIDIA GameStream Server")
        val rdnSet = derTag(0x31, derSequence(cnOid + cnVal))
        val nameSeq = derSequence(rdnSet)

        val utcFmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val validitySeq = derSequence(
            derUtcTime(utcFmt.format(notBefore)) + derUtcTime(utcFmt.format(notAfter))
        )

        val spkiDer = kp.public.encoded

        // X.509v3 Extensions: BasicConstraints (2.5.29.19 -> CA:FALSE) + KeyUsage (2.5.29.15 -> digitalSignature | keyEncipherment)
        val basicConstraintsExt = derSequence(
            byteArrayOf(0x06, 0x03, 0x55, 0x1D, 0x13) +
                derTag(0x04, derSequence(ByteArray(0)))
        )
        val keyUsageExt = derSequence(
            byteArrayOf(0x06, 0x03, 0x55, 0x1D, 0x0F) +
                derTag(0x04, byteArrayOf(0x03, 0x02, 0x05, 0xA0.toByte()))
        )
        val extensionsExplicit = derTag(0xA3, derSequence(basicConstraintsExt + keyUsageExt))

        val tbsBytes = derSequence(
            versionExplicit +
                serialDer +
                algIdSeq +
                nameSeq +
                validitySeq +
                nameSeq +
                spkiDer +
                extensionsExplicit
        )

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(kp.private)
        signer.update(tbsBytes)
        val signature = signer.sign()

        val bitStringBody = ByteArray(signature.size + 1)
        bitStringBody[0] = 0x00
        System.arraycopy(signature, 0, bitStringBody, 1, signature.size)
        val sigBitString = derTag(0x03, bitStringBody)

        val certDer = derSequence(tbsBytes + algIdSeq + sigBitString)
        val cf = CertificateFactory.getInstance("X.509")
        val x509 = cf.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
        return Pair(x509, kp.private)
    }

    private fun derSequence(content: ByteArray): ByteArray = derTag(0x30, content)

    private fun derUtf8String(text: String): ByteArray = derTag(0x0C, text.toByteArray(Charsets.UTF_8))

    private fun derUtcTime(utc: String): ByteArray = derTag(0x17, utc.toByteArray(Charsets.US_ASCII))

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val lenBytes = encodeDerLength(content.size)
        val result = ByteArray(1 + lenBytes.size + content.size)
        result[0] = tag.toByte()
        System.arraycopy(lenBytes, 0, result, 1, lenBytes.size)
        System.arraycopy(content, 0, result, 1 + lenBytes.size, content.size)
        return result
    }

    private fun encodeDerLength(length: Int): ByteArray {
        return when {
            length < 128 -> byteArrayOf(length.toByte())
            length < 256 -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(
                0x82.toByte(),
                ((length shr 8) and 0xFF).toByte(),
                (length and 0xFF).toByte()
            )
        }
    }

    companion object {
        fun bytesToHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
            }
            return sb.toString()
        }

        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim()
            val len = clean.length / 2
            val out = ByteArray(len)
            for (i in 0 until len) {
                val idx = i * 2
                out[i] = clean.substring(idx, idx + 2).toIntOrNull(16)?.toByte() ?: 0
            }
            return out
        }
    }
}
