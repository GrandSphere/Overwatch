package com.grandsphere.overwatch.ui.editor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.ui.chrome.CollapsibleSection
import com.grandsphere.overwatch.domain.catalog.AlarmCatalog
import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.DismissCatalog
import com.grandsphere.overwatch.domain.catalog.FeaturePermissions
import com.grandsphere.overwatch.runtime.VerboseLog
import com.grandsphere.overwatch.domain.catalog.NotifyCatalog
import com.grandsphere.overwatch.domain.catalog.PanicModeCatalog
import com.grandsphere.overwatch.domain.model.AppSettings
import com.grandsphere.overwatch.domain.model.CrashSensitivity
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.NotificationUrgency
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.RepeatKind
import com.grandsphere.overwatch.domain.model.ShakeStrength
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    draft: OverwatchConfig,
    settings: AppSettings,
    pinIsSet: Boolean,
    onChange: (OverwatchConfig) -> Unit,
    onSave: () -> Unit,
    onSetPin: (String) -> Unit,
) {
    val notifyConflicts = NotifyCatalog.conflicts(draft.notifyEffectIds, draft.covert)
    val pinRequiredMissing = !pinIsSet && (
        "pin" in draft.dismissEffectIds ||
            CancelCatalog.usesPin(draft.cancelEffectIds, draft.dismissEffectIds)
    )
    val dismissConflicts = DismissCatalog.conflicts(draft.dismissEffectIds) +
        if (pinRequiredMissing) setOf("pin") else emptySet()
    val cancelConflicts = CancelCatalog.conflicts(draft.cancelEffectIds) +
        if (pinRequiredMissing && CancelCatalog.usesPin(draft.cancelEffectIds, draft.dismissEffectIds)) {
            setOf("pin")
        } else {
            emptySet()
        }
    val alarmConflicts = AlarmCatalog.conflicts(draft.alarmEffectIds, draft.covert)
    val safetyIds = draft.safetyEffectIds.filter { AlarmCatalog.isAllowedInSafety(it) }
    val safetyConflicts = AlarmCatalog.conflicts(safetyIds, draft.covert)
    val panicConflicts = PanicModeCatalog.conflicts(draft.panicEffectIds)
    val needsAlarmSms = "sms" in draft.alarmEffectIds
    val needsAlarmCall = draft.alarmEffectIds.any { it == "call" || it == "quiet_call" }
    val needsSafetySms = "sms" in safetyIds
    val needsSafetyCall = "call" in safetyIds
    val alarmSmsOk = !needsAlarmSms || draft.smsContacts().isNotEmpty()
    val alarmCallOk = !needsAlarmCall || draft.callContacts().isNotEmpty()
    val safetySmsOk = !needsSafetySms || draft.safetySmsContacts().isNotEmpty()
    val safetyCallOk = !needsSafetyCall || draft.safetyCallContacts().isNotEmpty()
    val pinOk = !pinRequiredMissing
    val modesComplete = draft.notifyEffectIds.isNotEmpty() &&
        draft.dismissEffectIds.isNotEmpty() &&
        draft.alarmEffectIds.isNotEmpty() &&
        draft.cancelEffectIds.isNotEmpty()
    val canSave = modesComplete && alarmSmsOk && alarmCallOk && safetySmsOk && safetyCallOk && pinOk
    var incompleteDialog by remember { mutableStateOf<String?>(null) }
    var covertHint by remember { mutableStateOf(false) }
    var graceHint by remember { mutableStateOf(false) }
    var clockRoundDialog by remember { mutableStateOf(false) }
    var locationRequireDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var locationRecentHint by remember { mutableStateOf(false) }
    var locationContinuousHint by remember { mutableStateOf(false) }
    var soundDurationKind by remember { mutableStateOf<String?>(null) }
    var vibrateDurationKind by remember { mutableStateOf<String?>(null) }
    var flashlightKind by remember { mutableStateOf<String?>(null) }
    var shakeDialogKind by remember { mutableStateOf<String?>(null) }
    var powerTapsDialogKind by remember { mutableStateOf<String?>(null) }
    var showCrashDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showLiveNotifyDialog by remember { mutableStateOf(false) }
    var callDialogKind by remember { mutableStateOf<String?>(null) }
    var smsDialogKind by remember { mutableStateOf<String?>(null) }
    var notificationDialogKind by remember { mutableStateOf<String?>(null) }
    var turnoverDialogKind by remember { mutableStateOf<String?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var fingerprintDialogKind by remember { mutableStateOf<String?>(null) }
    var lastFiniteNotifyDurationMs by rememberSaveable {
        mutableStateOf(
            if (draft.notifySoundDurationMs > 0L) draft.notifySoundDurationMs else 30_000L,
        )
    }
    var lastFiniteAlarmDurationMs by rememberSaveable {
        mutableStateOf(
            if (draft.alarmSoundDurationMs > 0L) draft.alarmSoundDurationMs else 30_000L,
        )
    }
    var lastFiniteNotifyVibrateMs by rememberSaveable {
        mutableStateOf(
            if (draft.notifyVibrateDurationMs > 0L) draft.notifyVibrateDurationMs else 10_000L,
        )
    }
    var lastFiniteAlarmVibrateMs by rememberSaveable {
        mutableStateOf(
            if (draft.alarmVibrateDurationMs > 0L) draft.alarmVibrateDurationMs else 10_000L,
        )
    }
    var lastFiniteNotifyFlashlightMs by rememberSaveable {
        mutableStateOf(
            if (draft.notifyFlashlightDurationMs > 0L) draft.notifyFlashlightDurationMs else 5_000L,
        )
    }
    var lastFiniteAlarmFlashlightMs by rememberSaveable {
        mutableStateOf(
            if (draft.alarmFlashlightDurationMs > 0L) draft.alarmFlashlightDurationMs else 5_000L,
        )
    }
    val context = LocalContext.current
    val draftRef = remember { object { var current = draft } }
    draftRef.current = draft
    var pendingAdd by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingContactKind by remember { mutableStateOf("sms") }
    fun requirePinOrPrompt(after: () -> Unit) {
        if (pinIsSet) {
            after()
        } else {
            showPinDialog = true
            after()
        }
    }
    val requestFeature = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        VerboseLog.d(
            "Perms",
            "editor result ${results.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" }}",
        )
        if (results.isEmpty() || results.values.all { it }) pendingAdd?.invoke()
        pendingAdd = null
    }
    fun addAfterPermissions(permissions: List<String>, add: () -> Unit) {
        val missing = FeaturePermissions.missingOf(context, permissions)
        if (missing.isEmpty()) add() else {
            VerboseLog.d("Perms", "editor requesting ${missing.joinToString()}")
            pendingAdd = add
            requestFeature.launch(missing)
        }
    }
    fun addAfterPermission(permission: String?, add: () -> Unit) {
        addAfterPermissions(listOfNotNull(permission), add)
    }
    val pickPhone = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val number = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.trim().orEmpty()
        if (number.isNotEmpty()) {
            val current = draftRef.current
            when (pendingContactKind) {
                "call" -> onChange(current.withCallContacts(current.callContactEntries() + number))
                "safety_call" -> onChange(
                    current.withSafetyCallContacts(current.safetyCallContactEntries() + number),
                )
                "safety_sms" -> onChange(
                    current.withSafetySmsContacts(current.safetySmsContactEntries() + number),
                )
                else -> onChange(current.withSmsContacts(current.smsContactEntries() + number))
            }
        }
    }
    val requestContacts = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        VerboseLog.d("Perms", "result READ_CONTACTS=$granted")
        pickPhone.launch(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
        )
    }
    fun openContactPicker(kind: String) {
        pendingContactKind = kind
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            pickPhone.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
            )
        } else {
            VerboseLog.d("Perms", "requesting READ_CONTACTS")
            requestContacts.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    var pendingSoundKind by remember { mutableStateOf<String?>(null) }
    val pickRingtone = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val kind = pendingSoundKind
        pendingSoundKind = null
        if (kind == null || result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val uriString = uri?.toString().orEmpty()
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        val current = draftRef.current
        when (kind) {
            "notify" -> onChange(
                current.copy(
                    notifyEffectIds = (current.notifyEffectIds.filter { it != "none" } + "sound")
                        .distinct(),
                    notifySoundUri = uriString,
                    notifySoundDurationMs = if (current.notifyEffectIds.contains("sound")) {
                        current.notifySoundDurationMs
                    } else {
                        OverwatchConfig.SOUND_UNTIL_DISMISSED
                    },
                ),
            )
            "alarm" -> {
                onChange(
                    current.copy(
                        alarmEffectIds = (current.alarmEffectIds + "siren").distinct(),
                        alarmSoundUri = uriString,
                        alarmSoundDurationMs = if (current.alarmEffectIds.contains("siren")) {
                            current.alarmSoundDurationMs
                        } else {
                            OverwatchConfig.SOUND_UNTIL_DISMISSED
                        },
                    ),
                )
            }
            "safety" -> {
                onChange(
                    current.copy(
                        safetyEffectIds = (current.safetyEffectIds.filter { it != "none" } + "siren")
                            .filter { AlarmCatalog.isAllowedInSafety(it) }
                            .distinct(),
                        safetySoundUri = uriString,
                        safetySoundDurationMs = if (current.safetyEffectIds.contains("siren")) {
                            current.safetySoundDurationMs
                        } else {
                            30_000L
                        },
                        safetyLocationRecent = false,
                        safetyLocationContinuous = false,
                    ),
                )
            }
        }
    }
    fun openRingtonePicker(kind: String) {
        pendingSoundKind = kind
        val existing = when (kind) {
            "notify" -> draftRef.current.notifySoundUri
            "safety" -> draftRef.current.safetySoundUri
            else -> draftRef.current.alarmSoundUri
        }
        val type = if (kind == "alarm") RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sound")
        if (existing.isNotEmpty()) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
        }
        pickRingtone.launch(intent)
    }
    var previewingNotify by remember { mutableStateOf(false) }
    var previewingAlarm by remember { mutableStateOf(false) }
    var previewingSafety by remember { mutableStateOf(false) }
    fun previewSound(id: String) {
        val uri = when (id) {
            "sound" -> draftRef.current.notifySoundUri
            "siren" -> draftRef.current.alarmSoundUri
            "safety_siren" -> draftRef.current.safetySoundUri
            else -> return
        }
        OverwatchApp.from(context).cuePlayer.previewUri(uri)
    }
    fun stopPreview() {
        OverwatchApp.from(context).cuePlayer.stopPreview()
        previewingNotify = false
        previewingAlarm = false
        previewingSafety = false
    }
    DisposableEffect(Unit) {
        onDispose { OverwatchApp.from(context).cuePlayer.stopPreview() }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Collapsible("Schedule", hint = "How often you need to verify") {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { if (!draft.isDefault()) onChange(draft.copy(name = it)) },
                label = { Text("Name") },
                enabled = !draft.isDefault(),
                modifier = Modifier.fillMaxWidth(),
            )
            FooterText(
                text = draft.scheduleLabel(),
                onClick = { showScheduleDialog = true },
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        customActions = listOf(
                            CustomAccessibilityAction("Hint") {
                                covertHint = true
                                true
                            },
                        )
                    },
            ) {
                Text(
                    "Covert mode",
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { covertHint = true },
                        ),
                )
                Switch(draft.covert, onCheckedChange = { onChange(draft.copy(covert = it)) })
            }
        }

        EffectGroup(
            title = "Notify Mode",
            hint = "How you will be reminded to verify",
            ids = draft.notifyEffectIds,
            labels = { NotifyCatalog.labelOf(it) },
            options = NotifyCatalog.menuOptions(),
            conflicts = notifyConflicts,
            onAdd = { id ->
                if (id == "sound") {
                    openRingtonePicker("notify")
                } else {
                    addAfterPermission(FeaturePermissions.notifyPermission(id)) {
                        val current = draftRef.current
                        val already = id in current.notifyEffectIds
                        val next = when {
                            id == "none" -> listOf("none")
                            else -> (current.notifyEffectIds.filter { it != "none" && it != "popup" } + id)
                                .distinct()
                        }
                        onChange(
                            current.copy(
                                notifyEffectIds = next,
                                notifyVibrateDurationMs = when {
                                    id != "vibrate" || already -> current.notifyVibrateDurationMs
                                    else -> 10_000L
                                },
                                notifyFlashlightDurationMs = when {
                                    id != "flashlight" || already -> current.notifyFlashlightDurationMs
                                    else -> 5_000L
                                },
                                notifyFlashlightMode = when {
                                    id != "flashlight" || already -> current.notifyFlashlightMode
                                    else -> OverwatchConfig.FLASHLIGHT_STEADY
                                },
                            ),
                        )
                        if (id == "clock_alarm") clockRoundDialog = true
                    }
                }
            },
            onRemove = { id ->
                val next = (draft.notifyEffectIds - id).filter { it != "popup" }
                onChange(
                    draft.copy(
                        notifyEffectIds = next.ifEmpty { listOf("none") },
                        notifySoundUri = if (id == "sound") "" else draft.notifySoundUri,
                        notifySoundDurationMs = if (id == "sound") {
                            OverwatchConfig.SOUND_UNTIL_DISMISSED
                        } else {
                            draft.notifySoundDurationMs
                        },
                    ),
                )
            },
            footer = {
                if ("live_notify" in draft.notifyEffectIds) {
                    FooterText(
                        text = liveNotifyFooterLabel(draft.liveNotifyShowName),
                        onClick = { showLiveNotifyDialog = true },
                    )
                }
                if ("notification" in draft.notifyEffectIds) {
                    FooterText(
                        text = notificationFooterLabel(
                            draft.notifyNotificationBody,
                            "Remember to check in",
                        ),
                        onClick = { notificationDialogKind = "notify" },
                    )
                }
                if ("sound" in draft.notifyEffectIds) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Sound: ${OverwatchConfig.formatSoundDuration(draft.notifySoundDurationMs)}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { soundDurationKind = "notify" },
                        )
                        Text(
                            if (previewingNotify) "Stop preview" else "Preview",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                if (previewingNotify) stopPreview() else {
                                    previewSound("sound")
                                    previewingNotify = true
                                    previewingAlarm = false
                                    previewingSafety = false
                                }
                            },
                        )
                    }
                }
                if ("vibrate" in draft.notifyEffectIds) {
                    Text(
                        "Vibrate: ${OverwatchConfig.formatSoundDuration(draft.notifyVibrateDurationMs)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { vibrateDurationKind = "notify" },
                    )
                }
                if ("flashlight" in draft.notifyEffectIds) {
                    Text(
                        flashlightFooterLabel(
                            draft.notifyFlashlightMode,
                            draft.notifyFlashlightDurationMs,
                            draft.notifyFlickerOnMs,
                            draft.notifyFlickerOffMs,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { flashlightKind = "notify" },
                    )
                }
            },
        )
        EffectGroup(
            title = "Dismiss Mode",
            hint = "How you will verify",
            ids = DismissCatalog.chipIds(draft.dismissEffectIds),
            labels = { DismissCatalog.labelOf(it) },
            options = DismissCatalog.menuOptions(),
            conflicts = dismissConflicts,
            conflictMessage = when {
                pinRequiredMissing && DismissCatalog.conflicts(draft.dismissEffectIds).isEmpty() ->
                    "Set a PIN to use this dismiss method"
                pinRequiredMissing ->
                    "Highlighted effects conflict, or PIN is not set yet"
                else -> "Highlighted effects cannot be held at the same time"
            },
            onAdd = { id ->
                val addDismiss = {
                    val current = draftRef.current
                    if (id == "fingerprint") {
                        onChange(
                            current.copy(
                                dismissEffectIds = DismissCatalog.addFingerprint(
                                    current.dismissEffectIds,
                                    auto = true,
                                ),
                            ),
                        )
                    } else {
                        val nextIds = (current.dismissEffectIds + id).distinct()
                        onChange(
                            current.copy(
                                dismissEffectIds = nextIds,
                                dismissHardwareKey = if (id == "volume_down") {
                                    HardwareKeyOption.VOLUME_DOWN
                                } else {
                                    current.dismissHardwareKey
                                },
                            ),
                        )
                    }
                }
                if (id == "pin") requirePinOrPrompt(addDismiss) else addDismiss()
            },
            onRemove = { id ->
                val current = draftRef.current
                onChange(
                    current.copy(
                        dismissEffectIds = if (id == "fingerprint") {
                            DismissCatalog.removeFingerprint(current.dismissEffectIds)
                        } else {
                            current.dismissEffectIds - id
                        },
                        dismissHardwareKey = if (id == "volume_down") {
                            HardwareKeyOption.NONE
                        } else {
                            current.dismissHardwareKey
                        },
                    ),
                )
            },
            footer = {
                if (DismissCatalog.usesFingerprint(draft.dismissEffectIds)) {
                    Text(
                        DismissCatalog.fingerprintAutoLabel(draft.dismissEffectIds),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { fingerprintDialogKind = "dismiss" },
                    )
                }
                SensorTuningFooter(
                    ids = draft.dismissEffectIds,
                    powerTaps = draft.dismissPowerTaps,
                    shakeStrength = draft.dismissShakeStrength,
                    shakeCount = draft.dismissShakeCount,
                    crashThresholdG = draft.crashThresholdG,
                    crashStillnessMs = draft.crashStillnessMs,
                    turnoverHoldMs = draft.dismissTurnoverHoldMs,
                    showCrash = false,
                    onPowerTaps = { powerTapsDialogKind = "dismiss" },
                    onShake = { shakeDialogKind = "dismiss" },
                    onCrash = { showCrashDialog = true },
                    onTurnover = { turnoverDialogKind = "dismiss" },
                )
            },
        )

        Collapsible("Alarm Mode", hint = "What happens when you don't verify") {
            EffectGroup(
                title = "",
                ids = AlarmCatalog.chipIds(draft.alarmEffectIds),
                labels = { AlarmCatalog.labelOf(it) },
                options = AlarmCatalog.menuOptions(AlarmCatalog.all),
                conflicts = alarmConflicts,
                onAdd = { id ->
                    if (id == "siren") {
                        openRingtonePicker("alarm")
                    } else {
                        addAfterPermissions(FeaturePermissions.alarmPermissions(id)) {
                            val current = draftRef.current
                            val already = id in current.alarmEffectIds
                            val ids = (current.alarmEffectIds + id).distinct()
                            onChange(
                                current.copy(
                                    alarmEffectIds = ids,
                                    alarmVibrateDurationMs = when {
                                        id != "vibrate" || already -> current.alarmVibrateDurationMs
                                        else -> OverwatchConfig.SOUND_UNTIL_DISMISSED
                                    },
                                    alarmFlashlightDurationMs = when {
                                        id != "flashlight" || already -> current.alarmFlashlightDurationMs
                                        else -> OverwatchConfig.SOUND_UNTIL_DISMISSED
                                    },
                                    alarmFlashlightMode = when {
                                        id != "flashlight" || already -> current.alarmFlashlightMode
                                        else -> OverwatchConfig.FLASHLIGHT_STEADY
                                    },
                                ),
                            )
                            if (id == "location" && "sms" !in ids && "log" !in ids) {
                                locationRequireDialog = true
                            }
                            if (id == "clock_alarm") clockRoundDialog = true
                        }
                    }
                },
                onRemove = { id ->
                    val nextIds = draft.alarmEffectIds.toMutableList().also { list ->
                        list.removeAll { it == id }
                        if (id == "call") list.removeAll { it == "quiet_call" }
                    }
                    onChange(
                        draft.copy(
                            alarmEffectIds = nextIds,
                            alarmSoundUri = if (id == "siren") "" else draft.alarmSoundUri,
                            alarmSoundDurationMs = if (id == "siren") {
                                OverwatchConfig.SOUND_UNTIL_DISMISSED
                            } else {
                                draft.alarmSoundDurationMs
                            },
                            locationRecent = if (id == "location") false else draft.locationRecent,
                            locationContinuous = if (id == "location") false else draft.locationContinuous,
                        ),
                    )
                },
                nested = true,
            )
            if ("siren" in draft.alarmEffectIds) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Sound: ${OverwatchConfig.formatSoundDuration(draft.alarmSoundDurationMs)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { soundDurationKind = "alarm" },
                    )
                    Text(
                        if (previewingAlarm) "Stop preview" else "Preview",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            if (previewingAlarm) stopPreview() else {
                                previewSound("siren")
                                previewingAlarm = true
                                previewingNotify = false
                                previewingSafety = false
                            }
                        },
                    )
                }
            }
            if ("vibrate" in draft.alarmEffectIds) {
                Text(
                    "Vibrate: ${OverwatchConfig.formatSoundDuration(draft.alarmVibrateDurationMs)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { vibrateDurationKind = "alarm" },
                )
            }
            if ("flashlight" in draft.alarmEffectIds) {
                Text(
                    flashlightFooterLabel(
                        draft.alarmFlashlightMode,
                        draft.alarmFlashlightDurationMs,
                        draft.alarmFlickerOnMs,
                        draft.alarmFlickerOffMs,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { flashlightKind = "alarm" },
                )
            }
            if (draft.alarmEffectIds.any { it == "call" || it == "quiet_call" }) {
                FooterText(
                    text = callFooterLabel(draft.callContacts()),
                    onClick = { callDialogKind = "alarm" },
                )
            }
            if ("sms" in draft.alarmEffectIds) {
                SmsFooterBlock(
                    peopleCount = draft.smsContacts().size,
                    message = draft.alarmSmsBody,
                    showPanicInfo = draft.sendTriggerMode,
                    onClick = { smsDialogKind = "alarm" },
                )
            }
            if ("location" in draft.alarmEffectIds) {
                Text(
                    locationFooterLabel(draft.locationRecent, draft.locationContinuous),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { showLocationDialog = true },
                )
            }
            if ("notification" in draft.alarmEffectIds) {
                FooterText(
                    text = notificationFooterLabel(draft.alarmNotificationBody, "I need help"),
                    onClick = { notificationDialogKind = "alarm" },
                )
            }
        }

        EffectGroup(
            title = "Panic Mode",
            hint = "How you will manually trigger the alarm",
            ids = draft.panicEffectIds,
            labels = { PanicModeCatalog.labelOf(it) },
            options = PanicModeCatalog.menuOptions(),
            conflicts = panicConflicts,
            onAdd = { id ->
                val current = draftRef.current
                val next = when {
                    id == PanicModeCatalog.NONE -> listOf(PanicModeCatalog.NONE)
                    else -> (current.panicEffectIds.filter { it != PanicModeCatalog.NONE } + id).distinct()
                }
                val crashDefaults = id == PanicModeCatalog.CRASH_DETECT
                onChange(
                    current.copy(
                        panicEffectIds = PanicModeCatalog.sanitize(next),
                        crashThresholdG = if (crashDefaults) {
                            CrashSensitivity.MEDIUM.thresholdG
                        } else {
                            current.crashThresholdG
                        },
                        crashStillnessMs = if (crashDefaults) {
                            CrashSensitivity.MEDIUM.defaultStillnessMs
                        } else {
                            current.crashStillnessMs
                        },
                    ),
                )
            },
            onRemove = { id ->
                val next = draft.panicEffectIds - id
                onChange(
                    draft.copy(
                        panicEffectIds = if (next.isEmpty()) listOf(PanicModeCatalog.NONE) else next,
                    ),
                )
            },
            footer = {
                SensorTuningFooter(
                    ids = draft.panicEffectIds,
                    powerTaps = draft.panicPowerTaps,
                    shakeStrength = draft.shakeStrength,
                    shakeCount = draft.shakeCount,
                    crashThresholdG = draft.crashThresholdG,
                    crashStillnessMs = draft.crashStillnessMs,
                    turnoverHoldMs = draft.turnoverHoldMs,
                    showCrash = true,
                    onPowerTaps = { powerTapsDialogKind = "panic" },
                    onShake = { shakeDialogKind = "panic" },
                    onCrash = { showCrashDialog = true },
                    onTurnover = { turnoverDialogKind = "panic" },
                )
            },
        )

        Collapsible("Safety Mode", hint = "What happens when you are safe") {
            EffectGroup(
                title = "",
                ids = safetyIds,
                labels = { AlarmCatalog.labelOf(it) },
                options = AlarmCatalog.menuOptions(AlarmCatalog.safety),
                conflicts = safetyConflicts,
                onAdd = { id ->
                    if (!AlarmCatalog.isAllowedInSafety(id)) return@EffectGroup
                    if (id == "siren") {
                        openRingtonePicker("safety")
                    } else {
                        addAfterPermissions(FeaturePermissions.alarmPermissions(id)) {
                            val current = draftRef.current
                            val already = id in current.safetyEffectIds
                            val ids = when {
                                id == "none" -> listOf("none")
                                else -> (current.safetyEffectIds.filter { it != "none" } + id)
                                    .filter { AlarmCatalog.isAllowedInSafety(it) }
                                    .distinct()
                            }
                            onChange(
                                current.copy(
                                    safetyEffectIds = ids,
                                    safetyLocationRecent = false,
                                    safetyLocationContinuous = false,
                                    safetyVibrateDurationMs = when {
                                        id != "vibrate" || already -> current.safetyVibrateDurationMs
                                        else -> 10_000L
                                    },
                                    safetyFlashlightDurationMs = when {
                                        id != "flashlight" || already -> current.safetyFlashlightDurationMs
                                        else -> 5_000L
                                    },
                                    safetyFlashlightMode = when {
                                        id != "flashlight" || already -> current.safetyFlashlightMode
                                        else -> OverwatchConfig.FLASHLIGHT_STEADY
                                    },
                                ),
                            )
                            if (id == "location" && "sms" !in ids && "log" !in ids) {
                                locationRequireDialog = true
                            }
                        }
                    }
                },
                onRemove = { id ->
                    val next = draft.safetyEffectIds - id
                    onChange(
                        draft.copy(
                            safetyEffectIds = next.ifEmpty { listOf("none") },
                            safetySoundUri = if (id == "siren") "" else draft.safetySoundUri,
                            safetySoundDurationMs = if (id == "siren") 30_000L else draft.safetySoundDurationMs,
                            safetyLocationRecent = false,
                            safetyLocationContinuous = false,
                        ),
                    )
                },
                nested = true,
            )
            if ("siren" in safetyIds) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Sound: ${OverwatchConfig.formatSoundDuration(draft.safetySoundDurationMs)}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { soundDurationKind = "safety" },
                    )
                    Text(
                        if (previewingSafety) "Stop preview" else "Preview",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            if (previewingSafety) stopPreview() else {
                                previewSound("safety_siren")
                                previewingSafety = true
                                previewingAlarm = false
                                previewingNotify = false
                            }
                        },
                    )
                }
            }
            if ("vibrate" in safetyIds) {
                Text(
                    "Vibrate: ${OverwatchConfig.formatSoundDuration(draft.safetyVibrateDurationMs)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { vibrateDurationKind = "safety" },
                )
            }
            if ("flashlight" in safetyIds) {
                Text(
                    flashlightFooterLabel(
                        draft.safetyFlashlightMode,
                        draft.safetyFlashlightDurationMs,
                        draft.safetyFlickerOnMs,
                        draft.safetyFlickerOffMs,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { flashlightKind = "safety" },
                )
            }
            if ("call" in safetyIds) {
                FooterText(
                    text = callFooterLabel(draft.safetyCallContacts()),
                    onClick = { callDialogKind = "safety" },
                )
            }
            if ("sms" in safetyIds) {
                SmsFooterBlock(
                    peopleCount = draft.safetySmsContacts().size,
                    message = draft.safetySmsBody,
                    showPanicInfo = false,
                    defaultMessage = "I am safe",
                    onClick = { smsDialogKind = "safety" },
                )
            }
            if ("notification" in safetyIds) {
                FooterText(
                    text = notificationFooterLabel(draft.safetyNotificationBody, "I am safe"),
                    onClick = { notificationDialogKind = "safety" },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
            ) {
                Text("Trigger on Cancel", modifier = Modifier.weight(1f))
                Switch(
                    draft.safetyOnCancel,
                    onCheckedChange = { onChange(draft.copy(safetyOnCancel = it)) },
                )
            }
        }

        EffectGroup(
            title = "Cancel Mode",
            hint = "How to verify that you want to cancel overwatch",
            ids = CancelCatalog.chipIds(draft.cancelEffectIds),
            labels = { CancelCatalog.labelOf(it) },
            options = CancelCatalog.menuOptions(),
            conflicts = cancelConflicts,
            conflictMessage = when {
                pinRequiredMissing && CancelCatalog.conflicts(draft.cancelEffectIds).isEmpty() ->
                    "Set a PIN to use this cancel method"
                pinRequiredMissing ->
                    "Highlighted effects conflict, or PIN is not set yet"
                else -> "Highlighted effects cannot be held at the same time"
            },
            onAdd = { id ->
                val addCancel = {
                    val current = draftRef.current
                    val next = when {
                        id == CancelCatalog.SAME_AS_DISMISS -> listOf(CancelCatalog.SAME_AS_DISMISS)
                        id == "fingerprint" -> CancelCatalog.addFingerprint(
                            current.cancelEffectIds.filter { it != CancelCatalog.SAME_AS_DISMISS },
                            auto = true,
                        )
                        else -> (current.cancelEffectIds.filter { it != CancelCatalog.SAME_AS_DISMISS } + id)
                            .distinct()
                    }
                    onChange(current.copy(cancelEffectIds = next))
                }
                if (id == "pin") requirePinOrPrompt(addCancel) else addCancel()
            },
            onRemove = { id ->
                val next = if (id == "fingerprint") {
                    CancelCatalog.removeFingerprint(draft.cancelEffectIds)
                } else {
                    draft.cancelEffectIds - id
                }
                onChange(
                    draft.copy(
                        cancelEffectIds = next.ifEmpty { listOf(CancelCatalog.SAME_AS_DISMISS) },
                    ),
                )
            },
            footer = {
                if (DismissCatalog.usesFingerprint(draft.cancelEffectIds)) {
                    Text(
                        CancelCatalog.fingerprintAutoLabel(draft.cancelEffectIds),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { fingerprintDialogKind = "cancel" },
                    )
                }
                if (CancelCatalog.SAME_AS_DISMISS !in draft.cancelEffectIds) {
                    SensorTuningFooter(
                        ids = draft.cancelEffectIds,
                        powerTaps = draft.cancelPowerTaps,
                        shakeStrength = draft.cancelShakeStrength,
                        shakeCount = draft.cancelShakeCount,
                        crashThresholdG = draft.crashThresholdG,
                        crashStillnessMs = draft.crashStillnessMs,
                        turnoverHoldMs = draft.cancelTurnoverHoldMs,
                        showCrash = false,
                        onPowerTaps = { powerTapsDialogKind = "cancel" },
                        onShake = { shakeDialogKind = "cancel" },
                        onCrash = { showCrashDialog = true },
                        onTurnover = { turnoverDialogKind = "cancel" },
                    )
                }
            },
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                when {
                    !modesComplete -> incompleteDialog = "all modes not configured"
                    !alarmSmsOk -> incompleteDialog = "Add at least one Alarm SMS number"
                    !alarmCallOk -> incompleteDialog = "Add at least one Alarm Call number"
                    !safetySmsOk -> incompleteDialog = "Add at least one Safety SMS number"
                    !safetyCallOk -> incompleteDialog = "Add at least one Safety Call number"
                    !pinOk -> {
                        incompleteDialog = "Set a PIN before saving"
                        showPinDialog = true
                    }
                    else -> onSave()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = if (canSave) {
                ButtonDefaults.buttonColors()
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            },
        ) { Text("Save") }
        Spacer(Modifier.height(48.dp))
    }
    incompleteDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { incompleteDialog = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { incompleteDialog = null }) { Text("OK") }
            },
        )
    }
    if (covertHint) {
        AlertDialog(
            onDismissRequest = { covertHint = false },
            title = { Text("Covert mode") },
            text = { Text("Hide app actions") },
            confirmButton = {
                TextButton(onClick = { covertHint = false }) { Text("OK") }
            },
        )
    }
    if (graceHint) {
        AlertDialog(
            onDismissRequest = { graceHint = false },
            title = { Text("Grace period") },
            text = { Text("How long you can take to verify") },
            confirmButton = {
                TextButton(onClick = { graceHint = false }) { Text("OK") }
            },
        )
    }
    if (showPinDialog) {
        SetPinDialog(
            onConfirm = { pin ->
                onSetPin(pin)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false },
        )
    }
    if (clockRoundDialog) {
        AlertDialog(
            onDismissRequest = { clockRoundDialog = false },
            text = { Text("Alarms are set on the closest minute.") },
            confirmButton = {
                TextButton(onClick = { clockRoundDialog = false }) { Text("OK") }
            },
        )
    }
    if (locationRequireDialog) {
        AlertDialog(
            onDismissRequest = { locationRequireDialog = false },
            text = { Text("Location requires SMS or Log to be selected.") },
            confirmButton = {
                TextButton(onClick = { locationRequireDialog = false }) { Text("OK") }
            },
        )
    }
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                customActions = listOf(
                                    CustomAccessibilityAction("Hint") {
                                        locationRecentHint = true
                                        true
                                    },
                                )
                            },
                    ) {
                        Text(
                            "Recent Location",
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { locationRecentHint = true },
                                ),
                        )
                        Switch(
                            draft.locationRecent,
                            onCheckedChange = { onChange(draft.copy(locationRecent = it)) },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                customActions = listOf(
                                    CustomAccessibilityAction("Hint") {
                                        locationContinuousHint = true
                                        true
                                    },
                                )
                            },
                    ) {
                        Text(
                            "Continuous location",
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { locationContinuousHint = true },
                                ),
                        )
                        Switch(
                            draft.locationContinuous,
                            onCheckedChange = { onChange(draft.copy(locationContinuous = it)) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationDialog = false }) { Text("OK") }
            },
        )
    }
    if (locationRecentHint) {
        AlertDialog(
            onDismissRequest = { locationRecentHint = false },
            text = {
                Text("Sends ${settings.recentLocationPoints} recent locations")
            },
            confirmButton = {
                TextButton(onClick = { locationRecentHint = false }) { Text("OK") }
            },
        )
    }
    if (locationContinuousHint) {
        AlertDialog(
            onDismissRequest = { locationContinuousHint = false },
            text = {
                Text("Sends your current location every ${settings.continuousLocationMinutes}m")
            },
            confirmButton = {
                TextButton(onClick = { locationContinuousHint = false }) { Text("OK") }
            },
        )
    }
    val durationKind = soundDurationKind
    if (durationKind != null) {
        val selected = when (durationKind) {
            "alarm" -> if (draft.alarmSoundDurationMs > 0L) draft.alarmSoundDurationMs
            else lastFiniteAlarmDurationMs
            "notify" -> if (draft.notifySoundDurationMs > 0L) draft.notifySoundDurationMs
            else lastFiniteNotifyDurationMs
            else -> draft.safetySoundDurationMs.coerceAtLeast(1_000L)
        }
        val currentUntil = when (durationKind) {
            "alarm" -> draft.alarmSoundDurationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED
            "notify" -> draft.notifySoundDurationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED
            else -> false
        }
        SoundDurationDialog(
            title = "How long should the sound play?",
            selectedMs = selected,
            untilDismissed = currentUntil,
            allowUntilDismissed = durationKind != "safety",
            onSelect = { ms ->
                if (durationKind == "alarm") {
                    if (ms == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
                        onChange(draft.copy(alarmSoundDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED))
                    } else {
                        val finite = ms.coerceAtLeast(1_000L)
                        lastFiniteAlarmDurationMs = finite
                        onChange(draft.copy(alarmSoundDurationMs = finite))
                    }
                } else if (durationKind == "notify") {
                    if (ms == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
                        onChange(draft.copy(notifySoundDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED))
                    } else {
                        val finite = ms.coerceAtLeast(1_000L)
                        lastFiniteNotifyDurationMs = finite
                        onChange(draft.copy(notifySoundDurationMs = finite))
                    }
                } else {
                    onChange(draft.copy(safetySoundDurationMs = ms.coerceAtLeast(1_000L)))
                }
                soundDurationKind = null
            },
            onDismiss = { soundDurationKind = null },
        )
    }
    val vibrateKind = vibrateDurationKind
    if (vibrateKind != null) {
        val selected = when (vibrateKind) {
            "alarm" -> if (draft.alarmVibrateDurationMs > 0L) draft.alarmVibrateDurationMs
            else lastFiniteAlarmVibrateMs
            "notify" -> if (draft.notifyVibrateDurationMs > 0L) draft.notifyVibrateDurationMs
            else lastFiniteNotifyVibrateMs
            else -> draft.safetyVibrateDurationMs.coerceAtLeast(1_000L)
        }
        val currentUntil = when (vibrateKind) {
            "alarm" -> draft.alarmVibrateDurationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED
            "notify" -> draft.notifyVibrateDurationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED
            else -> false
        }
        SoundDurationDialog(
            title = "How long should it vibrate?",
            selectedMs = selected,
            untilDismissed = currentUntil,
            allowUntilDismissed = vibrateKind != "safety",
            onSelect = { ms ->
                when (vibrateKind) {
                    "alarm" -> {
                        if (ms == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
                            onChange(draft.copy(alarmVibrateDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED))
                        } else {
                            val finite = ms.coerceAtLeast(1_000L)
                            lastFiniteAlarmVibrateMs = finite
                            onChange(draft.copy(alarmVibrateDurationMs = finite))
                        }
                    }
                    "notify" -> {
                        if (ms == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
                            onChange(draft.copy(notifyVibrateDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED))
                        } else {
                            val finite = ms.coerceAtLeast(1_000L)
                            lastFiniteNotifyVibrateMs = finite
                            onChange(draft.copy(notifyVibrateDurationMs = finite))
                        }
                    }
                    else -> onChange(draft.copy(safetyVibrateDurationMs = ms.coerceAtLeast(1_000L)))
                }
                vibrateDurationKind = null
            },
            onDismiss = { vibrateDurationKind = null },
        )
    }
    val flashKind = flashlightKind
    if (flashKind != null) {
        val durationMs = when (flashKind) {
            "alarm" -> draft.alarmFlashlightDurationMs
            "safety" -> draft.safetyFlashlightDurationMs
            else -> draft.notifyFlashlightDurationMs
        }
        val selectedFinite = when (flashKind) {
            "alarm" -> if (durationMs > 0L) durationMs else lastFiniteAlarmFlashlightMs
            "notify" -> if (durationMs > 0L) durationMs else lastFiniteNotifyFlashlightMs
            else -> durationMs.coerceAtLeast(1_000L)
        }
        val mode = when (flashKind) {
            "alarm" -> draft.alarmFlashlightMode
            "safety" -> draft.safetyFlashlightMode
            else -> draft.notifyFlashlightMode
        }
        val (onMs, offMs) = when (flashKind) {
            "alarm" -> draft.alarmFlickerOnMs to draft.alarmFlickerOffMs
            "safety" -> draft.safetyFlickerOnMs to draft.safetyFlickerOffMs
            else -> draft.notifyFlickerOnMs to draft.notifyFlickerOffMs
        }
        FlashlightSettingsDialog(
            selectedMs = selectedFinite,
            untilDismissed = durationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED,
            allowUntilDismissed = flashKind != "safety",
            initialMode = OverwatchConfig.normalizeFlashlightMode(mode),
            flickerOnMs = onMs,
            flickerOffMs = offMs,
            onSelect = { ms, nextMode, nextOn, nextOff ->
                when (flashKind) {
                    "alarm" -> {
                        if (ms == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
                            onChange(
                                draft.copy(
                                    alarmFlashlightDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED,
                                    alarmFlashlightMode = nextMode,
                                    alarmFlickerOnMs = nextOn,
                                    alarmFlickerOffMs = nextOff,
                                ),
                            )
                        } else {
                            val finite = ms.coerceAtLeast(1_000L)
                            lastFiniteAlarmFlashlightMs = finite
                            onChange(
                                draft.copy(
                                    alarmFlashlightDurationMs = finite,
                                    alarmFlashlightMode = nextMode,
                                    alarmFlickerOnMs = nextOn,
                                    alarmFlickerOffMs = nextOff,
                                ),
                            )
                        }
                    }
                    "safety" -> onChange(
                        draft.copy(
                            safetyFlashlightDurationMs = ms.coerceAtLeast(1_000L),
                            safetyFlashlightMode = nextMode,
                            safetyFlickerOnMs = nextOn,
                            safetyFlickerOffMs = nextOff,
                        ),
                    )
                    else -> {
                        if (ms == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
                            onChange(
                                draft.copy(
                                    notifyFlashlightDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED,
                                    notifyFlashlightMode = nextMode,
                                    notifyFlickerOnMs = nextOn,
                                    notifyFlickerOffMs = nextOff,
                                ),
                            )
                        } else {
                            val finite = ms.coerceAtLeast(1_000L)
                            lastFiniteNotifyFlashlightMs = finite
                            onChange(
                                draft.copy(
                                    notifyFlashlightDurationMs = finite,
                                    notifyFlashlightMode = nextMode,
                                    notifyFlickerOnMs = nextOn,
                                    notifyFlickerOffMs = nextOff,
                                ),
                            )
                        }
                    }
                }
                flashlightKind = null
            },
            onDismiss = { flashlightKind = null },
        )
    }
    val activeShakeKind = shakeDialogKind
    if (activeShakeKind != null) {
        val (strength, count) = when (activeShakeKind) {
            "dismiss" -> draft.dismissShakeStrength to draft.dismissShakeCount
            "cancel" -> draft.cancelShakeStrength to draft.cancelShakeCount
            else -> draft.shakeStrength to draft.shakeCount
        }
        ShakeSettingsDialog(
            strength = strength,
            count = count,
            onSelect = { nextStrength, nextCount ->
                onChange(
                    when (activeShakeKind) {
                        "dismiss" -> draft.copy(
                            dismissShakeStrength = nextStrength,
                            dismissShakeCount = nextCount,
                        )
                        "cancel" -> draft.copy(
                            cancelShakeStrength = nextStrength,
                            cancelShakeCount = nextCount,
                        )
                        else -> draft.copy(shakeStrength = nextStrength, shakeCount = nextCount)
                    },
                )
                shakeDialogKind = null
            },
            onDismiss = { shakeDialogKind = null },
        )
    }
    val activePowerKind = powerTapsDialogKind
    if (activePowerKind != null) {
        val taps = when (activePowerKind) {
            "dismiss" -> draft.dismissPowerTaps
            "cancel" -> draft.cancelPowerTaps
            else -> draft.panicPowerTaps
        }
        PowerTapsDialog(
            taps = taps,
            onSelect = { next ->
                val coerced = next.coerceIn(2, 10)
                onChange(
                    when (activePowerKind) {
                        "dismiss" -> draft.copy(dismissPowerTaps = coerced)
                        "cancel" -> draft.copy(cancelPowerTaps = coerced)
                        else -> draft.copy(panicPowerTaps = coerced)
                    },
                )
                powerTapsDialogKind = null
            },
            onDismiss = { powerTapsDialogKind = null },
        )
    }
    if (showCrashDialog) {
        CrashSensitivityDialog(
            thresholdG = draft.crashThresholdG,
            stillnessMs = draft.crashStillnessMs,
            onConfirm = { g, still ->
                onChange(
                    draft.copy(
                        crashThresholdG = g.coerceIn(1f, 16f),
                        crashStillnessMs = still.coerceAtLeast(1_000L),
                    ),
                )
                showCrashDialog = false
            },
            onDismiss = { showCrashDialog = false },
        )
    }
    val fingerprintKind = fingerprintDialogKind
    if (fingerprintKind != null) {
        val sourceIds = when (fingerprintKind) {
            "dismiss" -> draft.dismissEffectIds
            else -> draft.cancelEffectIds
        }
        var autoFingerprint by remember(fingerprintKind) {
            mutableStateOf(DismissCatalog.usesAutoFingerprint(sourceIds))
        }
        AlertDialog(
            onDismissRequest = { fingerprintDialogKind = null },
            title = { Text("Fingerprint") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Auto fingerprint", modifier = Modifier.weight(1f))
                    Switch(autoFingerprint, onCheckedChange = { autoFingerprint = it })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (fingerprintKind) {
                            "dismiss" -> onChange(
                                draft.copy(
                                    dismissEffectIds = DismissCatalog.setFingerprintAuto(
                                        draft.dismissEffectIds,
                                        auto = autoFingerprint,
                                    ),
                                ),
                            )
                            else -> onChange(
                                draft.copy(
                                    cancelEffectIds = CancelCatalog.setFingerprintAuto(
                                        draft.cancelEffectIds,
                                        auto = autoFingerprint,
                                    ),
                                ),
                            )
                        }
                        fingerprintDialogKind = null
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { fingerprintDialogKind = null }) { Text("Cancel") }
            },
        )
    }
    if (showScheduleDialog) {
        ScheduleDialog(
            draft = draft,
            onConfirm = { updated ->
                onChange(updated)
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false },
            onGraceHint = { graceHint = true },
        )
    }
    if (showLiveNotifyDialog) {
        LiveNotifyDialog(
            showName = draft.liveNotifyShowName,
            onConfirm = { showName ->
                onChange(draft.copy(liveNotifyShowName = showName))
                showLiveNotifyDialog = false
            },
            onDismiss = { showLiveNotifyDialog = false },
        )
    }
    val activeCallKind = callDialogKind
    if (activeCallKind != null) {
        CallDialog(
            kind = activeCallKind,
            entries = when (activeCallKind) {
                "safety" -> draft.safetyCallContactEntries()
                else -> draft.callContactEntries()
            },
            quietCall = "quiet_call" in draft.alarmEffectIds,
            onPick = { openContactPicker(if (activeCallKind == "safety") "safety_call" else "call") },
            onConfirm = { entries, quiet ->
                val withQuiet = if (activeCallKind == "alarm") {
                    val ids = draft.alarmEffectIds.toMutableList()
                    if (quiet && "quiet_call" !in ids) ids += "quiet_call"
                    if (!quiet) ids.removeAll { it == "quiet_call" }
                    onChange(
                        draft.withCallContacts(entries).copy(alarmEffectIds = ids.distinct()),
                    )
                } else {
                    onChange(draft.withSafetyCallContacts(entries))
                }
                callDialogKind = null
            },
            onDismiss = { callDialogKind = null },
        )
    }
    val activeSmsKind = smsDialogKind
    if (activeSmsKind != null) {
        SmsDialog(
            kind = activeSmsKind,
            entries = when (activeSmsKind) {
                "safety" -> draft.safetySmsContactEntries()
                else -> draft.smsContactEntries()
            },
            message = when (activeSmsKind) {
                "safety" -> draft.safetySmsBody
                else -> draft.alarmSmsBody
            },
            includePanicInfo = draft.sendTriggerMode,
            messagePlaceholder = when (activeSmsKind) {
                "safety" -> "I am safe"
                else -> "I need help"
            },
            onPick = { openContactPicker(if (activeSmsKind == "safety") "safety_sms" else "sms") },
            onConfirm = { entries, message, panic ->
                when (activeSmsKind) {
                    "safety" -> onChange(draft.withSafetySmsContacts(entries).copy(safetySmsBody = message))
                    else -> onChange(
                        draft.withSmsContacts(entries).copy(
                            alarmSmsBody = message,
                            sendTriggerMode = panic,
                        ),
                    )
                }
                smsDialogKind = null
            },
            onDismiss = { smsDialogKind = null },
        )
    }
    val activeNotificationKind = notificationDialogKind
    if (activeNotificationKind != null) {
        val (body, urgency, defaultMessage) = when (activeNotificationKind) {
            "notify" -> Triple(
                draft.notifyNotificationBody,
                draft.notifyNotificationUrgency,
                "Remember to check in",
            )
            "safety" -> Triple(
                draft.safetyNotificationBody,
                draft.safetyNotificationUrgency,
                "I am safe",
            )
            else -> Triple(
                draft.alarmNotificationBody,
                draft.alarmNotificationUrgency,
                "I need help",
            )
        }
        NotificationSettingsDialog(
            body = body,
            urgency = urgency,
            defaultMessage = defaultMessage,
            onConfirm = { newBody, newUrgency ->
                onChange(
                    when (activeNotificationKind) {
                        "notify" -> draft.copy(
                            notifyNotificationBody = newBody,
                            notifyNotificationUrgency = newUrgency,
                        )
                        "safety" -> draft.copy(
                            safetyNotificationBody = newBody,
                            safetyNotificationUrgency = newUrgency,
                        )
                        else -> draft.copy(
                            alarmNotificationBody = newBody,
                            alarmNotificationUrgency = newUrgency,
                        )
                    },
                )
                notificationDialogKind = null
            },
            onDismiss = { notificationDialogKind = null },
        )
    }
    val activeTurnoverKind = turnoverDialogKind
    if (activeTurnoverKind != null) {
        val holdMs = when (activeTurnoverKind) {
            "dismiss" -> draft.dismissTurnoverHoldMs
            "cancel" -> draft.cancelTurnoverHoldMs
            else -> draft.turnoverHoldMs
        }
        TurnoverDialog(
            holdMs = holdMs,
            onConfirm = { ms ->
                val coerced = ms.coerceAtLeast(100L)
                onChange(
                    when (activeTurnoverKind) {
                        "dismiss" -> draft.copy(dismissTurnoverHoldMs = coerced)
                        "cancel" -> draft.copy(cancelTurnoverHoldMs = coerced)
                        else -> draft.copy(turnoverHoldMs = coerced)
                    },
                )
                turnoverDialogKind = null
            },
            onDismiss = { turnoverDialogKind = null },
        )
    }
}

@Composable
private fun Collapsible(
    title: String,
    hint: String,
    startExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    CollapsibleSection(title = title, hint = hint, startExpanded = startExpanded, content = content)
}

@Composable
private fun ContactListSection(
    title: String,
    entries: List<String>,
    onAddBlank: () -> Unit,
    onPick: () -> Unit,
    onChangeAt: (Int, String) -> Unit,
    onRemoveAt: (Int) -> Unit,
) {
    val focusRequesters = remember(entries.size) { List(entries.size) { FocusRequester() } }
    var focusNewest by remember { mutableStateOf(false) }
    LaunchedEffect(entries.size, focusNewest) {
        if (focusNewest && entries.isNotEmpty()) {
            focusRequesters.lastOrNull()?.requestFocus()
            focusNewest = false
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        if (title.isNotEmpty()) {
            Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        }
        Text(
            "Type number",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .then(if (title.isEmpty()) Modifier.weight(1f) else Modifier)
                .clickable {
                    onAddBlank()
                    focusNewest = true
                }
                .padding(vertical = 12.dp),
        )
        IconButton(onClick = onPick) {
            Icon(Icons.Default.Add, contentDescription = "Add from contacts")
        }
    }
    entries.forEachIndexed { index, number ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = number,
                onValueChange = { value -> onChangeAt(index, value) },
                label = { Text("Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequesters[index]),
            )
            TextButton(onClick = { onRemoveAt(index) }) { Text("Remove") }
        }
    }
}

@Composable
private fun SetPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pinText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PIN") },
        text = {
            OutlinedTextField(
                value = pinText,
                onValueChange = { value ->
                    pinText = value.filter { it.isDigit() }.take(8)
                },
                label = { Text("PIN (4–8 digits)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pinText) },
                enabled = pinText.length in 4..8,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinutesField(
    label: String,
    ms: Long,
    onLabelLongPress: (() -> Unit)? = null,
    onMs: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(OverwatchConfig.formatDurationInput(ms)) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            onMs(OverwatchConfig.parseDurationInput(value))
        },
        label = {
            Text(
                label,
                modifier = if (onLabelLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = onLabelLongPress,
                    )
                } else {
                    Modifier
                },
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLabelLongPress != null) {
                    Modifier.semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Hint") {
                                onLabelLongPress()
                                true
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInBeforeField(
    hour: Int,
    minute: Int,
    onTime: (Int, Int) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Check in before %02d:%02d".format(hour, minute))
    }
    if (show) {
        val state = rememberTimePickerState(
            initialHour = hour.coerceIn(0, 23),
            initialMinute = minute.coerceIn(0, 59),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onTime(state.hour, state.minute)
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectGroup(
    title: String,
    ids: List<String>,
    labels: (String) -> String,
    options: List<Pair<String, String>>,
    conflicts: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    nested: Boolean = false,
    hint: String = "",
    conflictMessage: String = "Highlighted effects cannot be held at the same time",
    footer: @Composable () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ids.forEach { id ->
                    val conflict = id in conflicts
                    FilterChip(
                        selected = true,
                        onClick = { onRemove(id) },
                        label = {
                            Text(
                                labels(id),
                                color = if (conflict) Color(0xFFFFB4AB) else Color.Unspecified,
                            )
                        },
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        options.filter { it.first !in ids }.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onAdd(id)
                                    menu = false
                                },
                            )
                        }
                    }
                }
            }
            if (conflicts.isNotEmpty()) {
                Text(
                    conflictMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
            footer()
        }
    }
    if (nested) {
        if (title.isNotEmpty()) Text(title, fontSize = 16.sp)
        body()
    } else {
        Collapsible(title, hint = hint, content = body)
    }
}

@Composable
private fun SoundDurationDialog(
    title: String = "How long should the sound play?",
    selectedMs: Long,
    untilDismissed: Boolean,
    allowUntilDismissed: Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var until by remember { mutableStateOf(untilDismissed && allowUntilDismissed) }
    var text by remember {
        mutableStateOf(OverwatchConfig.formatDurationInput(selectedMs.coerceAtLeast(1_000L)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (allowUntilDismissed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Until dismissed", modifier = Modifier.weight(1f))
                        Switch(until, onCheckedChange = { until = it })
                    }
                }
                if (!until) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Duration") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(
                        if (until && allowUntilDismissed) OverwatchConfig.SOUND_UNTIL_DISMISSED
                        else OverwatchConfig.parseDurationInput(text),
                    )
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FlashlightSettingsDialog(
    selectedMs: Long,
    untilDismissed: Boolean,
    allowUntilDismissed: Boolean,
    initialMode: String,
    flickerOnMs: Long,
    flickerOffMs: Long,
    onSelect: (durationMs: Long, mode: String, onMs: Long, offMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var until by remember { mutableStateOf(untilDismissed && allowUntilDismissed) }
    var text by remember {
        mutableStateOf(OverwatchConfig.formatDurationInput(selectedMs.coerceAtLeast(1_000L)))
    }
    var mode by remember {
        mutableStateOf(OverwatchConfig.normalizeFlashlightMode(initialMode))
    }
    var onText by remember {
        mutableStateOf(OverwatchConfig.formatDurationInput(flickerOnMs.coerceAtLeast(0L)))
    }
    var offText by remember {
        mutableStateOf(OverwatchConfig.formatDurationInput(flickerOffMs.coerceAtLeast(0L)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flashlight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (allowUntilDismissed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Until dismissed", modifier = Modifier.weight(1f))
                        Switch(until, onCheckedChange = { until = it })
                    }
                }
                if (!until) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Duration") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode != OverwatchConfig.FLASHLIGHT_SOS) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Flicker", modifier = Modifier.weight(1f))
                        Switch(
                            checked = mode == OverwatchConfig.FLASHLIGHT_FLICKER,
                            onCheckedChange = { on ->
                                mode = if (on) {
                                    OverwatchConfig.FLASHLIGHT_FLICKER
                                } else {
                                    OverwatchConfig.FLASHLIGHT_STEADY
                                }
                            },
                        )
                    }
                }
                if (mode == OverwatchConfig.FLASHLIGHT_FLICKER) {
                    OutlinedTextField(
                        value = onText,
                        onValueChange = { onText = it },
                        label = { Text("On (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = offText,
                        onValueChange = { offText = it },
                        label = { Text("Off (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode != OverwatchConfig.FLASHLIGHT_FLICKER) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("SOS", modifier = Modifier.weight(1f))
                        Switch(
                            checked = mode == OverwatchConfig.FLASHLIGHT_SOS,
                            onCheckedChange = { on ->
                                mode = if (on) {
                                    OverwatchConfig.FLASHLIGHT_SOS
                                } else {
                                    OverwatchConfig.FLASHLIGHT_STEADY
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(
                        if (until && allowUntilDismissed) OverwatchConfig.SOUND_UNTIL_DISMISSED
                        else OverwatchConfig.parseDurationInput(text),
                        mode,
                        OverwatchConfig.parseDurationInput(onText).coerceAtLeast(0L),
                        OverwatchConfig.parseDurationInput(offText).coerceAtLeast(0L),
                    )
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FooterText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        fontSize = 14.sp,
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SmsFooterBlock(
    peopleCount: Int,
    message: String,
    showPanicInfo: Boolean,
    defaultMessage: String = "I need help",
    onClick: () -> Unit,
) {
    val peopleLabel = peopleCountLabel(peopleCount)
    val panicSuffix = if (showPanicInfo) " (Panic Info)" else ""
    val messageText = message.trim().ifEmpty { defaultMessage }.let {
        if (it.length > 48) it.take(45) + "…" else it
    }
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "SMS: $peopleLabel$panicSuffix",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        Text(
            "Message: $messageText",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

private fun peopleCountLabel(count: Int): String =
    if (count == 1) "1 person" else "$count people"

private fun callFooterLabel(contacts: List<String>): String {
    val count = contacts.size
    return "Call: ${peopleCountLabel(count)}"
}

private fun notificationFooterLabel(body: String, defaultMessage: String): String {
    val text = body.trim().ifEmpty { defaultMessage }
    return "Notification: $text"
}

private fun liveNotifyFooterLabel(showName: Boolean): String =
    if (showName) "Live Notify: Show Overwatch" else "Live Notify: Default"

private fun turnoverFooterLabel(holdMs: Long): String =
    "Turnover: ${OverwatchConfig.formatDuration(holdMs)}"

@Composable
private fun SensorTuningFooter(
    ids: Collection<String>,
    powerTaps: Int,
    shakeStrength: ShakeStrength,
    shakeCount: Int,
    crashThresholdG: Float,
    crashStillnessMs: Long,
    turnoverHoldMs: Long,
    showCrash: Boolean,
    onPowerTaps: () -> Unit,
    onShake: () -> Unit,
    onCrash: () -> Unit,
    onTurnover: () -> Unit,
) {
    if ("power_button" in ids) {
        Text(
            "Power Button: $powerTaps Taps",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onPowerTaps),
        )
    }
    if ("shake" in ids) {
        Text(
            "Shake: ${shakeStrength.name.lowercase().replaceFirstChar { it.titlecase() }}, $shakeCount Shakes",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onShake),
        )
    }
    if (showCrash && "crash_detect" in ids) {
        Text(
            crashFooterLabel(crashThresholdG, crashStillnessMs),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onCrash),
        )
    }
    if ("turnover" in ids) {
        FooterText(
            text = turnoverFooterLabel(turnoverHoldMs),
            onClick = onTurnover,
        )
    }
}

private fun crashFooterLabel(thresholdG: Float, stillnessMs: Long): String {
    val preset = CrashSensitivity.fromThresholdG(thresholdG)
    val sens = preset?.name?.lowercase()?.replaceFirstChar { it.titlecase() }
        ?: formatCrashG(thresholdG)
    return "Crash Detect: $sens, ${OverwatchConfig.formatDuration(stillnessMs)} Stillness"
}

private fun flashlightFooterLabel(
    mode: String,
    durationMs: Long,
    flickerOnMs: Long,
    flickerOffMs: Long,
): String {
    val durationPart = if (durationMs == OverwatchConfig.SOUND_UNTIL_DISMISSED) {
        "until dismissed"
    } else {
        "for ${OverwatchConfig.formatDuration(durationMs)}"
    }
    return when (OverwatchConfig.normalizeFlashlightMode(mode)) {
        OverwatchConfig.FLASHLIGHT_SOS -> "Flashlight: SOS $durationPart"
        OverwatchConfig.FLASHLIGHT_FLICKER -> {
            val on = OverwatchConfig.formatDuration(flickerOnMs.coerceAtLeast(0L))
            val off = OverwatchConfig.formatDuration(flickerOffMs.coerceAtLeast(0L))
            "Flashlight: Flicker ($on, $off) $durationPart"
        }
        else -> "Flashlight: $durationPart"
    }
}

private fun locationFooterLabel(recent: Boolean, continuous: Boolean): String = when {
    recent && continuous -> "Location: Recent, Continuous"
    recent -> "Location: Recent"
    continuous -> "Location: Continuous"
    else -> "Location: Once"
}

private fun formatCrashG(g: Float): String = "${formatCrashGInput(g)}g"

/** Always use `.` as decimal separator so chip parsing matches `toFloatOrNull()`. */
private fun formatCrashGInput(g: Float): String =
    if (g == g.toLong().toFloat()) {
        g.toLong().toString()
    } else {
        "%.1f".format(Locale.US, g)
    }

@Composable
private fun CrashSensitivityDialog(
    thresholdG: Float,
    stillnessMs: Long,
    onConfirm: (Float, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var gText by remember { mutableStateOf(formatCrashGInput(thresholdG)) }
    var stillText by remember {
        mutableStateOf(OverwatchConfig.formatDurationInput(stillnessMs.coerceAtLeast(1_000L)))
    }
    fun applyPreset(option: CrashSensitivity) {
        gText = formatCrashGInput(option.thresholdG)
        stillText = OverwatchConfig.formatDurationInput(option.defaultStillnessMs)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crash sensitivity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "High is easiest to trip. After the jolt, the phone must stay still.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrashSensitivity.entries.forEach { option ->
                        val selected = CrashSensitivity.fromThresholdG(
                            gText.toFloatOrNull() ?: thresholdG,
                        ) == option
                        FilterChip(
                            selected = selected,
                            onClick = { applyPreset(option) },
                            label = {
                                Text(option.name.lowercase().replaceFirstChar { it.titlecase() })
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = gText,
                    onValueChange = { value ->
                        gText = value.filter { it.isDigit() || it == '.' }.take(5)
                    },
                    label = { Text("G's") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stillText,
                    onValueChange = { stillText = it },
                    label = { Text("Stillness") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val g = gText.toFloatOrNull()
            TextButton(
                onClick = {
                    if (g != null) {
                        onConfirm(g, OverwatchConfig.parseDurationInput(stillText).coerceAtLeast(1_000L))
                    }
                },
                enabled = g != null && g in 1f..16f,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ShakeSettingsDialog(
    strength: ShakeStrength,
    count: Int,
    onSelect: (ShakeStrength, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(strength) }
    var countText by remember { mutableStateOf(count.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shake settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Strength")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShakeStrength.entries.forEach { option ->
                        FilterChip(
                            selected = selected == option,
                            onClick = { selected = option },
                            label = {
                                Text(option.name.lowercase().replaceFirstChar { it.titlecase() })
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Shake count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(selected, (countText.toIntOrNull() ?: 3).coerceIn(1, 10))
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PowerTapsDialog(
    taps: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(taps.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Number of taps") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Taps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect((text.toIntOrNull() ?: 3).coerceIn(2, 10)) },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
    draft: OverwatchConfig,
    onConfirm: (OverwatchConfig) -> Unit,
    onDismiss: () -> Unit,
    onGraceHint: () -> Unit,
) {
    var local by remember(draft) { mutableStateOf(draft) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = local.repeatKind == RepeatKind.SINGLE,
                        onClick = { local = local.copy(repeatKind = RepeatKind.SINGLE) },
                        label = { Text("Single") },
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(
                        selected = local.repeatKind == RepeatKind.WINDOW || local.repeatKind == RepeatKind.COUNT,
                        onClick = {
                            if (local.repeatKind != RepeatKind.WINDOW && local.repeatKind != RepeatKind.COUNT) {
                                val window = if (local.windowMs > 0L) local.windowMs else 60_000L * 60
                                local = local.copy(repeatKind = RepeatKind.WINDOW, windowMs = window)
                            }
                        },
                        label = { Text("Repeat") },
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(
                        selected = local.repeatKind == RepeatKind.BY_TIME,
                        onClick = { local = local.copy(repeatKind = RepeatKind.BY_TIME) },
                        label = { Text("By Time") },
                    )
                }
                if (local.repeatKind == RepeatKind.WINDOW || local.repeatKind == RepeatKind.COUNT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = local.repeatKind == RepeatKind.WINDOW,
                            onClick = { local = local.copy(repeatKind = RepeatKind.WINDOW) },
                            label = { Text("Over an interval") },
                        )
                        Spacer(Modifier.padding(4.dp))
                        FilterChip(
                            selected = local.repeatKind == RepeatKind.COUNT,
                            onClick = { local = local.copy(repeatKind = RepeatKind.COUNT) },
                            label = { Text("Count") },
                        )
                    }
                }
                if (local.repeatKind != RepeatKind.BY_TIME) {
                    MinutesField("Check in every", local.intervalMs) {
                        local = local.copy(intervalMs = it)
                    }
                }
                if (local.repeatKind == RepeatKind.WINDOW) {
                    MinutesField("Over", local.windowMs) {
                        local = local.copy(windowMs = it)
                    }
                }
                if (local.repeatKind == RepeatKind.COUNT) {
                    OutlinedTextField(
                        value = local.repeatCount.toString(),
                        onValueChange = { local = local.copy(repeatCount = it.toIntOrNull() ?: 0) },
                        label = { Text("Number of Times") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (local.repeatKind == RepeatKind.BY_TIME) {
                    CheckInBeforeField(
                        hour = local.checkInHour,
                        minute = local.checkInMinute,
                        onTime = { h, m -> local = local.copy(checkInHour = h, checkInMinute = m) },
                    )
                }
                MinutesField(
                    label = "Grace period",
                    ms = local.graceDurationMs,
                    onLabelLongPress = onGraceHint,
                    onMs = { local = local.copy(graceDurationMs = it.coerceAtLeast(0L)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(local) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LiveNotifyDialog(
    showName: Boolean,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(showName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Live Notify") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Show active Overwatch", modifier = Modifier.weight(1f))
                Switch(show, onCheckedChange = { show = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(show) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CallDialog(
    kind: String,
    entries: List<String>,
    quietCall: Boolean,
    onPick: () -> Unit,
    onConfirm: (List<String>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var localEntries by remember(kind, entries) { mutableStateOf(entries) }
    var quiet by remember(kind, quietCall) { mutableStateOf(quietCall) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Call numbers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactListSection(
                    title = "",
                    entries = localEntries,
                    onAddBlank = { localEntries = localEntries + "" },
                    onPick = onPick,
                    onChangeAt = { index, value ->
                        localEntries = localEntries.toMutableList().also { it[index] = value }
                    },
                    onRemoveAt = { index ->
                        localEntries = localEntries.toMutableList().also { it.removeAt(index) }
                    },
                )
                if (kind == "alarm") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Quiet call (best effort)", modifier = Modifier.weight(1f))
                        Switch(quiet, onCheckedChange = { quiet = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localEntries, quiet) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SmsDialog(
    kind: String,
    entries: List<String>,
    message: String,
    includePanicInfo: Boolean,
    messagePlaceholder: String,
    onPick: () -> Unit,
    onConfirm: (List<String>, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var localEntries by remember(kind, entries) { mutableStateOf(entries) }
    var localMessage by remember(kind, message) { mutableStateOf(message) }
    var panic by remember(kind, includePanicInfo) { mutableStateOf(includePanicInfo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SMS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactListSection(
                    title = "",
                    entries = localEntries,
                    onAddBlank = { localEntries = localEntries + "" },
                    onPick = onPick,
                    onChangeAt = { index, value ->
                        localEntries = localEntries.toMutableList().also { it[index] = value }
                    },
                    onRemoveAt = { index ->
                        localEntries = localEntries.toMutableList().also { it.removeAt(index) }
                    },
                )
                OutlinedTextField(
                    value = localMessage,
                    onValueChange = { localMessage = it },
                    label = { Text("Message") },
                    placeholder = { Text(messagePlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                if (kind == "alarm") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Include panic info", modifier = Modifier.weight(1f))
                        Switch(panic, onCheckedChange = { panic = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localEntries, localMessage, panic) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationSettingsDialog(
    body: String,
    urgency: NotificationUrgency,
    defaultMessage: String,
    onConfirm: (String, NotificationUrgency) -> Unit,
    onDismiss: () -> Unit,
) {
    var localBody by remember(body) { mutableStateOf(body) }
    var localUrgency by remember(urgency) { mutableStateOf(urgency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = localBody,
                    onValueChange = { localBody = it },
                    label = { Text("Message") },
                    placeholder = { Text(defaultMessage) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text("Urgency")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotificationUrgency.entries.forEach { option ->
                        FilterChip(
                            selected = localUrgency == option,
                            onClick = { localUrgency = option },
                            label = {
                                Text(option.name.lowercase().replaceFirstChar { it.titlecase() })
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localBody, localUrgency) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TurnoverDialog(
    holdMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(holdMs) {
        mutableStateOf(OverwatchConfig.formatDurationInput(holdMs.coerceAtLeast(100L)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turnover") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Hold duration") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(OverwatchConfig.parseDurationInput(text).coerceAtLeast(100L)) },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
