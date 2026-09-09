package com.grandsphere.overwatch.domain.engine

import android.os.SystemClock
import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.DismissCatalog
import com.grandsphere.overwatch.domain.catalog.PanicModeCatalog
import com.grandsphere.overwatch.domain.model.AppState
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.LeaveReason
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.RepeatKind
import com.grandsphere.overwatch.domain.model.Submode
import com.grandsphere.overwatch.domain.security.PinHasher
import com.grandsphere.overwatch.runtime.VerboseLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface LoopCallbacks {
    fun onEnterOverwatch(config: OverwatchConfig)
    /** Stop alarm/safety output (siren, calls, etc.) and dismiss clock alarm. */
    fun onHaltAlarmEffects()
    /** Stop Notify Mode cues (sound, torch, clock). Do not halt Alarm/Safety. */
    fun onHaltNotifyCues()
    /** Cancel deadline scheduler; stop FGS unless [keepService] (Safety lasting effects). */
    fun onTeardownOverwatch(keepService: Boolean = false)
    fun onNotifyCue(config: OverwatchConfig, effectIds: List<String>, covert: Boolean)
    fun onAlarm(config: OverwatchConfig)
    fun onSafety(config: OverwatchConfig, resetCaps: Boolean)
    fun onAlarmCapSchedule(minutes: Int)
    fun onAlarmCapCancel()
    /** Hard quit (max duration / Quit effect / drawer). */
    fun onHardQuit()
    fun onUserLog(config: OverwatchConfig, message: String)
}

/**
 * Single source of truth for Wading vs Overwatch and the four submodes.
 * UI and OverwatchService both observe [state].
 */
class OverwatchEngine(
    private val callbacks: LoopCallbacks,
) {
    private val _state = MutableStateFlow<AppState>(AppState.Wading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Volatile
    var configs: List<OverwatchConfig> = emptyList()

    @Volatile
    var settingsPinHash: String = ""

    @Volatile
    var duressDigit: Char? = null

    @Volatile
    var duressPrefix: Boolean = false

    @Volatile
    var panicOnTwoWrongPins: Boolean = false

    @Volatile
    var failSecretly: Boolean = false

    @Volatile
    var maxAlarmDurationMinutes: Int = 60

    private var consecutiveWrongPins: Int = 0

    fun configById(id: Long): OverwatchConfig? = configs.find { it.id == id }

    fun enable(config: OverwatchConfig): Boolean {
        val current = _state.value
        if (current is AppState.Overwatch) {
            VerboseLog.d("Engine", "enable ignored already ${current.config.name} submode=${current.submode}")
            return false
        }
        val now = elapsed()
        val interval = loopIntervalMs(config)
        val running = AppState.Overwatch(
            config = config,
            submode = Submode.NotifyMode,
            deadlineElapsedMs = now + interval,
            intervalMs = interval,
            repeatsLeft = repeatsLeftInitial(config),
            windowEndElapsedMs = windowEnd(config, now),
            remainingMs = interval,
            startedByPanic = false,
            enteredAlarmThisRun = false,
            enabledAtEpochMs = System.currentTimeMillis(),
        )
        _state.value = running
        VerboseLog.ok("Engine", "enable ${config.name} intervalMs=$interval kind=${config.repeatKind}")
        callbacks.onEnterOverwatch(config)
        callbacks.onUserLog(config, "Activated")
        return true
    }

    fun panic(idleDefault: OverwatchConfig? = null, triggerLabel: String = "Panic, Pressed") {
        val current = _state.value
        if (current is AppState.Overwatch && current.submode == Submode.AlarmMode) {
            VerboseLog.d("Engine", "panic ignored already AlarmMode")
            return
        }
        val config = when (current) {
            is AppState.Overwatch -> current.config
            AppState.Wading -> idleDefault ?: configs.firstOrNull() ?: run {
                VerboseLog.fail("Engine", "panic no config")
                return
            }
        }
        VerboseLog.d("Engine", "panic ${config.name} from=${if (current is AppState.Overwatch) current.submode else "Wading"}")
        enterAlarm(config, startedByPanic = true, triggerLabel = triggerLabel)
    }

    /** One-way shade obfuscation for the current run (notification Hide / panic). */
    fun hideNotification() {
        val running = _state.value as? AppState.Overwatch ?: return
        if (running.notificationHidden) return
        _state.value = running.copy(notificationHidden = true)
        VerboseLog.d("Engine", "notification hidden")
    }

    fun cancelRequested(): Boolean {
        val running = _state.value as? AppState.Overwatch ?: return false
        return running.config.cancelEffectIds.isNotEmpty()
    }

    fun cancelConfirm() {
        VerboseLog.ok("Engine", "cancelConfirm")
        leaveToWading(LeaveReason.Cancel)
    }

    private fun cancelProofIds(config: OverwatchConfig): List<String> =
        CancelCatalog.resolvedProofIds(config.cancelEffectIds, config.dismissEffectIds)

    fun cancelWithPin(pin: String): Boolean {
        val running = _state.value as? AppState.Overwatch ?: return false
        val ids = cancelProofIds(running.config)
        val ok = "pin" in ids && pinMatches(pin)
        if (ok) {
            VerboseLog.ok("Engine", "cancelWithPin")
            leaveToWading(LeaveReason.Cancel)
            return true
        }
        if (isDuressPin(pin) && running.submode != Submode.AlarmMode) {
            VerboseLog.d("Engine", "cancelWithPin duress -> Alarm")
            enterAlarm(running.config, startedByPanic = false, secret = failSecretly)
            return true
        }
        VerboseLog.fail("Engine", "cancelWithPin mismatch")
        return false
    }

    /** Cancel pad: true if cancel succeeded or duress entered alarm (leave cancel UI). */
    fun cancelPinDigit(digit: Char, buffer: String): Pair<String, Boolean> {
        val running = _state.value as? AppState.Overwatch ?: return buffer to false
        if (duressPrefix && buffer.isEmpty() && digit == duressDigit) {
            if (running.submode != Submode.AlarmMode) {
                VerboseLog.d("Engine", "cancelPinDigit duress prefix -> Alarm")
                enterAlarm(running.config, startedByPanic = false, secret = failSecretly)
                return "" to true
            }
            return "" to false
        }
        val next = (buffer + digit).take(8)
        if (next.length >= 4) {
            return if (cancelWithPin(next)) "" to true else "" to false
        }
        return next to false
    }

    fun submitPinDigit(digit: Char) {
        val running = _state.value as? AppState.Overwatch ?: return
        if (duressPrefix && running.pinBuffer.isEmpty() && digit == duressDigit) {
            if (running.submode != Submode.AlarmMode) {
                VerboseLog.d("Engine", "submitPinDigit duress prefix -> Alarm")
                enterAlarm(running.config, startedByPanic = false, secret = failSecretly)
            } else {
                _state.update {
                    if (it is AppState.Overwatch) it.copy(pinBuffer = "") else it
                }
            }
            return
        }
        val next = (running.pinBuffer + digit).take(8)
        _state.update {
            if (it is AppState.Overwatch) it.copy(pinBuffer = next) else it
        }
        if (next.length >= 4) {
            attemptProof(next)
        }
    }

    fun pinBackspace() {
        _state.update {
            if (it is AppState.Overwatch) it.copy(pinBuffer = it.pinBuffer.dropLast(1)) else it
        }
    }

    fun fingerprintSuccess() {
        val running = _state.value as? AppState.Overwatch ?: return
        if (!DismissCatalog.usesFingerprint(proofIds(running))) return
        VerboseLog.ok("Engine", "fingerprintSuccess")
        onProofSuccess()
    }

    /**
     * Sensor trigger (power / turnover / shake / crash). Panic Mode wins, then dismiss, then cancel.
     * Panic always uses the existing immediate [panic] chain.
     */
    fun onSensorTrigger(effectId: String) {
        val running = _state.value as? AppState.Overwatch
        if (running != null) {
            val panicIds = running.config.panicEffectIds
            if (effectId != PanicModeCatalog.NONE && effectId in panicIds) {
                VerboseLog.ok("Engine", "sensor panic id=$effectId")
                panic(triggerLabel = "Panic, ${PanicModeCatalog.labelOf(effectId)}")
                return
            }
            if (effectId in proofIds(running)) {
                VerboseLog.ok("Engine", "sensor dismiss id=$effectId")
                onProofSuccess()
                return
            }
            if (effectId in cancelProofIds(running.config)) {
                VerboseLog.ok("Engine", "sensor cancel id=$effectId")
                leaveToWading(LeaveReason.Cancel)
                return
            }
            VerboseLog.d("Engine", "sensor ignored id=$effectId")
            return
        }
        // Idle: only panic if we somehow get a trigger without a run (should not arm sensors idle).
        VerboseLog.d("Engine", "sensor ignored idle id=$effectId")
    }

    fun pinClear() {
        _state.update {
            if (it is AppState.Overwatch) it.copy(pinBuffer = "") else it
        }
    }

    fun cancelWithTap(): Boolean {
        val running = _state.value as? AppState.Overwatch ?: return false
        if ("tap" !in cancelProofIds(running.config)) return false
        VerboseLog.ok("Engine", "cancelWithTap")
        leaveToWading(LeaveReason.Cancel)
        return true
    }

    fun cancelWithFingerprint(): Boolean {
        val running = _state.value as? AppState.Overwatch ?: return false
        if (!DismissCatalog.usesFingerprint(cancelProofIds(running.config))) return false
        VerboseLog.ok("Engine", "cancelWithFingerprint")
        leaveToWading(LeaveReason.Cancel)
        return true
    }

    fun tapDismiss() {
        val running = _state.value as? AppState.Overwatch ?: return
        val ids = proofIds(running)
        if ("tap" in ids) {
            VerboseLog.ok("Engine", "tapDismiss")
            onProofSuccess()
        } else {
            VerboseLog.fail("Engine", "tapDismiss not configured")
            onProofFailure()
        }
    }

    fun onVolumeDown(): Boolean {
        val running = _state.value as? AppState.Overwatch ?: return false
        if (running.config.dismissHardwareKey != HardwareKeyOption.VOLUME_DOWN &&
            "volume_down" !in proofIds(running) &&
            !CancelCatalog.usesVolumeDown(running.config.cancelEffectIds, running.config.dismissEffectIds)
        ) {
            return false
        }
        if ("volume_down" in proofIds(running) ||
            running.config.dismissHardwareKey == HardwareKeyOption.VOLUME_DOWN
        ) {
            VerboseLog.ok("Engine", "volumeDown dismiss")
            onProofSuccess()
            return true
        }
        if (CancelCatalog.usesVolumeDown(running.config.cancelEffectIds, running.config.dismissEffectIds)) {
            VerboseLog.ok("Engine", "volumeDown cancel")
            leaveToWading(LeaveReason.Cancel)
            return true
        }
        return false
    }

    fun onDeadline() {
        val running = _state.value as? AppState.Overwatch ?: return
        VerboseLog.d("Engine", "deadline submode=${running.submode} ${running.config.name}")
        when (running.submode) {
            Submode.NotifyMode -> notifyElapsed(running)
            Submode.DismissMode -> enterGrace(running)
            Submode.GraceMode -> {
                val graceEnd = running.graceDeadlineElapsedMs ?: return
                if (elapsed() >= graceEnd) {
                    enterAlarm(running.config, startedByPanic = false)
                }
            }
            Submode.AlarmMode -> Unit
        }
    }

    fun onAlarmMaxDuration() {
        val running = _state.value as? AppState.Overwatch
        VerboseLog.d("Engine", "alarm max duration config=${running?.config?.name}")
        if (running != null) {
            callbacks.onUserLog(running.config, "Stopped: max alarm duration")
            callbacks.onUserLog(running.config, "Idle")
        }
        callbacks.onHardQuit()
    }

    /** State only — teardown is owned by [LoopCallbacks.onHardQuit] / AppShutdown. */
    fun forceWadingHardQuit() {
        consecutiveWrongPins = 0
        VerboseLog.d("Engine", "forceWadingHardQuit")
        _state.value = AppState.Wading
    }

    fun tick() {
        val running = _state.value as? AppState.Overwatch ?: return
        if (running.submode == Submode.AlarmMode) return
        val left = running.deadlineElapsedMs - elapsed()
        _state.update {
            if (it is AppState.Overwatch) it.copy(remainingMs = left) else it
        }
        when (running.submode) {
            Submode.NotifyMode, Submode.DismissMode -> if (left <= 0L) onDeadline()
            Submode.GraceMode -> {
                val graceEnd = running.graceDeadlineElapsedMs ?: return
                if (elapsed() >= graceEnd) onDeadline()
            }
            Submode.AlarmMode -> Unit
        }
    }

    fun nextDeadlineElapsed(): Long? {
        val running = _state.value as? AppState.Overwatch ?: return null
        return when (running.submode) {
            Submode.AlarmMode -> null
            Submode.GraceMode -> running.graceDeadlineElapsedMs
            Submode.NotifyMode, Submode.DismissMode -> running.deadlineElapsedMs
        }
    }

    private fun notifyElapsed(running: AppState.Overwatch) {
        val cueIds = running.config.notifyEffectIds.ifEmpty { listOf("none") }
        val covert = running.config.covert
        try {
            callbacks.onNotifyCue(running.config, cueIds, covert)
            callbacks.onUserLog(running.config, "Reminder sent")
            enterGrace(running)
        } catch (t: Throwable) {
            VerboseLog.fail("Engine", "notify cue", t)
            callbacks.onUserLog(running.config, "Reminder failed")
            enterGrace(running.copy(errorFlag = true, errorMessage = t.message))
        }
    }

    private fun attemptProof(pin: String) {
        val running = _state.value as? AppState.Overwatch ?: return
        if (proofOk(pin, proofIds(running))) {
            consecutiveWrongPins = 0
            VerboseLog.ok("Engine", "proof PIN")
            onProofSuccess()
        } else if (isDuressPin(pin)) {
            consecutiveWrongPins = 0
            VerboseLog.d("Engine", "proof duress -> Alarm")
            enterAlarm(running.config, startedByPanic = false, secret = failSecretly)
        } else {
            consecutiveWrongPins += 1
            if (panicOnTwoWrongPins && consecutiveWrongPins >= 2) {
                consecutiveWrongPins = 0
                VerboseLog.d("Engine", "proof two wrong PINs -> Alarm")
                enterAlarm(running.config, startedByPanic = false, secret = failSecretly)
            } else {
                VerboseLog.fail("Engine", "proof PIN mismatch count=$consecutiveWrongPins")
                _state.update {
                    if (it is AppState.Overwatch) it.copy(pinBuffer = "") else it
                }
            }
        }
    }

    private fun proofOk(pin: String, ids: List<String>): Boolean {
        if ("pin" in ids) return pinMatches(pin)
        return false
    }

    private fun pinMatches(pin: String): Boolean =
        PinHasher.matches(pin, settingsPinHash)

    private fun isDuressPin(pin: String): Boolean {
        val digit = duressDigit ?: return false
        if (pin.length < 4) return false
        if (pinMatches(pin)) return false
        return if (duressPrefix) pin.first() == digit else pin.last() == digit
    }

    private fun proofIds(running: AppState.Overwatch): List<String> = when (running.submode) {
        Submode.NotifyMode, Submode.DismissMode, Submode.GraceMode -> running.config.dismissEffectIds
        Submode.AlarmMode -> emptyList()
    }

    private fun onProofSuccess() {
        consecutiveWrongPins = 0
        val running = _state.value as? AppState.Overwatch ?: return
        val checkedIn = running.copy(lastCheckInAtEpochMs = System.currentTimeMillis())
        VerboseLog.ok("Engine", "proofSuccess submode=${checkedIn.submode} kind=${checkedIn.config.repeatKind}")
        when (checkedIn.submode) {
            Submode.NotifyMode, Submode.DismissMode, Submode.GraceMode -> {
                callbacks.onUserLog(checkedIn.config, "Checked in")
                afterSuccessfulCheck(checkedIn)
            }
            Submode.AlarmMode -> Unit
        }
    }

    private fun afterSuccessfulCheck(running: AppState.Overwatch) {
        when (running.config.repeatKind) {
            RepeatKind.SINGLE -> leaveToWading(LeaveReason.LoopComplete)
            RepeatKind.COUNT -> {
                val left = (running.repeatsLeft ?: 1) - 1
                if (left <= 0) leaveToWading(LeaveReason.LoopComplete)
                else resetNotify(running.copy(repeatsLeft = left))
            }
            RepeatKind.WINDOW -> {
                val end = running.windowEndElapsedMs ?: elapsed()
                if (elapsed() >= end) leaveToWading(LeaveReason.LoopComplete)
                else resetNotify(running)
            }
            RepeatKind.BY_TIME -> resetNotify(running, afterByTimeSuccess = true)
        }
    }

    private fun resetNotify(running: AppState.Overwatch, afterByTimeSuccess: Boolean = false) {
        callbacks.onHaltNotifyCues()
        val now = elapsed()
        val interval = loopIntervalMs(running.config, afterByTimeSuccess)
        _state.value = running.copy(
            submode = Submode.NotifyMode,
            deadlineElapsedMs = now + interval,
            intervalMs = interval,
            remainingMs = interval,
            pinBuffer = "",
            errorFlag = false,
            errorMessage = null,
            graceDeadlineElapsedMs = null,
        )
        VerboseLog.ok("Engine", "resetNotify intervalMs=$interval repeatsLeft=${running.repeatsLeft}")
    }

    private fun onProofFailure() {
        val running = _state.value as? AppState.Overwatch ?: return
        when (running.submode) {
            Submode.NotifyMode, Submode.DismissMode -> enterGrace(running)
            Submode.GraceMode -> enterAlarm(running.config, startedByPanic = false)
            Submode.AlarmMode -> Unit
        }
    }

    private fun enterGrace(running: AppState.Overwatch) {
        val grace = running.config.graceDurationMs.coerceAtLeast(0L)
        if (grace <= 0L) {
            enterAlarm(running.config, startedByPanic = false)
            return
        }
        val now = elapsed()
        // Keep the dismiss-window deadline as the display anchor; grace is hidden.
        _state.value = running.copy(
            submode = Submode.GraceMode,
            remainingMs = running.deadlineElapsedMs - now,
            pinBuffer = "",
            graceDeadlineElapsedMs = now + grace,
        )
        VerboseLog.d("Engine", "enterGrace ${grace}ms")
        callbacks.onUserLog(running.config, "Grace started")
    }

    private fun enterAlarm(
        config: OverwatchConfig,
        startedByPanic: Boolean,
        secret: Boolean = false,
        triggerLabel: String? = null,
    ) {
        val current = _state.value
        if (current is AppState.Overwatch && current.submode == Submode.AlarmMode) return
        callbacks.onHaltNotifyCues()
        val previouslyEntered = (current as? AppState.Overwatch)?.enteredAlarmThisRun == true
        val prev = current as? AppState.Overwatch
        val now = elapsed()
        val wallNow = System.currentTimeMillis()
        val label = triggerLabel?.ifBlank { null }
            ?: if (startedByPanic) "Panic, Pressed" else "No check-in"
        val enabledAt = prev?.enabledAtEpochMs?.takeIf { it > 0L } ?: wallNow
        VerboseLog.ok("Engine", "enterAlarm ${config.name} panic=$startedByPanic secret=$secret trigger=$label")
        _state.value = AppState.Overwatch(
            config = config,
            submode = Submode.AlarmMode,
            deadlineElapsedMs = now,
            intervalMs = config.intervalMs,
            repeatsLeft = 0,
            windowEndElapsedMs = null,
            remainingMs = 0L,
            startedByPanic = startedByPanic,
            graceDeadlineElapsedMs = null,
            enteredAlarmThisRun = previouslyEntered || true,
            secretAlarm = secret,
            notificationHidden = true,
            enabledAtEpochMs = enabledAt,
            alarmAtEpochMs = wallNow,
            alarmTriggerLabel = label,
            lastCheckInAtEpochMs = prev?.lastCheckInAtEpochMs ?: 0L,
        )
        callbacks.onEnterOverwatch(config)
        callbacks.onAlarmCapSchedule(maxAlarmDurationMinutes)
        callbacks.onAlarm(config)
        val how = if (startedByPanic) "panic" else "failed to dismiss"
        callbacks.onUserLog(config, "Alarm triggered: $how")
    }

    private fun leaveToWading(reason: LeaveReason = LeaveReason.Cancel) {
        val running = _state.value as? AppState.Overwatch
        val config = running?.config
        val enteredAlarm = running?.enteredAlarmThisRun == true
        consecutiveWrongPins = 0
        _state.value = AppState.Wading
        VerboseLog.d(
            "Engine",
            "wading reason=$reason alarmThisRun=$enteredAlarm config=${config?.name}",
        )
        callbacks.onAlarmCapCancel()
        callbacks.onHaltAlarmEffects()
        val fireSafety = config != null && shouldFireSafety(reason, enteredAlarm, config)
        if (config != null) {
            callbacks.onUserLog(config, leaveUserMessage(reason))
            if (fireSafety) {
                VerboseLog.ok("Engine", "Safety Mode started ${config.name}")
                callbacks.onUserLog(config, "Safety Mode started")
                callbacks.onSafety(config, resetCaps = !enteredAlarm)
            } else {
                val skip = safetySkipReason(reason, enteredAlarm, config)
                VerboseLog.d("Engine", skip)
            }
            callbacks.onUserLog(config, "Idle")
        }
        val keepService = fireSafety && config != null && safetyNeedsForeground(config)
        callbacks.onTeardownOverwatch(keepService = keepService)
    }

    private fun leaveUserMessage(reason: LeaveReason): String = when (reason) {
        LeaveReason.Cancel -> "Canceled"
        LeaveReason.LoopComplete -> "Check-in completed"
        LeaveReason.HardQuit -> "Stopped"
    }

    private fun safetyNeedsForeground(config: OverwatchConfig): Boolean {
        val ids = config.safetyEffectIds.filter { it != "none" }.toSet()
        return "siren" in ids ||
            "vibrate" in ids ||
            "flashlight" in ids ||
            "record_video" in ids ||
            "record_audio" in ids ||
            "call" in ids ||
            "quiet_call" in ids ||
            (config.safetyLocationContinuous && "location" in ids)
    }

    private fun shouldFireSafety(
        reason: LeaveReason,
        enteredAlarmThisRun: Boolean,
        config: OverwatchConfig,
    ): Boolean {
        if (config.safetyEffectIds.filter { it != "none" }.isEmpty()) return false
        return when (reason) {
            LeaveReason.HardQuit -> false
            LeaveReason.LoopComplete -> true
            LeaveReason.Cancel -> config.safetyOnCancel && !enteredAlarmThisRun
        }
    }

    private fun safetySkipReason(
        reason: LeaveReason,
        enteredAlarmThisRun: Boolean,
        config: OverwatchConfig,
    ): String = when {
        config.safetyEffectIds.filter { it != "none" }.isEmpty() ->
            "Safety Mode skipped: no effects configured"
        reason == LeaveReason.HardQuit -> "Safety Mode skipped: hard quit"
        reason == LeaveReason.Cancel && !config.safetyOnCancel ->
            "Safety Mode skipped: cancel without Also when cancelled"
        reason == LeaveReason.Cancel && enteredAlarmThisRun ->
            "Safety Mode skipped: Alarm Mode already ran this run"
        else -> "Safety Mode skipped"
    }

    private fun repeatsLeftInitial(config: OverwatchConfig): Int? =
        if (config.repeatKind == RepeatKind.COUNT) config.repeatCount else null

    private fun windowEnd(config: OverwatchConfig, now: Long): Long? =
        if (config.repeatKind == RepeatKind.WINDOW) now + config.windowMs else null

    private fun loopIntervalMs(config: OverwatchConfig, afterByTimeSuccess: Boolean = false): Long =
        if (config.repeatKind == RepeatKind.BY_TIME) {
            OverwatchConfig.msUntilCheckIn(config.checkInHour, config.checkInMinute, afterByTimeSuccess)
        } else {
            config.intervalMs.coerceAtLeast(1_000L)
        }

    private fun elapsed(): Long = SystemClock.elapsedRealtime()
}
