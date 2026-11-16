package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.HostConfigEntity
import com.example.model.DisplayCaptureMode
import com.example.model.HardwareEncoderInfo
import com.example.model.TabletResolutionPreset
import com.example.model.VideoCodecProfile
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.RootEmerald
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun DisplayCodecStudioScreen(
    config: HostConfigEntity,
    hardwareEncoders: List<HardwareEncoderInfo>,
    onSelectCaptureMode: (DisplayCaptureMode) -> Unit,
    onSelectResolutionPreset: (TabletResolutionPreset) -> Unit,
    onSelectRefreshRate: (Int) -> Unit,
    onUpdateVirtualDpi: (Int) -> Unit,
    onSelectCodecProfile: (VideoCodecProfile) -> Unit,
    onUpdateBitrateMbps: (Int) -> Unit,
    onUpdateFecPercentage: (Int) -> Unit,
    onUpdateLowLatencyCbr: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = runCatching { DisplayCaptureMode.valueOf(config.captureModeName) }
        .getOrDefault(DisplayCaptureMode.VIRTUAL_EXTENDED)
    val activePreset = runCatching { TabletResolutionPreset.valueOf(config.resolutionPresetName) }
        .getOrDefault(TabletResolutionPreset.WUXGA_1920_1200)
    val activeCodec = runCatching { VideoCodecProfile.valueOf(config.codecProfileName) }
        .getOrDefault(VideoCodecProfile.HEVC_H265)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("display_codec_studio_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 10-Inch Tablet Display Mode & Resolution Presets
        item {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = SolarAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "10\" TABLET DISPLAY & RESOLUTION STUDIO",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "Select whether your 10-inch tablet acts as an independent 16:10 / 16:9 virtual extended display or mirrors your phone screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    // Capture Mode Radio Cards
                    DisplayCaptureMode.entries.forEach { mode ->
                        val selected = mode == activeMode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) SolarAmber.copy(alpha = 0.14f) else ObsidianSurfaceVariant)
                                .border(
                                    1.5.dp,
                                    if (selected) SolarAmber else OutlineSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectCaptureMode(mode) }
                                .padding(14.dp)
                                .testTag("studio_mode_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) SolarAmber else TextPrimary
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = SolarAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10-INCH TABLET ASPECT RATIO & RESOLUTION PRESETS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TelemetryCyan
                    )

                    TabletResolutionPreset.entries.forEach { preset ->
                        val isSelected = preset == activePreset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) TelemetryCyan.copy(alpha = 0.14f) else ObsidianSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) TelemetryCyan else OutlineSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectResolutionPreset(preset) }
                                .padding(12.dp)
                                .testTag("preset_${preset.name.lowercase()}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) TelemetryCyan else TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ObsidianSurface)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = preset.aspectBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SolarAmber
                                        )
                                    }
                                }
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "${preset.recommendedDpi} DPI",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    // Refresh Rate Chips + Virtual DPI Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Refresh Rate",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(60, 90, 120).forEach { hz ->
                                val selected = config.refreshRateHz == hz
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectRefreshRate(hz) },
                                    label = {
                                        Text(
                                            text = "$hz Hz",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SolarAmber,
                                        selectedLabelColor = ObsidianSurface
                                    ),
                                    modifier = Modifier.testTag("fps_chip_$hz")
                                )
                            }
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Virtual Secondary Display Density",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${config.virtualDpi} DPI",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan
                            )
                        }
                        Slider(
                            value = config.virtualDpi.toFloat(),
                            onValueChange = { onUpdateVirtualDpi(it.roundToInt()) },
                            valueRange = 160f..360f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = TelemetryCyan,
                                activeTrackColor = TelemetryCyan
                            )
                        )
                    }
                }
            }
        }

        // 2. Hardware Video Codec & UDP/RTP Bitrate Tuning
        item {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ADAPTIVE HARDWARE VIDEO ENCODER",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }

                    VideoCodecProfile.entries.forEach { profile ->
                        val selected = profile == activeCodec
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) SolarAmber.copy(alpha = 0.14f) else ObsidianSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (selected) SolarAmber else OutlineSubtle,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectCodecProfile(profile) }
                                .padding(12.dp)
                                .testTag("codec_${profile.shortBadge.lowercase()}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) SolarAmber else TextPrimary
                                )
                                Text(
                                    text = profile.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = profile.mimeType,
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan
                            )
                        }
                    }

                    // Bitrate Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Target Video Stream Bitrate",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${config.targetBitrateMbps} Mbps",
                                style = MaterialTheme.typography.labelMedium,
                                color = SolarAmber
                            )
                        }
                        Slider(
                            value = config.targetBitrateMbps.toFloat(),
                            onValueChange = { onUpdateBitrateMbps(it.roundToInt()) },
                            valueRange = 10f..150f,
                            colors = SliderDefaults.colors(
                                thumbColor = SolarAmber,
                                activeTrackColor = SolarAmber
                            ),
                            modifier = Modifier.testTag("bitrate_slider")
                        )
                    }

                    // FEC Parity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "UDP/RTP Forward Error Correction (FEC)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${config.fecPercentage}% Parity",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan
                            )
                        }
                        Slider(
                            value = config.fecPercentage.toFloat(),
                            onValueChange = { onUpdateFecPercentage(it.roundToInt()) },
                            valueRange = 0f..40f,
                            colors = SliderDefaults.colors(
                                thumbColor = TelemetryCyan,
                                activeTrackColor = TelemetryCyan
                            )
                        )
                    }

                    // Low-Latency CBR Mode Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strict Constant Bitrate (CBR) Sub-Frame Pacing",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Eliminates Wi-Fi bufferbloat spikes during rapid 10\" tablet scrolling",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = config.lowLatencyCbrMode,
                            onCheckedChange = onUpdateLowLatencyCbr,
                            colors = SwitchDefaults.colors(checkedThumbColor = SolarAmber)
                        )
                    }
                }
            }
        }

        // 3. Discovered SoC Hardware MediaCodec Inventory
        item {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = null,
                            tint = RootEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DETECTED SOC MEDIACODEC ENCODERS (${hardwareEncoders.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }

                    hardwareEncoders.take(6).forEach { enc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianSurfaceVariant)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = enc.codecName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${enc.mimeType} • Max ${enc.maxWidth}×${enc.maxHeight} @ ${enc.maxFrameRate}fps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (enc.isHardwareAccelerated) {
                                            RootEmerald.copy(alpha = 0.18f)
                                        } else {
                                            OutlineSubtle
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (enc.isHardwareAccelerated) "HW SURFACE" else "SW FALLBACK",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enc.isHardwareAccelerated) RootEmerald else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
