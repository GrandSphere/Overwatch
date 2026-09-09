package com.grandsphere.overwatch.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.data.SettingsExport
import com.grandsphere.overwatch.domain.catalog.FeaturePermissions
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.security.PinHasher
import com.grandsphere.overwatch.ui.about.AboutScreen
import com.grandsphere.overwatch.ui.chrome.AppDrawer
import com.grandsphere.overwatch.ui.help.HelpScreen
import com.grandsphere.overwatch.ui.chrome.OverwatchTopBar
import com.grandsphere.overwatch.ui.detail.DetailScreen
import com.grandsphere.overwatch.ui.editor.EditorScreen
import com.grandsphere.overwatch.ui.home.IdleHomeScreen
import com.grandsphere.overwatch.ui.running.RunningScreen
import com.grandsphere.overwatch.ui.settings.SettingsScreen
import com.grandsphere.overwatch.ui.theme.OverwatchTheme
import com.grandsphere.overwatch.runtime.LocationSender
import kotlinx.coroutines.launch

sealed class Nav {
    data object Home : Nav()
    data class Detail(val id: Long) : Nav()
    data class Editor(val id: Long?) : Nav()
    data object Settings : Nav()
    data object About : Nav()
    data object Help : Nav()
}

@Composable
fun OverwatchRoot(vm: MainViewModel) {
    val appState by vm.appState.collectAsState()
    val configs by vm.configs.collectAsState()
    val settings by vm.settings.collectAsState()
    val nav by vm.nav.collectAsState()
    val draft by vm.draft.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val running = appState as? AppState.Overwatch

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importDb(context, uri)
    }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.writeExportTo(context, uri)
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSettings(context, uri)
    }
    val createSettingsDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.writeSettingsExportTo(context, uri)
    }
    val createVerboseDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.writeVerboseLogTo(context, uri)
    }
    val createLogDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.writeLogTo(context, uri)
    }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAfterLocation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLocationOff by remember { mutableStateOf(false) }
    var awaitingLocationSettings by remember { mutableStateOf(false) }
    val startPerms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        com.grandsphere.overwatch.runtime.VerboseLog.d(
            "Perms",
            "result ${results.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" }}",
        )
        pendingStart?.invoke()
        pendingStart = null
    }
    fun afterRuntimePerms(config: OverwatchConfig?, start: () -> Unit) {
        if (config != null &&
            FeaturePermissions.needsLocation(config) &&
            !LocationSender.isLocationEnabled(context)
        ) {
            com.grandsphere.overwatch.runtime.VerboseLog.d("Perms", "location services off for ${config.name}")
            pendingAfterLocation = start
            showLocationOff = true
        } else {
            start()
        }
    }
    fun startAfterPerms(config: OverwatchConfig?, start: () -> Unit) {
        val missing = if (config == null) {
            emptyArray()
        } else {
            FeaturePermissions.missingFor(context, config)
        }
        if (missing.isNotEmpty()) {
            com.grandsphere.overwatch.runtime.VerboseLog.d(
                "Perms",
                "requesting ${missing.joinToString()} for ${config?.name}",
            )
        } else {
            com.grandsphere.overwatch.runtime.VerboseLog.d("Perms", "none missing for ${config?.name}")
        }
        if (missing.isEmpty()) {
            afterRuntimePerms(config, start)
        } else {
            pendingStart = { afterRuntimePerms(config, start) }
            startPerms.launch(missing)
        }
    }
    val locationLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(locationLifecycle, awaitingLocationSettings) {
        if (!awaitingLocationSettings) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingLocationSettings) {
                awaitingLocationSettings = false
                val enabled = LocationSender.isLocationEnabled(context)
                com.grandsphere.overwatch.runtime.VerboseLog.d("Perms", "returned from location settings enabled=$enabled")
                val next = pendingAfterLocation
                pendingAfterLocation = null
                next?.invoke()
            }
        }
        locationLifecycle.addObserver(observer)
        onDispose { locationLifecycle.removeObserver(observer) }
    }
    fun idlePanicConfig(): OverwatchConfig? =
        configs.firstOrNull { it.isDefault() } ?: configs.firstOrNull()

    fun quitApp() {
        if (vm.appState.value is AppState.Overwatch) return
        com.grandsphere.overwatch.runtime.AppShutdown.hardStop(context, finishAffinity = true)
    }

    fun promptBatteryIfNeeded() {
        if (settings.batteryPrompted) return
        vm.updateSettings(transform = { it.copy(batteryPrompted = true) })
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
            com.grandsphere.overwatch.runtime.VerboseLog.d("Perms", "battery optimization already ignored")
            return
        }
        com.grandsphere.overwatch.runtime.VerboseLog.d("Perms", "requesting ignore battery optimizations")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
            .onFailure { com.grandsphere.overwatch.runtime.VerboseLog.fail("Perms", "battery prompt", it) }
    }

    LaunchedEffect(settings) {
        com.grandsphere.overwatch.MainActivitySettings.latest = settings
    }
    LaunchedEffect(running) {
        if (running != null) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        if (vm.settings.value.batteryPrompted) return@LaunchedEffect
        promptBatteryIfNeeded()
    }

    BackHandler(enabled = running == null && (drawerState.isOpen || nav !is Nav.Home)) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            vm.back()
        }
    }

    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * settings.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
    OverwatchTheme(
        light = settings.lightTheme,
        cardArgb = settings.darkCardArgb,
        actionArgb = settings.actionArgb,
    ) {
        Box(Modifier.statusBarsPadding()) {
        if (running != null) {
            val secret = running.secretAlarm
            LaunchedEffect(secret) {
                if (!secret) return@LaunchedEffect
                kotlinx.coroutines.delay(80)
                (context as? android.app.Activity)?.moveTaskToBack(true)
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    if (!secret) {
                        OverwatchTopBar(
                            title = "Overwatch",
                            showMenu = false,
                            onMenu = {},
                            panicActivation = settings.panicActivation,
                            onPanic = { startAfterPerms(running.config) { vm.panic() } },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    RunningScreen(
                        state = running,
                        onDigit = { vm.engine.submitPinDigit(it) },
                        onBackspace = { vm.engine.pinBackspace() },
                        onClear = { vm.engine.pinClear() },
                        onTap = { vm.engine.tapDismiss() },
                        onFingerprint = { vm.engine.fingerprintSuccess() },
                        onConfirmCancel = { vm.engine.cancelConfirm() },
                        onCancelWithPin = { vm.engine.cancelWithPin(it) },
                        onCancelWithTap = { vm.engine.cancelWithTap() },
                        onCancelWithFingerprint = { vm.engine.cancelWithFingerprint() },
                        cancelLabel = if (secret) {
                            settings.failSecretPhrase.ifBlank { "Okay" }
                        } else {
                            "Cancel"
                        },
                    )
                }
            }
            return@Box
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                AppDrawer(
                    selectedHome = nav is Nav.Home,
                    selectedAbout = nav is Nav.About,
                    selectedHelp = nav is Nav.Help,
                    selectedSettings = nav is Nav.Settings,
                    onHome = {
                        vm.goHome()
                        scope.launch { drawerState.close() }
                    },
                    onAbout = {
                        vm.openAbout()
                        scope.launch { drawerState.close() }
                    },
                    onHelp = {
                        vm.openHelp()
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        vm.openSettings()
                        scope.launch { drawerState.close() }
                    },
                    onQuit = {
                        scope.launch { drawerState.close() }
                        quitApp()
                    },
                )
            },
        ) {
            val title = when (val n = nav) {
                Nav.Home -> "Idle"
                is Nav.Detail -> configs.find { it.id == n.id }?.name ?: "Idle"
                is Nav.Editor -> if (n.id == null) "New" else "Edit"
                Nav.Settings -> "Settings"
                Nav.About -> "About"
                Nav.Help -> "Help"
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    OverwatchTopBar(
                        title = title,
                        showMenu = nav is Nav.Home,
                        onMenu = { scope.launch { drawerState.open() } },
                        panicActivation = settings.panicActivation,
                        onPanic = { startAfterPerms(idlePanicConfig()) { vm.panic() } },
                        navigation = {
                            if (nav !is Nav.Home) {
                                IconButton(onClick = { vm.back() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                    )
                },
                floatingActionButton = {
                    if (nav is Nav.Home) {
                        FloatingActionButton(onClick = { vm.openEditor(null) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (val n = nav) {
                        Nav.Home -> IdleHomeScreen(
                            configs = configs,
                            onOpen = { vm.openDetail(it) },
                            onDelete = { vm.deleteConfig(it) },
                        )
                        is Nav.Detail -> {
                            val config = configs.find { it.id == n.id }
                            if (config != null) {
                                DetailScreen(
                                    config = config,
                                    onEnable = { startAfterPerms(config) { vm.enable(config) } },
                                    onEdit = { vm.openEditor(config.id) },
                                )
                            }
                        }
                        is Nav.Editor -> {
                            val d = draft
                            if (d != null) {
                                EditorScreen(
                                    draft = d,
                                    settings = settings,
                                    pinIsSet = settings.pinHash.isNotBlank(),
                                    onChange = vm::updateDraft,
                                    onSave = { vm.saveDraft() },
                                    onSetPin = { pin ->
                                        val hash = PinHasher.hash(pin)
                                        vm.updateSettings(transform = { it.copy(pinHash = hash) })
                                    },
                                )
                            }
                        }
                        Nav.Settings -> SettingsScreen(
                            settings = settings,
                            onSave = { next ->
                                vm.updateSettings(
                                    transform = { next },
                                    onSuccess = {
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            },
                            onExport = { createDoc.launch("overwatch-export.db") },
                            onShare = { vm.exportDb(context) },
                            onShareLog = { vm.shareLog(context) },
                            onExportLog = { createLogDoc.launch("overwatch-log.txt") },
                            onExportVerboseLog = {
                                createVerboseDoc.launch("overwatch-verbose.log")
                            },
                            onShareVerboseLog = { vm.shareVerboseLog(context) },
                            onImport = { importLauncher.launch(arrayOf("*/*")) },
                            onExportSettings = {
                                createSettingsDoc.launch(SettingsExport.FILE_NAME)
                            },
                            onShareSettings = { vm.shareSettings(context) },
                            onImportSettings = { importSettingsLauncher.launch(arrayOf("*/*")) },
                            onClearOverwatches = { vm.clearOverwatches() },
                        )
                        Nav.About -> AboutScreen()
                        Nav.Help -> HelpScreen()
                    }
                }
            }
        }
        }
        if (showLocationOff) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Location is off") },
                text = {
                    Text("Turn on location so this Overwatch can use it.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            com.grandsphere.overwatch.runtime.VerboseLog.d("Perms", "location settings Turn on")
                            showLocationOff = false
                            awaitingLocationSettings = true
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }.onFailure {
                                com.grandsphere.overwatch.runtime.VerboseLog.fail(
                                    "Perms",
                                    "location settings",
                                    it,
                                )
                                awaitingLocationSettings = false
                                pendingAfterLocation?.invoke()
                                pendingAfterLocation = null
                            }
                        },
                    ) { Text("Turn on") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            com.grandsphere.overwatch.runtime.VerboseLog.d("Perms", "location settings Not now")
                            showLocationOff = false
                            awaitingLocationSettings = false
                            val next = pendingAfterLocation
                            pendingAfterLocation = null
                            next?.invoke()
                        },
                    ) { Text("Not now") }
                },
            )
        }
    }
    }
}
