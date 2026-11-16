package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HostConfigEntity
import com.example.model.DisplayCaptureMode
import com.example.model.HostStreamStatus
import com.example.model.RootCapabilityReport
import com.example.model.StreamTelemetry
import com.example.model.TabletResolutionPreset
import com.example.model.VideoCodecProfile
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.RootEmerald
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CommandDeckScreen(
    config: HostConfigEntity,
    telemetry: StreamTelemetry,
    rootReport: RootCapabilityReport,
    onToggleStream: () -> Unit,
    onSelectCaptureMode: (DisplayCaptureMode) -> Unit,
    onBlankPhoneOled: () -> Unit,
    onOpenPinPairDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStreaming = telemetry.status == HostStreamStatus.STREAMING_ACTIVE
    val activeMode = runCatching { DisplayCaptureMode.valueOf(config.captureModeName) }
        .getOrDefault(DisplayCaptureMode.VIRTUAL_EXTENDED)
    val activePreset = runCatching { TabletResolutionPreset.valueOf(config.resolutionPresetName) }
        .getOrDefault(TabletResolutionPreset.WUXGA_1920_1200)
    val activeCodec = runCatching { VideoCodecProfile.valueOf(config.codecProfileName) }
        .getOrDefault(VideoCodecProfile.HEVC_H265)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("command_deck_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Topology & Stream Ignition Card
        item {
            HeroCommandCard(
                isStreaming = isStreaming,
                hostName = config.hostName,
                telemetry = telemetry,
                activeMode = activeMode,
                activePreset = activePreset,
                activeCodec = activeCodec,
                refreshRateHz = config.refreshRateHz,
                rootModeEnabled = config.rootModeEnabled,
                onToggleStream = onToggleStream,
                onBlankPhoneOled = onBlankPhoneOled,
                onOpenPinPairDialog = onOpenPinPairDialog
            )
        }

        // 2. Instant Display Topology Switcher (Mirror vs 10" Extended Virtual Display)
        item {
            CaptureTopologyQuickSwitch(
                selectedMode = activeMode,
                presetLabel = "${activePreset.width}×${activePreset.height} (${activePreset.aspectBadge})",
                onSelectMode = onSelectCaptureMode
            )
        }

        // 3. Live Hardware Encoder & RTP Telemetry HUD
        item {
            LiveTelemetryHudCard(
                isStreaming = isStreaming,
                telemetry = telemetry,
                targetBitrateMbps = config.targetBitrateMbps,
                refreshRateHz = config.refreshRateHz,
                codecBadge = activeCodec.shortBadge
            )
        }

        // 4. Root Mode Privileged Pipeline Status Strip
        item {
            RootPipelineSummaryCard(
                config = config,
                rootReport = rootReport
            )
        }
    }
}

@Composable
private fun HeroCommandCard(
    isStreaming: Boolean,
    hostName: String,
    telemetry: StreamTelemetry,
    activeMode: DisplayCaptureMode,
    activePreset: TabletResolutionPreset,
    activeCodec: VideoCodecProfile,
    refreshRateHz: Int,
    rootModeEnabled: Boolean,
    onToggleStream: () -> Unit,
    onBlankPhoneOled: () -> Unit,
    onOpenPinPairDialog: () -> Unit
) {
    val borderBrush = if (isStreaming) {
        Brush.linearGradient(listOf(SolarAmber, TelemetryCyan))
    } else {
        Brush.linearGradient(listOf(OutlineSubtle, ObsidianSurfaceElevated))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderBrush, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_topology_1794788570170),
                    contentDescription = stringResource(R.string.hero_banner_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianCanvas.copy(alpha = 0.25f),
                                    ObsidianCanvas.copy(alpha = 0.72f),
                                    ObsidianSurface
                                )
                            )
                        )
                )

                // Top Status Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isStreaming) SolarAmber.copy(alpha = 0.2f) else ObsidianCanvas.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isStreaming) SolarAmber else OutlineSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isStreaming) SolarAmber else TelemetryCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isStreaming) {
                                    stringResource(R.string.host_status_active)
                                } else {
                                    stringResource(R.string.host_status_idle)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isStreaming) SolarAmber else TextPrimary
                            )
                        }
                    }

                    if (rootModeEnabled) {
                        Surface(
                            color = RootEmerald.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RootEmerald)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Root Mode Active",
                                    tint = RootEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ROOT HW ACCEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RootEmerald
                                )
                            }
                        }
                    }
                }

                // Bottom overlay inside hero banner
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "$hostName • ${telemetry.localIpAddress}:${telemetry.sunshineHttpPort}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TelemetryCyan
                    )
                    Text(
                        text = "${activeMode.title} (${activePreset.width}×${activePreset.height} @ ${refreshRateHz}Hz • ${activeCodec.shortBadge})",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Start / Stop Host Button
                Button(
                    onClick = onToggleStream,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("toggle_host_stream_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming) MaterialTheme.colorScheme.error else SolarAmber,
                        contentColor = if (isStreaming) Color.White else Color(0xFF1A0E00)
                    )
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isStreaming) {
                            "Stop Sunshine Host Stream"
                        } else {
                            "Start Low-Latency Sunshine Host"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Secondary Action Row: Pair Tablet via PIN + Blank Phone OLED
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenPinPairDialog,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("quick_pair_pin_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = TelemetryCyan
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pin,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_pair_pin),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onBlankPhoneOled,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("blank_phone_oled_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSubtle)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = SolarAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_blank_oled),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureTopologyQuickSwitch(
    selectedMode: DisplayCaptureMode,
    presetLabel: String,
    onSelectMode: (DisplayCaptureMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISPLAY CAPTURE TOPOLOGY",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = presetLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DisplayCaptureMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) SolarAmber.copy(alpha = 0.16f) else ObsidianSurfaceVariant,
                        label = "modeBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) SolarAmber else OutlineSubtle,
                        label = "modeBorder"
                    )
                    val tag = if (mode == DisplayCaptureMode.VIRTUAL_EXTENDED) {
                        "quick_mode_extended"
                    } else {
                        "quick_mode_mirror"
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                            .clickable { onSelectMode(mode) }
                            .padding(12.dp)
                            .testTag(tag)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (mode == DisplayCaptureMode.VIRTUAL_EXTENDED) {
                                        Icons.Default.DesktopWindows
                                    } else {
                                        Icons.Default.ScreenShare
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) SolarAmber else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (mode == DisplayCaptureMode.VIRTUAL_EXTENDED) {
                                        "10\" Extended"
                                    } else {
                                        "Phone Mirror"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) SolarAmber else TextPrimary
                                )
                            }
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryHudCard(
    isStreaming: Boolean,
    telemetry: StreamTelemetry,
    targetBitrateMbps: Int,
    refreshRateHz: Int,
    codecBadge: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = TelemetryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HARDWARE ENCODER & RTP TELEMETRY",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (isStreaming) "UDP :${telemetry.rtpVideoUdpPort} LIVE" else "STANDBY",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isStreaming) RootEmerald else TextMuted
                )
            }

            // 4-Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricTile(
                    label = "ENCODE TIME",
                    value = if (isStreaming) "${"%.2f".format(telemetry.encodeLatencyMs)} ms" else "2.10 ms",
                    subLabel = "COLOR_FormatSurface",
                    accentColor = SolarAmber,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = "BITRATE ($codecBadge)",
                    value = if (isStreaming) "${"%.1f".format(telemetry.instantBitrateMbps)} Mbps" else "$targetBitrateMbps Mbps",
                    subLabel = "FEC Parity ${telemetry.fecParityPercent}%",
                    accentColor = TelemetryCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricTile(
                    label = "FRAME PACING",
                    value = if (isStreaming) "${telemetry.currentFps} FPS" else "$refreshRateHz Hz Target",
                    subLabel = "Disp ID: ${telemetry.virtualDisplayId}",
                    accentColor = RootEmerald,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = "UINPUT RETURN",
                    value = if (isStreaming) "${telemetry.returnInputRateHz} Hz" else "120 Hz Ready",
                    subLabel = "Touch & Stylus Pen",
                    accentColor = SolarAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            // Frame-Time Sparkline Canvas
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SoC Encoder: ${telemetry.activeEncoderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Packets: ${telemetry.packetsSent}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                }

                FrameTimeSparkline(
                    samples = telemetry.frameTimeHistoryMs,
                    isActive = isStreaming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricTile(
    label: String,
    value: String,
    subLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            text = subLabel,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FrameTimeSparkline(
    samples: List<Float>,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isActive) SolarAmber else TelemetryCyan.copy(alpha = 0.65f)
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        val path = Path()
        samples.forEachIndexed { idx, ms ->
            val normalized = ((ms - 1.0f) / 3.5f).coerceIn(0.08f, 0.92f)
            val x = idx * stepX
            val y = h - (normalized * h)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Baseline reference lines
        drawLine(
            color = OutlineSubtle,
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = 1f
        )

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun RootPipelineSummaryCard(
    config: HostConfigEntity,
    rootReport: RootCapabilityReport
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (config.rootModeEnabled) RootEmerald else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PRIVILEGED ROOT PIPELINE STATUS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (config.rootModeEnabled) "ENABLED" else "STANDARD MODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (config.rootModeEnabled) RootEmerald else TextMuted
                )
            }

            Text(
                text = "${rootReport.uidSummary} • ${rootReport.kernelRelease}",
                style = MaterialTheme.typography.labelSmall,
                color = TelemetryCyan
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrivilegePill(
                    icon = Icons.Default.CastConnected,
                    title = "SurfaceControl",
                    enabled = config.rootModeEnabled && config.rootDirectSurfaceControl,
                    modifier = Modifier.weight(1f)
                )
                PrivilegePill(
                    icon = Icons.Default.TouchApp,
                    title = "/dev/uinput",
                    enabled = config.rootModeEnabled && config.rootUinputInjection,
                    modifier = Modifier.weight(1f)
                )
                PrivilegePill(
                    icon = Icons.Default.Memory,
                    title = "Perf Lock",
                    enabled = config.rootModeEnabled && config.rootLockCpuGovernor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PrivilegePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) RootEmerald.copy(alpha = 0.14f) else ObsidianSurfaceVariant)
            .border(
                1.dp,
                if (enabled) RootEmerald.copy(alpha = 0.6f) else OutlineSubtle,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) RootEmerald else TextMuted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) RootEmerald else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
