package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.HostConfigEntity
import com.example.model.KernelDiagnosticLog
import com.example.model.RootCapabilityReport
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.RootEmerald
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RootKernelScreen(
    config: HostConfigEntity,
    rootReport: RootCapabilityReport,
    diagnosticLogs: List<KernelDiagnosticLog>,
    onToggleRootMaster: (Boolean) -> Unit,
    onToggleDirectSurface: (Boolean) -> Unit,
    onToggleUinput: (Boolean) -> Unit,
    onToggleAutoBlankOled: (Boolean) -> Unit,
    onToggleGovernorLock: (Boolean) -> Unit,
    onReprobeRoot: () -> Unit,
    onTestTouchEvent: () -> Unit,
    onTestStylusEvent: () -> Unit,
    onBlankPhoneOledNow: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("root_kernel_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Master Root Mode & Kernel Probe Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (config.rootModeEnabled) RootEmerald else SolarAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "PRIVILEGED ROOT ACCELERATION",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = rootReport.uidSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan
                                )
                            }
                        }

                        Switch(
                            checked = config.rootModeEnabled,
                            onCheckedChange = onToggleRootMaster,
                            colors = SwitchDefaults.colors(checkedThumbColor = RootEmerald),
                            modifier = Modifier.testTag("root_mode_master_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onReprobeRoot,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("probe_root_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Probe su & Kernel",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = onBlankPhoneOledNow,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("root_blank_oled_now_button"),
                            shape = RoundedCornerShape(12.dp),
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
                                text = "Blank Phone OLED",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // 2. Privileged Root Feature Toggles
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
                    Text(
                        text = "KERNEL & COMPOSITOR HOOKS",
                        style = MaterialTheme.typography.labelLarge,
                        color = SolarAmber
                    )

                    RootToggleRow(
                        title = "Low-Level SurfaceControl Display Capture",
                        subtitle = "Bypasses per-session MediaProjection consent dialogs and enables zero-copy hardware compositor surfaces",
                        checked = config.rootDirectSurfaceControl,
                        enabled = config.rootModeEnabled,
                        onCheckedChange = onToggleDirectSurface,
                        tag = "toggle_surface_control"
                    )

                    RootToggleRow(
                        title = "Native /dev/uinput Multi-Touch & Stylus Injection",
                        subtitle = "Injects 10-inch tablet return touch, hover, and pressure-sensitive stylus events directly into the virtual display ID",
                        checked = config.rootUinputInjection,
                        enabled = config.rootModeEnabled,
                        onCheckedChange = onToggleUinput,
                        tag = "toggle_uinput_injection"
                    )

                    RootToggleRow(
                        title = "Auto-Blank Physical Phone OLED While Streaming",
                        subtitle = "Calls SurfaceControl.setDisplayPowerMode(OFF) when stream starts so the phone stays cool while driving the tablet",
                        checked = config.rootAutoBlankOled,
                        enabled = config.rootModeEnabled,
                        onCheckedChange = onToggleAutoBlankOled,
                        tag = "toggle_auto_blank_oled"
                    )

                    RootToggleRow(
                        title = "CPU/GPU Performance Governor & Fixed-Perf Lock",
                        subtitle = "Locks cpufreq scaling_governor to 'performance' and enables Android fixed-performance mode to eliminate frame jitter",
                        checked = config.rootLockCpuGovernor,
                        enabled = config.rootModeEnabled,
                        onCheckedChange = onToggleGovernorLock,
                        tag = "toggle_governor_lock"
                    )

                    // Interactive uinput test buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestTouchEvent,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("test_uinput_touch_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSubtle)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Test uinput Touch",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = onTestStylusEvent,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("test_uinput_stylus_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSubtle)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Draw,
                                contentDescription = null,
                                tint = SolarAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Test Stylus Pen",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. Live Kernel & Encoder Diagnostic Console
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = RootEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ROOT & ENCODER DIAGNOSTIC LOG",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClearAll,
                                contentDescription = "Clear diagnostic logs",
                                tint = TextMuted
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianCanvas)
                            .border(1.dp, OutlineSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (diagnosticLogs.isEmpty()) {
                            Text(
                                text = "No diagnostic events logged. Tap 'Probe su & Kernel' or start the Sunshine host.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        } else {
                            diagnosticLogs.take(12).forEach { entry ->
                                Text(
                                    text = "${entry.timestampFormatted} [${entry.subsystem}] ${entry.message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.isRootPrivilegeLog) RootEmerald else TelemetryCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) TextPrimary else TextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = RootEmerald),
            modifier = Modifier.testTag(tag)
        )
    }
}
