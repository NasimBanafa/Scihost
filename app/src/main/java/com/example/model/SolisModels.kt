package com.example.model

enum class DisplayCaptureMode(
    val title: String,
    val subtitle: String,
    val sunshineAppTitle: String
) {
    VIRTUAL_EXTENDED(
        title = "Virtual Extended Display",
        subtitle = "Independent 16:10 / 16:9 secondary workspace tailored for 10\" tablet",
        sunshineAppTitle = "10\" Tablet Extended Workspace"
    ),
    MIRROR_PRIMARY(
        title = "Mirror Primary Screen",
        subtitle = "Zero-copy clone of the phone's primary display panel",
        sunshineAppTitle = "Android Phone Primary Mirror"
    )
}

enum class TabletResolutionPreset(
    val label: String,
    val aspectBadge: String,
    val width: Int,
    val height: Int,
    val recommendedDpi: Int,
    val description: String
) {
    WQXGA_2560_1600(
        label = "2560 × 1600 (WQXGA)",
        aspectBadge = "16:10 Tablet Native",
        width = 2560,
        height = 1600,
        recommendedDpi = 280,
        description = "Flagship 10-inch tablet panel crispness"
    ),
    WUXGA_1920_1200(
        label = "1920 × 1200 (WUXGA)",
        aspectBadge = "16:10 Balanced",
        width = 1920,
        height = 1200,
        recommendedDpi = 240,
        description = "Ultra-low encode latency for 10-inch 16:10 tablets"
    ),
    QHD_2560_1440(
        label = "2560 × 1440 (QHD)",
        aspectBadge = "16:9 Widescreen",
        width = 2560,
        height = 1440,
        recommendedDpi = 280,
        description = "High-density 16:9 widescreen secondary monitor"
    ),
    FHD_1920_1080(
        label = "1920 × 1080 (FHD)",
        aspectBadge = "16:9 Esport",
        width = 1920,
        height = 1080,
        recommendedDpi = 220,
        description = "Maximum frame pacing stability at 120 Hz"
    )
}

enum class VideoCodecProfile(
    val title: String,
    val mimeType: String,
    val shortBadge: String,
    val description: String
) {
    HEVC_H265(
        title = "HEVC (H.265 Main10)",
        mimeType = "video/hevc",
        shortBadge = "HEVC",
        description = "40% higher compression efficiency & crisp text for 10\" tablets"
    ),
    AVC_H264(
        title = "AVC (H.264 Low-Latency)",
        mimeType = "video/avc",
        shortBadge = "H.264",
        description = "Universal hardware decode with fastest sub-frame slice pacing"
    ),
    AV1(
        title = "AV1 Hardware Main",
        mimeType = "video/av01",
        shortBadge = "AV1",
        description = "Next-gen royalty-free codec with fallback to HEVC/AVC"
    )
}

enum class HostStreamStatus {
    IDLE,
    ADVERTISING,
    PAIRING_PENDING,
    STREAMING_ACTIVE,
    ERROR
}

data class HardwareEncoderInfo(
    val codecName: String,
    val mimeType: String,
    val isHardwareAccelerated: Boolean,
    val supportsLowLatency: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFrameRate: Int
)

data class RootCapabilityReport(
    val isRootAvailable: Boolean = false,
    val uidSummary: String = "uid=10XXX(unprivileged)",
    val surfaceControlDirectCapture: Boolean = false,
    val uinputDeviceReady: Boolean = false,
    val oledPanelBlankingReady: Boolean = false,
    val cpuGovernorPerfLockReady: Boolean = false,
    val selinuxContext: String = "u:r:untrusted_app:s0",
    val kernelRelease: String = System.getProperty("os.version") ?: "Linux Android",
    val lastProbeTimeMs: Long = 0L
)

data class StreamTelemetry(
    val status: HostStreamStatus = HostStreamStatus.IDLE,
    val activeEncoderName: String = "Standby (No Active Surface)",
    val activeCodecBadge: String = "HEVC",
    val activeCaptureBackend: String = "Standby",
    val activeResolution: String = "1920×1200 @ 90Hz",
    val encodeLatencyMs: Float = 0.0f,
    val networkRttMs: Float = 0.0f,
    val currentFps: Int = 0,
    val instantBitrateMbps: Float = 0.0f,
    val packetsSent: Long = 0L,
    val fecParityPercent: Int = 20,
    val returnInputRateHz: Int = 0,
    val virtualDisplayId: Int = 0,
    val isPhoneOledBlanked: Boolean = false,
    val localIpAddress: String = "192.168.1.100",
    val sunshineHttpPort: Int = 47989,
    val sunshineHttpsPort: Int = 47984,
    val rtpVideoUdpPort: Int = 47998,
    val controlUdpPort: Int = 47999,
    val frameTimeHistoryMs: List<Float> = listOf(
        2.4f, 2.3f, 2.5f, 2.2f, 2.4f, 2.1f, 2.3f, 2.2f,
        2.4f, 2.3f, 2.1f, 2.2f, 2.5f, 2.3f, 2.2f, 2.1f
    )
)

data class PendingPinRequest(
    val requestId: String,
    val clientDeviceName: String,
    val clientIp: String,
    val expectedPinHint: String,
    val requestedAtMs: Long = System.currentTimeMillis()
)

data class KernelDiagnosticLog(
    val id: Long = System.nanoTime(),
    val timestampFormatted: String,
    val subsystem: String,
    val message: String,
    val isRootPrivilegeLog: Boolean = false,
    val isWarning: Boolean = false
)
