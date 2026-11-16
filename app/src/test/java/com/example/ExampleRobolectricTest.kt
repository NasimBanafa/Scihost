package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.HardwareEncoderEngine
import com.example.model.DisplayCaptureMode
import com.example.model.TabletResolutionPreset
import com.example.model.VideoCodecProfile
import org.junit.Assert.assertEquals
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
}
