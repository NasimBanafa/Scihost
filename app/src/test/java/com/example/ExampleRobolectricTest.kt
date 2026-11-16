package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.HardwareEncoderEngine
import com.example.engine.SunshineCryptoManager
import com.example.model.DisplayCaptureMode
import com.example.model.TabletResolutionPreset
import com.example.model.VideoCodecProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read SolisHost app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SolisHost", appName)
    }

    @Test
    fun `hardware encoder engine discovers HEVC and AVC profiles`() {
        val engine = HardwareEncoderEngine()
        val encoders = engine.probeDeviceEncoders()
        assertTrue(encoders.isNotEmpty())
        assertTrue(encoders.any { it.mimeType == VideoCodecProfile.HEVC_H265.mimeType || it.mimeType == VideoCodecProfile.AVC_H264.mimeType })
    }

    @Test
    fun `tablet 16_10 WQXGA and WUXGA presets match 10 inch display aspect ratio`() {
        val wqxga = TabletResolutionPreset.WQXGA_2560_1600
        val wuxga = TabletResolutionPreset.WUXGA_1920_1200
        assertEquals(1.6f, wqxga.width.toFloat() / wqxga.height.toFloat(), 0.01f)
        assertEquals(1.6f, wuxga.width.toFloat() / wuxga.height.toFloat(), 0.01f)
        assertEquals(2, DisplayCaptureMode.entries.size)
    }

    @Test
    fun `sunshine crypto manager generates X509 cert TLS context and 4-phase pairing XML`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = SunshineCryptoManager(context)
        assertNotNull(crypto.serverCertificate)
        assertNotNull(crypto.createServerSslContext())

        val phase1Xml = crypto.processGetServerCert(
            clientUniqueId = "test-tablet",
            clientIp = "192.168.1.142",
            saltHex = "00112233445566778899aabbccddeeff",
            clientCertHex = "308201",
            pinCode = "1234"
        )
        assertTrue(phase1Xml.contains("<plaincert>"))

        val phase2Xml = crypto.processClientChallenge(
            clientUniqueId = "test-tablet",
            clientIp = "192.168.1.142",
            clientChallengeHex = "00112233445566778899aabbccddeeff"
        )
        assertTrue(phase2Xml.contains("<challengeresponse>"))

        val phase3Xml = crypto.processServerChallengeResp(
            clientUniqueId = "test-tablet",
            clientIp = "192.168.1.142"
        )
        assertTrue(phase3Xml.contains("<pairingsecret>"))

        val pngBytes = crypto.generateAppAssetPng("1")
        assertTrue(pngBytes.size > 16)
    }
}
