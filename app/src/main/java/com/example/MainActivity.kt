package com.example

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsOverscan
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.HostStreamStatus
import com.example.model.PendingPinRequest
import com.example.ui.screens.CommandDeckScreen
import com.example.ui.screens.DisplayCodecStudioScreen
import com.example.ui.screens.MoonlightClientsScreen
import com.example.ui.screens.RootKernelScreen
import com.example.ui.theme.MyApplicationTheme
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
import com.example.viewmodel.SolisHostViewModel
import com.example.viewmodel.SolisNavTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SolisHostApp()
            }
        }
    }
}

@Composable
fun SolisHostApp(
    viewModel: SolisHostViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val config by viewModel.hostConfig.collectAsStateWithLifecycle()
    val pairedClients by viewModel.pairedClients.collectAsStateWithLifecycle()
    val hardwareEncoders by viewModel.hardwareEncoders.collectAsStateWithLifecycle()
    val rootReport by viewModel.rootReport.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val pendingPin by viewModel.pendingPinRequest.collectAsStateWithLifecycle()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()

    // BackHandler for non-home tabs or when OLED Blanker is active
    BackHandler(enabled = telemetry.isPhoneOledBlanked || selectedTab != SolisNavTab.COMMAND_DECK) {
        if (telemetry.isPhoneOledBlanked) {
            viewModel.togglePhoneOledBlanking(false)
        } else {
            viewModel.selectTab(SolisNavTab.COMMAND_DECK)
        }
    }

    // MediaProjection launcher for non-root or standard capture fallback
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startHostStream(
                mediaProjectionResultCode = result.resultCode,
                mediaProjectionData = result.data
            )
        } else {
            // Start in virtual display surface fallback if user cancels or runs in headless test
            viewModel.startHostStream()
        }
    }

    val handleToggleStream: () -> Unit = {
        if (telemetry.status == HostStreamStatus.STREAMING_ACTIVE) {
            viewModel.stopHostStream()
        } else if (viewModel.requiresMediaProjectionPrompt()) {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            val captureIntent = mpManager?.createScreenCaptureIntent()
            if (captureIntent != null) {
                runCatching { mediaProjectionLauncher.launch(captureIntent) }
                    .onFailure { viewModel.startHostStream() }
            } else {
                viewModel.startHostStream()
            }
        } else {
            // Root Direct SurfaceControl path — zero prompt!
            viewModel.startHostStream()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianCanvas)
        ) {
            val isExpandedScreen = maxWidth >= 680.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = ObsidianCanvas,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    SolisTopBar(
                        hostName = config.hostName,
                        isStreaming = telemetry.status == HostStreamStatus.STREAMING_ACTIVE,
                        rootEnabled = config.rootModeEnabled,
                        localIp = telemetry.localIpAddress
                    )
                },
                bottomBar = {
                    if (!isExpandedScreen) {
                        SolisBottomNavigationBar(
                            selectedTab = selectedTab,
                            onSelectTab = viewModel::selectTab
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        SolisNavigationRail(
                            selectedTab = selectedTab,
                            onSelectTab = viewModel::selectTab
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (selectedTab) {
                            SolisNavTab.COMMAND_DECK -> CommandDeckScreen(
                                config = config,
                                telemetry = telemetry,
                                rootReport = rootReport,
                                onToggleStream = handleToggleStream,
                                onSelectCaptureMode = viewModel::updateDisplayCaptureMode,
                                onBlankPhoneOled = { viewModel.togglePhoneOledBlanking(true) },
                                onOpenPinPairDialog = { viewModel.triggerIncomingTabletPairRequest() }
                            )

                            SolisNavTab.DISPLAY_CODEC -> DisplayCodecStudioScreen(
                                config = config,
                                hardwareEncoders = hardwareEncoders,
                                onSelectCaptureMode = viewModel::updateDisplayCaptureMode,
                                onSelectResolutionPreset = viewModel::updateResolutionPreset,
                                onSelectRefreshRate = viewModel::updateRefreshRate,
                                onUpdateVirtualDpi = viewModel::updateVirtualDpi,
                                onSelectCodecProfile = viewModel::updateCodecProfile,
                                onUpdateBitrateMbps = viewModel::updateBitrateMbps,
                                onUpdateFecPercentage = viewModel::updateFecPercentage,
                                onUpdateLowLatencyCbr = viewModel::updateLowLatencyCbr
                            )

                            SolisNavTab.MOONLIGHT_CLIENTS -> MoonlightClientsScreen(
                                hostName = config.hostName,
                                telemetry = telemetry,
                                pairedClients = pairedClients,
                                onTriggerPinPairing = { viewModel.triggerIncomingTabletPairRequest() },
                                onConnectClient = viewModel::connectToPairedClient,
                                onRemoveClient = viewModel::removePairedClient
                            )

                            SolisNavTab.ROOT_ENGINE -> RootKernelScreen(
                                config = config,
                                rootReport = rootReport,
                                diagnosticLogs = diagnosticLogs,
                                onToggleRootMaster = { viewModel.updateRootMode(rootEnabled = it) },
                                onToggleDirectSurface = { viewModel.updateRootMode(directSurface = it) },
                                onToggleUinput = { viewModel.updateRootMode(uinputEnabled = it) },
                                onToggleAutoBlankOled = { viewModel.updateRootMode(autoBlankOled = it) },
                                onToggleGovernorLock = { viewModel.updateRootMode(lockGovernor = it) },
                                onReprobeRoot = viewModel::refreshRootCapabilities,
                                onTestTouchEvent = { viewModel.testReturnTouchOrStylusEvent(isStylus = false) },
                                onTestStylusEvent = { viewModel.testReturnTouchOrStylusEvent(isStylus = true) },
                                onBlankPhoneOledNow = { viewModel.togglePhoneOledBlanking(true) },
                                onClearLogs = viewModel::clearDiagnosticLogs
                            )
                        }
                    }
                }
            }
        }

        // Moonlight 4-Digit PIN Pairing Modal
        val activePinRequest = pendingPin
        if (activePinRequest != null) {
            MoonlightPinPairingModal(
                request = activePinRequest,
                onConfirmPin = { pin, clientName ->
                    viewModel.verifyAndPairMoonlightClient(pin, clientName)
                },
                onDismiss = viewModel::dismissPinDialog
            )
        }

        // Fullscreen Pure #000000 OLED Physical Screen Blanker Curtain while streaming to 10" Tablet
        if (telemetry.isPhoneOledBlanked) {
            OledPhysicalBlankerCurtain(
                resolutionLabel = telemetry.activeResolution,
                onWakeScreen = { viewModel.togglePhoneOledBlanking(false) }
            )
        }
    }
}

@Composable
private fun SolisTopBar(
    hostName: String,
    isStreaming: Boolean,
    rootEnabled: Boolean,
    localIp: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SolarAmber.copy(alpha = 0.18f))
                    .border(1.dp, SolarAmber, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = SolarAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "SolisHost",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "$hostName • $localIp",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rootEnabled) {
                Surface(
                    color = RootEmerald.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RootEmerald)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = RootEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "ROOT",
                            style = MaterialTheme.typography.labelSmall,
                            color = RootEmerald
                        )
                    }
                }
            }

            Surface(
                color = if (isStreaming) SolarAmber.copy(alpha = 0.2f) else ObsidianSurfaceVariant,
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isStreaming) SolarAmber else OutlineSubtle
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = if (isStreaming) SolarAmber else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isStreaming) "ON AIR" else "READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStreaming) SolarAmber else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SolisBottomNavigationBar(
    selectedTab: SolisNavTab,
    onSelectTab: (SolisNavTab) -> Unit
) {
    NavigationBar(
        containerColor = ObsidianSurface,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("solis_bottom_nav")
    ) {
        val items = listOf(
            Triple(SolisNavTab.COMMAND_DECK, R.string.nav_command_deck, Icons.Default.Dashboard),
            Triple(SolisNavTab.DISPLAY_CODEC, R.string.nav_display_studio, Icons.Default.SettingsOverscan),
            Triple(SolisNavTab.MOONLIGHT_CLIENTS, R.string.nav_moonlight_clients, Icons.Default.Devices),
            Triple(SolisNavTab.ROOT_ENGINE, R.string.nav_root_kernel, Icons.Default.Security)
        )
        items.forEach { (tab, labelRes, icon) ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(labelRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ObsidianCanvas,
                    selectedTextColor = SolarAmber,
                    indicatorColor = SolarAmber,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun SolisNavigationRail(
    selectedTab: SolisNavTab,
    onSelectTab: (SolisNavTab) -> Unit
) {
    NavigationRail(
        containerColor = ObsidianSurface,
        modifier = Modifier.fillMaxHeight()
    ) {
        val items = listOf(
            Triple(SolisNavTab.COMMAND_DECK, R.string.nav_command_deck, Icons.Default.Dashboard),
            Triple(SolisNavTab.DISPLAY_CODEC, R.string.nav_display_studio, Icons.Default.SettingsOverscan),
            Triple(SolisNavTab.MOONLIGHT_CLIENTS, R.string.nav_moonlight_clients, Icons.Default.Devices),
            Triple(SolisNavTab.ROOT_ENGINE, R.string.nav_root_kernel, Icons.Default.Security)
        )
        items.forEach { (tab, labelRes, icon) ->
            NavigationRailItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(labelRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = ObsidianCanvas,
                    selectedTextColor = SolarAmber,
                    indicatorColor = SolarAmber,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                ),
                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun MoonlightPinPairingModal(
    request: PendingPinRequest,
    onConfirmPin: (pin: String, clientName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinCode by remember(request.requestId) { mutableStateOf("") }
    var tabletName by remember(request.requestId) { mutableStateOf(request.clientDeviceName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Pair 10\" Tablet via Moonlight PIN",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = request.expectedPinHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TelemetryCyan
                )
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(4)
                        pinCode = digits
                    },
                    label = { Text("4-Digit PIN Shown on Tablet") },
                    placeholder = { Text("Enter 4 digits from Moonlight") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_code_input")
                )
                OutlinedTextField(
                    value = tabletName,
                    onValueChange = { tabletName = it },
                    label = { Text("Tablet Client Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_client_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPin(pinCode, tabletName) },
                enabled = pinCode.length == 4,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SolarAmber,
                    contentColor = Color(0xFF1A0E00)
                ),
                modifier = Modifier.testTag("confirm_pin_pair_button")
            ) {
                Text("Verify & Pair Tablet")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_pin_pair_button")
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun OledPhysicalBlankerCurtain(
    resolutionLabel: String,
    onWakeScreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onWakeScreen() },
                    onLongPress = { onWakeScreen() }
                )
            }
            .testTag("oled_blanker_curtain"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background( Color(0xFF0A0D12) ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "OLED PANEL BLANKED • STREAMING TO 10\" TABLET ($resolutionLabel)",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted.copy(alpha = 0.55f)
            )
            TextButton(
                onClick = onWakeScreen,
                modifier = Modifier.testTag("restore_oled_screen_button")
            ) {
                Text(
                    text = "Double-Tap or Tap Here to Restore Phone Screen",
                    style = MaterialTheme.typography.labelSmall,
                    color = SolarAmber.copy(alpha = 0.65f)
                )
            }
        }
    }
}
