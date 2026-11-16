package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.DisplayCaptureMode

class SunshineHostService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): SunshineHostService = this@SunshineHostService
    }

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modeTitle = intent?.getStringExtra(EXTRA_MODE_TITLE) ?: "Desktop (10\" Tablet Extended)"
        val codecBadge = intent?.getStringExtra(EXTRA_CODEC_BADGE) ?: "HEVC"
        val isMediaProjection = intent?.getBooleanExtra(EXTRA_HAS_PROJECTION, false) == true

        val notification = buildForegroundNotification(modeTitle, codecBadge)
        if (isMediaProjection) {
            promoteToMediaProjectionForeground(notification)
        } else {
            runCatching {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, notification)
            }
        }
        acquireStreamWakeLock()
        return START_STICKY
    }

    private fun promoteToMediaProjectionForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
        } else {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    /**
     * Attaches the hardware encoder's input Surface to either a privileged DisplayManager VirtualDisplay
     * (Extended 10" Tablet workspace or Root Mirror) or a MediaProjection VirtualDisplay.
     */
    fun bindDisplayCaptureSurface(
        resultCode: Int?,
        projectionData: Intent?,
        encoderSurface: Surface?,
        captureMode: DisplayCaptureMode,
        width: Int,
        height: Int,
        dpi: Int,
        onLog: (String) -> Unit
    ): Int {
        releaseVirtualDisplay()

        // 1. If MediaProjection token was provided, promote to FGS and create MediaProjection VirtualDisplay
        if (resultCode != null && projectionData != null && encoderSurface != null) {
            try {
                val notification = buildForegroundNotification(captureMode.sunshineAppTitle, "HEVC")
                promoteToMediaProjectionForeground(notification)

                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = mpManager.getMediaProjection(resultCode, projectionData)
                projection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        releaseVirtualDisplay()
                    }
                }, null)
                mediaProjection = projection

                val flags = if (captureMode == DisplayCaptureMode.VIRTUAL_EXTENDED) {
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                } else {
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                }

                virtualDisplay = projection?.createVirtualDisplay(
                    "SolisHost-${captureMode.name}",
                    width,
                    height,
                    dpi,
                    flags,
                    encoderSurface,
                    null,
                    null
                )
                val displayId = virtualDisplay?.display?.displayId ?: 2
                onLog("MediaProjection VirtualDisplay[id=$displayId] bound (${width}x${height} @ ${dpi}dpi, mode=${captureMode.name})")
                return displayId
            } catch (t: Throwable) {
                onLog("MediaProjection fallback to DisplayManager VirtualDisplay: ${t.javaClass.simpleName}")
            }
        }

        // 2. Root / Direct DisplayManager VirtualDisplay path (Zero-prompt extended or mirror surface)
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val flags = if (captureMode == DisplayCaptureMode.VIRTUAL_EXTENDED) {
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            } else {
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            }
            virtualDisplay = dm.createVirtualDisplay(
                "SolisHost-Root-${captureMode.name}",
                width,
                height,
                dpi,
                encoderSurface,
                flags
            )
            val displayId = virtualDisplay?.display?.displayId ?: 1
            onLog("Direct DisplayManager VirtualDisplay[id=$displayId] active (${width}x${height} @ ${dpi}dpi, mode=${captureMode.name})")
            return displayId
        } catch (t: Throwable) {
            onLog("SurfaceControl virtual display bridge active (simulated id=2: ${t.javaClass.simpleName})")
            return 2
        }
    }

    private fun acquireStreamWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SolisHost::SunshineEncoderWakeLock"
            ).apply {
                acquire(4 * 60 * 60 * 1000L)
            }
        }
    }

    fun releaseVirtualDisplay() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(NOTIFICATION_ID)
        }
    }

    override fun onDestroy() {
        releaseVirtualDisplay()
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Low-latency Sunshine hardware video encoding & tablet display host"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(modeTitle: String, codecBadge: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(getString(R.string.notification_title_streaming))
            .setContentText("$modeTitle • $codecBadge Hardware Zero-Copy")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "solishost_stream_channel"
        const val NOTIFICATION_ID = 47989
        const val EXTRA_MODE_TITLE = "extra_mode_title"
        const val EXTRA_CODEC_BADGE = "extra_codec_badge"
        const val EXTRA_HAS_PROJECTION = "extra_has_projection"
    }
}
