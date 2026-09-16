package com.grandsphere.overwatch.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.AppearanceDefaults
import com.grandsphere.overwatch.runtime.VerboseLog
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.PanicActivation
import com.grandsphere.overwatch.domain.model.RecordCameraMode
import com.grandsphere.overwatch.domain.security.PinHasher
import com.grandsphere.overwatch.runtime.LocationFeatures
import com.grandsphere.overwatch.ui.chrome.CollapsibleSection
import com.grandsphere.overwatch.ui.chrome.HintCopy
import com.grandsphere.overwatch.ui.chrome.HintFilterChip
import com.grandsphere.overwatch.ui.chrome.HintText
import com.grandsphere.overwatch.ui.chrome.HoldHintDialog
import com.grandsphere.overwatch.ui.chrome.hintFieldLabel
import com.grandsphere.overwatch.ui.chrome.hintSemantics
import com.grandsphere.overwatch.ui.chrome.holdHintOrClick
import com.grandsphere.overwatch.ui.chrome.watchHoldHint
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onShareLog: () -> Unit,
    onExportLog: () -> Unit,
    onExportVerboseLog: () -> Unit,
    onShareVerboseLog: () -> Unit,
    onImport: () -> Unit,
    onExportSettings: () -> Unit,
    onShareSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onClearOverwatches: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(settings) }
    LaunchedEffect(settings.pinHash, settings.batteryPrompted) {
        draft = draft.copy(
            pinHash = settings.pinHash,
            batteryPrompted = settings.batteryPrompted,
        )
    }
    var pinText by remember { mutableStateOf(if (settings.pinHash.isNotEmpty()) "****" else "") }
    var pinIsMask by remember { mutableStateOf(settings.pinHash.isNotEmpty()) }
    var clipSecondsText by remember { mutableStateOf(settings.videoClipSeconds.toString()) }
    var audioClipText by remember { mutableStateOf(settings.audioClipSeconds.toString()) }
    var recentLocText by remember { mutableStateOf(settings.recentLocationMinutes.toString()) }
    var recentPointsText by remember { mutableStateOf(settings.recentLocationPoints.toString()) }
    var contLocText by remember { mutableStateOf(settings.continuousLocationMinutes.toString()) }
    var maxDurText by remember { mutableStateOf(settings.maxAlarmDurationMinutes.toString()) }
    var maxSmsText by remember { mutableStateOf(settings.maxAlarmSms.toString()) }
    var maxCallsText by remember { mutableStateOf(settings.maxAlarmCalls.toString()) }
    var colourPicker by remember { mutableStateOf<String?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    var fakePinHint by remember { mutableStateOf(false) }
    var failSecretHint by remember { mutableStateOf(false) }
    var holdHint by remember { mutableStateOf<String?>(null) }
    val showHoldHint: (String) -> Unit = { if (it.isNotEmpty()) holdHint = it }
    var permTick by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var pendingPerm by remember { mutableStateOf<String?>(null) }
    val requestPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        VerboseLog.d("Perms", "result ${pendingPerm?.substringAfterLast('.')}=$granted")
        pendingPerm = null
        permTick++
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CollapsibleSection("Panic", hint = "How Panic is triggered in the app") {
            HintText(
                text = "Panic activation",
                hint = HintCopy.PANIC_ACTIVATION,
                onHint = showHoldHint,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row {
                PanicActivation.entries.forEach { act ->
                    HintFilterChip(
                        selected = draft.panicActivation == act,
                        label = act.name.lowercase().replace('_', ' '),
                        hint = HintCopy.panicActivation(act),
                        onHint = showHoldHint,
                        onClick = { draft = draft.copy(panicActivation = act) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            HintText(
                text = "Panic hardware key (in-app)",
                hint = HintCopy.PANIC_HARDWARE,
                onHint = showHoldHint,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row {
                listOf(
                    HardwareKeyOption.NONE to "Off",
                    HardwareKeyOption.VOLUME_UP_DOUBLE to "Vol up x2",
                    HardwareKeyOption.VOLUME_UP to "Vol up",
                ).forEach { (opt, label) ->
                    HintFilterChip(
                        selected = draft.panicHardwareKey == opt,
                        label = label,
                        hint = HintCopy.hardwareKey(opt),
                        onHint = showHoldHint,
                        onClick = { draft = draft.copy(panicHardwareKey = opt) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        CollapsibleSection("PIN", hint = "Change the PIN and fake PIN used for Overwatch") {
            OutlinedTextField(
                value = pinText,
                onValueChange = { incoming ->
                    if (pinIsMask) {
                        pinIsMask = false
                        pinText = incoming.filter { c -> c.isDigit() }.take(8)
                    } else {
                        pinText = incoming.filter { c -> c.isDigit() }.take(8)
                    }
                },
                label = hintFieldLabel("PIN", HintCopy.SETTINGS_PIN, showHoldHint),
                placeholder = { Text("Leave blank to keep") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.SETTINGS_PIN) },
            )
            OutlinedTextField(
                value = draft.duressDigit,
                onValueChange = { draft = draft.copy(duressDigit = it.filter { c -> c.isDigit() }.take(1)) },
                label = {
                    Text(
                        "Fake PIN digit",
                        modifier = Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation()
                                }
                                if (up == null) fakePinHint = true
                            }
                        },
                    )
                },
                placeholder = { Text("Off") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Hint") {
                                fakePinHint = true
                                true
                            },
                        )
                    },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HintFilterChip(
                    selected = !draft.duressPrefix,
                    label = "Ends with",
                    hint = HintCopy.ENDS_STARTS,
                    onHint = showHoldHint,
                    onClick = { draft = draft.copy(duressPrefix = false) },
                )
                HintFilterChip(
                    selected = draft.duressPrefix,
                    label = "Starts with",
                    hint = HintCopy.ENDS_STARTS,
                    onHint = showHoldHint,
                    onClick = { draft = draft.copy(duressPrefix = true) },
                )
            }
            SettingsSwitchRow(
                label = "Panic after 2 wrong PINs",
                checked = draft.panicOnTwoWrongPins,
                onChecked = { draft = draft.copy(panicOnTwoWrongPins = it) },
                onLongClick = { showHoldHint(HintCopy.PANIC_TWO_WRONG) },
            )
            SettingsSwitchRow(
                label = "Fail secretly",
                checked = draft.failSecretly,
                onChecked = { draft = draft.copy(failSecretly = it) },
                onLongClick = { failSecretHint = true },
            )
            if (draft.failSecretly) {
                OutlinedTextField(
                    value = draft.failSecretPhrase,
                    onValueChange = { draft = draft.copy(failSecretPhrase = it.take(40)) },
                    label = hintFieldLabel("Secret cancel phrase", HintCopy.SECRET_PHRASE, showHoldHint),
                    placeholder = { Text("Okay") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .hintSemantics { showHoldHint(HintCopy.SECRET_PHRASE) },
                )
            }
        }

        CollapsibleSection("Alarm", hint = "Limits while Alarm Mode / Safety outbound traffic") {
            OutlinedTextField(
                value = maxDurText,
                onValueChange = { maxDurText = it.filter { c -> c.isDigit() }.take(4) },
                label = hintFieldLabel("Max duration (minutes)", HintCopy.MAX_DURATION, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.MAX_DURATION) },
            )
            OutlinedTextField(
                value = maxSmsText,
                onValueChange = { maxSmsText = it.filter { c -> c.isDigit() }.take(4) },
                label = hintFieldLabel("Max SMS", HintCopy.MAX_SMS, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.MAX_SMS) },
            )
            OutlinedTextField(
                value = maxCallsText,
                onValueChange = { maxCallsText = it.filter { c -> c.isDigit() }.take(4) },
                label = hintFieldLabel("Max Calls", HintCopy.MAX_CALLS, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.MAX_CALLS) },
            )
        }

        CollapsibleSection("Recording", hint = "Clip length and camera for Alarm Mode recording") {
            OutlinedTextField(
                value = clipSecondsText,
                onValueChange = { clipSecondsText = it.filter { c -> c.isDigit() }.take(3) },
                label = hintFieldLabel("Video clip seconds", HintCopy.CLIP_LENGTH, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.CLIP_LENGTH) },
            )
            OutlinedTextField(
                value = audioClipText,
                onValueChange = { audioClipText = it.filter { c -> c.isDigit() }.take(3) },
                label = hintFieldLabel("Audio clip seconds", HintCopy.CLIP_LENGTH, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.CLIP_LENGTH) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HintFilterChip(
                    selected = draft.recordCameraMode == RecordCameraMode.BACK,
                    label = "Back camera",
                    hint = HintCopy.CAMERA_BACK,
                    onHint = showHoldHint,
                    onClick = { draft = draft.copy(recordCameraMode = RecordCameraMode.BACK) },
                )
                HintFilterChip(
                    selected = draft.recordCameraMode == RecordCameraMode.FRONT,
                    label = "Front camera",
                    hint = HintCopy.CAMERA_FRONT,
                    onHint = showHoldHint,
                    onClick = { draft = draft.copy(recordCameraMode = RecordCameraMode.FRONT) },
                )
                HintFilterChip(
                    selected = draft.recordCameraMode == RecordCameraMode.BOTH,
                    label = "Both",
                    hint = HintCopy.CAMERA_BOTH,
                    onHint = showHoldHint,
                    onClick = { draft = draft.copy(recordCameraMode = RecordCameraMode.BOTH) },
                )
            }
        }

        CollapsibleSection("Location", hint = "Recent trail and continuous SMS intervals (full minutes)") {
            OutlinedTextField(
                value = recentLocText,
                onValueChange = { recentLocText = it.filter { c -> c.isDigit() }.take(3) },
                label = hintFieldLabel("Recent sample every (minutes)", HintCopy.RECENT_SAMPLE, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.RECENT_SAMPLE) },
            )
            OutlinedTextField(
                value = recentPointsText,
                onValueChange = { recentPointsText = it.filter { c -> c.isDigit() }.take(2) },
                label = hintFieldLabel("Recent points in SMS", HintCopy.RECENT_POINTS, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.RECENT_POINTS) },
            )
            OutlinedTextField(
                value = contLocText,
                onValueChange = { contLocText = it.filter { c -> c.isDigit() }.take(3) },
                label = hintFieldLabel("Continuous SMS every (minutes)", HintCopy.CONTINUOUS_SMS, showHoldHint),
                placeholder = { },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .hintSemantics { showHoldHint(HintCopy.CONTINUOUS_SMS) },
            )
        }

        CollapsibleSection("Appearance", hint = "Theme, group colour, widget colour, action colour, font size, and notify banners") {
            SettingsSwitchRow(
                label = "Light theme",
                checked = draft.lightTheme,
                onChecked = { light ->
                    val (group, widget, action) = AppearanceDefaults.withSchemeDefaults(light)
                    draft = draft.copy(
                        lightTheme = light,
                        darkCardArgb = group,
                        widgetArgb = widget,
                        actionArgb = action,
                    )
                },
                onLongClick = { showHoldHint(HintCopy.LIGHT_THEME) },
            )
            ColourSettingRow(
                label = "Group colour",
                argb = draft.darkCardArgb,
                onClick = { colourPicker = "group" },
                onLongClick = {
                    draft = draft.copy(darkCardArgb = AppearanceDefaults.group(draft.lightTheme))
                },
            )
            ColourSettingRow(
                label = "Widget colour",
                argb = draft.widgetArgb,
                onClick = { colourPicker = "widget" },
                onLongClick = {
                    draft = draft.copy(widgetArgb = AppearanceDefaults.widget(draft.lightTheme))
                },
            )
            ColourSettingRow(
                label = "Action colour",
                argb = draft.actionArgb,
                onClick = { colourPicker = "action" },
                onLongClick = {
                    draft = draft.copy(actionArgb = AppearanceDefaults.action(draft.lightTheme))
                },
            )
            HintText(
                text = "Font size",
                hint = HintCopy.FONT_SIZE,
                onHint = showHoldHint,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0.85f to ("Smaller" to HintCopy.FONT_SMALLER),
                    1f to ("Default" to HintCopy.FONT_DEFAULT),
                    1.15f to ("Larger" to HintCopy.FONT_LARGER),
                ).forEach { (scale, pair) ->
                    val (label, hint) = pair
                    HintFilterChip(
                        selected = kotlin.math.abs(draft.fontScale - scale) < 0.01f,
                        label = label,
                        hint = hint,
                        onHint = showHoldHint,
                        onClick = { draft = draft.copy(fontScale = scale) },
                    )
                }
            }
            SettingsSwitchRow(
                label = "Notify toasts",
                checked = draft.notifyToasts,
                onChecked = { draft = draft.copy(notifyToasts = it) },
                onLongClick = { showHoldHint(HintCopy.NOTIFY_TOASTS) },
            )
        }

        CollapsibleSection("Permissions", hint = "Grant or revoke what Overwatch may use") {
            val nm = context.getSystemService(NotificationManager::class.java)
            val pm = context.getSystemService(PowerManager::class.java)
            @Suppress("UNUSED_EXPRESSION")
            permTick
            runtimeRows(context).forEach { row ->
                PermissionRow(
                    label = row.label,
                    on = ContextCompat.checkSelfPermission(context, row.permission) ==
                        PackageManager.PERMISSION_GRANTED,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, row.permission) ==
                            PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            pendingPerm = row.permission
                            VerboseLog.d("Perms", "requesting ${row.permission.substringAfterLast('.')}")
                            requestPerm.launch(row.permission)
                        } else if (Build.VERSION.SDK_INT >= 31) {
                            runCatching { context.revokeSelfPermissionOnKill(row.permission) }
                            openAppDetails(context)
                        } else {
                            openAppDetails(context)
                        }
                    },
                    onHint = { showHoldHint(HintCopy.permissionHint(row.label)) },
                )
            }
            PermissionRow(
                label = "Battery optimisation",
                on = pm.isIgnoringBatteryOptimizations(context.packageName),
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(intent) }
                },
                onHint = { showHoldHint(HintCopy.PERM_BATTERY) },
            )
            if (Build.VERSION.SDK_INT >= 31) {
                PermissionRow(
                    label = "Exact alarms",
                    on = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}")),
                        )
                    },
                    onHint = { showHoldHint(HintCopy.PERM_EXACT) },
                )
            }
            PermissionRow(
                label = "Do Not Disturb",
                on = nm.isNotificationPolicyAccessGranted,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                },
                onHint = { showHoldHint(HintCopy.PERM_DND) },
            )
            if (Build.VERSION.SDK_INT >= 29) {
                PermissionRow(
                    label = "Bubbles",
                    on = com.grandsphere.overwatch.runtime.CheckInBubbles.areAllowed(nm),
                    onClick = {
                        com.grandsphere.overwatch.runtime.CheckInBubbles.openSettings(context)
                    },
                    onHint = { showHoldHint(HintCopy.PERM_BUBBLES) },
                )
            }
            if (Build.VERSION.SDK_INT >= 34) {
                PermissionRow(
                    label = "Full-screen intents",
                    on = nm.canUseFullScreenIntent(),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData(Uri.parse("package:${context.packageName}")),
                        )
                    },
                    onHint = { showHoldHint(HintCopy.PERM_FSI) },
                )
                PermissionRow(
                    label = "Live Updates",
                    on = nm.canPostPromotedNotifications(),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                                    .setData(Uri.parse("package:${context.packageName}")),
                            )
                        }.onFailure { openAppDetails(context) }
                    },
                    onHint = { showHoldHint(HintCopy.PERM_LIVE) },
                )
            }
            if (LocationFeatures.OFFER_BACKGROUND_LOCATION && Build.VERSION.SDK_INT >= 29) {
                Spacer(Modifier.height(8.dp))
                val fineOn = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                val bgOn = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                PermissionRow(
                    label = "Background location (not critical)",
                    on = bgOn,
                    onClick = {
                        if (bgOn) {
                            openAppDetails(context)
                        } else if (!fineOn) {
                            pendingPerm = Manifest.permission.ACCESS_FINE_LOCATION
                            VerboseLog.d("Perms", "requesting ACCESS_FINE_LOCATION")
                            requestPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            pendingPerm = Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            VerboseLog.d("Perms", "requesting ACCESS_BACKGROUND_LOCATION")
                            requestPerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    },
                    onHint = { showHoldHint(HintCopy.PERM_BG_LOCATION) },
                )
            }
        }

        CollapsibleSection("Export", hint = "Copy Overwatches and settings between devices") {
            HintActionButton("Export Overwatches", HintCopy.EXPORT_OW, showHoldHint, onExport)
            HintActionButton("Share export Overwatches", HintCopy.SHARE_OW, showHoldHint, onShare)
            HintActionButton("Import Overwatches", HintCopy.IMPORT_OW, showHoldHint, onImport)
            HintActionButton("Clear Overwatches", HintCopy.CLEAR_OW, showHoldHint) { clearConfirm = true }
            HintActionButton("Export settings", HintCopy.EXPORT_SETTINGS, showHoldHint, onExportSettings)
            HintActionButton("Share export settings", HintCopy.SHARE_SETTINGS, showHoldHint, onShareSettings)
            HintActionButton("Import settings", HintCopy.IMPORT_SETTINGS, showHoldHint, onImportSettings)
            HintActionButton("Export log", HintCopy.EXPORT_LOG, showHoldHint, onExportLog)
            HintActionButton("Share log", HintCopy.SHARE_LOG, showHoldHint, onShareLog)
            HintActionButton("Export verbose log", HintCopy.EXPORT_VERBOSE, showHoldHint, onExportVerboseLog)
            HintActionButton(
                "Share export verbose log",
                HintCopy.SHARE_VERBOSE,
                showHoldHint,
                onShareVerboseLog,
            )
        }

        CollapsibleSection(
            "Log",
            hint = "Documents/Overwatch/overwatch-log.txt and overwatch-verbose.log",
        ) {
            SettingsSwitchRow(
                label = "Verbose logging",
                checked = draft.verboseLogging,
                onChecked = { draft = draft.copy(verboseLogging = it) },
                onLongClick = { showHoldHint(HintCopy.VERBOSE_LOG) },
            )
            SettingsSwitchRow(
                label = "Always log events",
                checked = draft.alwaysLogEvents,
                onChecked = { draft = draft.copy(alwaysLogEvents = it) },
                onLongClick = { showHoldHint(HintCopy.ALWAYS_LOG) },
            )
        }

        Button(
            onClick = {
                val hashed = if (!pinIsMask && pinText.length >= 4) PinHasher.hash(pinText) else draft.pinHash
                val pinLen = if (!pinIsMask && pinText.length >= 4) {
                    pinText.length.coerceIn(4, 8)
                } else {
                    draft.pinLength
                }
                val videoSec = clipSecondsText.toIntOrNull()?.coerceIn(3, 120) ?: draft.videoClipSeconds
                val audioSec = audioClipText.toIntOrNull()?.coerceIn(3, 120) ?: draft.audioClipSeconds
                val recent = recentLocText.toIntOrNull()?.coerceAtLeast(1) ?: draft.recentLocationMinutes
                val recentPoints = recentPointsText.toIntOrNull()?.coerceIn(1, 20) ?: draft.recentLocationPoints
                val cont = contLocText.toIntOrNull()?.coerceAtLeast(1) ?: draft.continuousLocationMinutes
                val maxDur = maxDurText.toIntOrNull()?.coerceAtLeast(1) ?: draft.maxAlarmDurationMinutes
                val maxSms = maxSmsText.toIntOrNull()?.coerceAtLeast(1) ?: draft.maxAlarmSms
                val maxCalls = maxCallsText.toIntOrNull()?.coerceAtLeast(1) ?: draft.maxAlarmCalls
                onSave(
                    draft.copy(
                        pinHash = hashed,
                        pinLength = pinLen,
                        videoClipSeconds = videoSec,
                        audioClipSeconds = audioSec,
                        recentLocationMinutes = recent,
                        recentLocationPoints = recentPoints,
                        continuousLocationMinutes = cont,
                        maxAlarmDurationMinutes = maxDur,
                        maxAlarmSms = maxSms,
                        maxAlarmCalls = maxCalls,
                        failSecretPhrase = draft.failSecretPhrase.trim().ifBlank { "Okay" },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        Spacer(Modifier.height(48.dp))
    }
    if (colourPicker != null) {
        val title = when (colourPicker) {
            "widget" -> "Widget colour"
            "action" -> "Action colour"
            else -> "Group colour"
        }
        val initial = when (colourPicker) {
            "widget" -> draft.widgetArgb
            "action" -> draft.actionArgb
            else -> draft.darkCardArgb
        }
        ColorPickerDialog(
            title = title,
            initialArgb = initial,
            onConfirm = { argb ->
                draft = when (colourPicker) {
                    "widget" -> draft.copy(widgetArgb = argb)
                    "action" -> draft.copy(actionArgb = argb)
                    else -> draft.copy(darkCardArgb = argb)
                }
                colourPicker = null
            },
            onDismiss = { colourPicker = null },
        )
    }
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("Clear Overwatches") },
            text = { Text("Delete all Overwatches except Default? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearConfirm = false
                    onClearOverwatches()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) { Text("Cancel") }
            },
        )
    }
    if (fakePinHint) {
        val endsOrStarts = if (draft.duressPrefix) "starts" else "ends"
        AlertDialog(
            onDismissRequest = { fakePinHint = false },
            title = { Text("Fake PIN digit") },
            text = {
                Text("Trigger alarm if an entered PIN $endsOrStarts with this digit.")
            },
            confirmButton = {
                TextButton(onClick = { fakePinHint = false }) { Text("OK") }
            },
        )
    }
    if (failSecretHint) {
        AlertDialog(
            onDismissRequest = { failSecretHint = false },
            title = { Text("Fail secretly") },
            text = {
                Text(
                    "Makes it so that a wrong pin appears successful, but secretly triggers the alarm.",
                )
            },
            confirmButton = {
                TextButton(onClick = { failSecretHint = false }) { Text("OK") }
            },
        )
    }
    HoldHintDialog(text = holdHint, onDismiss = { holdHint = null })
}

@Composable
private fun HintActionButton(
    label: String,
    hint: String,
    onHint: (String) -> Unit,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.watchHoldHint { onHint(hint) },
    ) { Text(label) }
}

@Composable
private fun PermissionRow(label: String, on: Boolean, onClick: () -> Unit, onHint: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .holdHintOrClick(onClick = onClick, onHint = onHint)
            .padding(vertical = 8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (on) "On" else "Off",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColourSettingRow(
    label: String,
    argb: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private data class RuntimePerm(val permission: String, val label: String)

private fun runtimeRows(context: android.content.Context): List<RuntimePerm> {
    val rows = mutableListOf<RuntimePerm>()
    if (Build.VERSION.SDK_INT >= 33) {
        rows += RuntimePerm(Manifest.permission.POST_NOTIFICATIONS, "Notifications")
    }
    rows += RuntimePerm(Manifest.permission.SEND_SMS, "SMS")
    rows += RuntimePerm(Manifest.permission.CALL_PHONE, "Phone calls")
    rows += RuntimePerm(Manifest.permission.READ_PHONE_STATE, "Phone state")
    rows += RuntimePerm(Manifest.permission.READ_CONTACTS, "Contacts")
    rows += RuntimePerm(Manifest.permission.ACCESS_FINE_LOCATION, "Precise location")
    rows += RuntimePerm(Manifest.permission.CAMERA, "Camera")
    rows += RuntimePerm(Manifest.permission.RECORD_AUDIO, "Microphone")
    return rows
}

private fun openAppDetails(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}")),
    )
}
