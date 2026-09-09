package com.grandsphere.overwatch.domain.model

enum class RepeatKind {
    SINGLE,
    COUNT,
    WINDOW,
    BY_TIME,
}

enum class PanicActivation {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
}

enum class Submode {
    NotifyMode,
    DismissMode,
    GraceMode,
    AlarmMode,
}

enum class HardwareKeyOption {
    NONE,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_UP_DOUBLE,
}

enum class ShakeStrength {
    LOW,
    MEDIUM,
    HIGH,
}

enum class NotificationUrgency {
    LOW,
    DEFAULT,
    HIGH,
}

/** Crash / fall detect presets (g thresholds). Custom g values are also allowed. */
enum class CrashSensitivity {
    HIGH,
    MEDIUM,
    LOW,
    ;

    val thresholdG: Float
        get() = when (this) {
            HIGH -> 5.5f
            MEDIUM -> 8f
            LOW -> 12f
        }

    val defaultStillnessMs: Long
        get() = when (this) {
            HIGH -> 40_000L
            MEDIUM -> 50_000L
            LOW -> 60_000L
        }

    companion object {
        fun fromThresholdG(g: Float): CrashSensitivity? =
            entries.find { kotlin.math.abs(it.thresholdG - g) < 0.05f }
    }
}

/** How to add an effect later: implement the catalog interface and register it. Room stores [id] strings. */
data class OverwatchConfig(
    val id: Long = 0L,
    val name: String,
    val intervalMs: Long,
    val repeatKind: RepeatKind,
    val repeatCount: Int = 0,
    val windowMs: Long = 0L,
    val checkInHour: Int = 15,
    val checkInMinute: Int = 0,
    val covert: Boolean = false,
    val cancelEffectIds: List<String> = emptyList(),
    val pinHash: String? = null,
    val graceDurationMs: Long = 60_000L,
    val notifyEffectIds: List<String> = listOf("none"),
    val dismissEffectIds: List<String> = emptyList(),
    val graceNotifyEffectIds: List<String> = emptyList(),
    val graceDismissEffectIds: List<String> = emptyList(),
    val alarmEffectIds: List<String> = emptyList(),
    /** Legacy shared list; prefer [smsContactNumbers] / [callContactNumbers]. */
    val contactNumbers: String = "",
    val smsContactNumbers: String = "",
    val callContactNumbers: String = "",
    val alarmSmsBody: String = "",
    val notifySoundUri: String = "",
    /** Notify cue sound length; [SOUND_UNTIL_DISMISSED] keeps looping until halt. */
    val notifySoundDurationMs: Long = SOUND_UNTIL_DISMISSED,
    val alarmSoundUri: String = "",
    /** Alarm sound length; [SOUND_UNTIL_DISMISSED] keeps looping until halt. */
    val alarmSoundDurationMs: Long = SOUND_UNTIL_DISMISSED,
    val dismissHardwareKey: HardwareKeyOption = HardwareKeyOption.NONE,
    val locationRecent: Boolean = false,
    val locationContinuous: Boolean = false,
    val safetyEffectIds: List<String> = listOf("none"),
    val safetySmsBody: String = "",
    val safetySmsContactNumbers: String = "",
    val safetyCallContactNumbers: String = "",
    val safetyLocationRecent: Boolean = false,
    val safetyLocationContinuous: Boolean = false,
    val safetyOnCancel: Boolean = false,
    val safetySoundUri: String = "",
    /** Safety sound length; must be a positive duration (no until-dismissed). */
    val safetySoundDurationMs: Long = 30_000L,
    val notifyFlickerOnMs: Long = 2_000L,
    val notifyFlickerOffMs: Long = 2_000L,
    val alarmFlickerOnMs: Long = 2_000L,
    val alarmFlickerOffMs: Long = 2_000L,
    val safetyFlickerOnMs: Long = 2_000L,
    val safetyFlickerOffMs: Long = 2_000L,
    /** Panic Mode effect ids; default [none]. */
    val panicEffectIds: List<String> = listOf("none"),
    val panicPowerTaps: Int = 3,
    val dismissPowerTaps: Int = 3,
    val cancelPowerTaps: Int = 3,
    val shakeStrength: ShakeStrength = ShakeStrength.MEDIUM,
    val dismissShakeStrength: ShakeStrength = ShakeStrength.MEDIUM,
    val cancelShakeStrength: ShakeStrength = ShakeStrength.MEDIUM,
    val shakeCount: Int = 3,
    val dismissShakeCount: Int = 3,
    val cancelShakeCount: Int = 3,
    /** Crash Detect absolute spike threshold in g (Medium preset = 8). */
    val crashThresholdG: Float = 8f,
    /** How long must stay nearly still after spike (Medium preset = 50s). */
    val crashStillnessMs: Long = 50_000L,
    val persistentPanicNotification: Boolean = false,
    val liveNotifyShowName: Boolean = false,
    val alarmNotificationBody: String = "",
    val safetyNotificationBody: String = "",
    val notifyNotificationBody: String = "",
    val notifyNotificationUrgency: NotificationUrgency = NotificationUrgency.DEFAULT,
    val alarmNotificationUrgency: NotificationUrgency = NotificationUrgency.DEFAULT,
    val safetyNotificationUrgency: NotificationUrgency = NotificationUrgency.DEFAULT,
    /** Face-down hold before turnover triggers (default 700 ms). Panic / Dismiss / Cancel each have their own. */
    val turnoverHoldMs: Long = 700L,
    val dismissTurnoverHoldMs: Long = 700L,
    val cancelTurnoverHoldMs: Long = 700L,
    /** When true, SMS alarm payloads include trigger diagnostics. Log always includes them. */
    val sendTriggerMode: Boolean = true,
    val notifyVibrateDurationMs: Long = 10_000L,
    val alarmVibrateDurationMs: Long = SOUND_UNTIL_DISMISSED,
    val safetyVibrateDurationMs: Long = 10_000L,
    val notifyFlashlightDurationMs: Long = 5_000L,
    val alarmFlashlightDurationMs: Long = SOUND_UNTIL_DISMISSED,
    val safetyFlashlightDurationMs: Long = 5_000L,
    /** steady | flicker | sos */
    val notifyFlashlightMode: String = FLASHLIGHT_STEADY,
    val alarmFlashlightMode: String = FLASHLIGHT_STEADY,
    val safetyFlashlightMode: String = FLASHLIGHT_STEADY,
) {
    fun isDefault(): Boolean = name == DEFAULT_NAME

    fun smsContactEntries(): List<String> = parseContactEntries(smsContactNumbers)
    fun callContactEntries(): List<String> = parseContactEntries(callContactNumbers)
    fun safetySmsContactEntries(): List<String> = parseContactEntries(safetySmsContactNumbers)
    fun safetyCallContactEntries(): List<String> = parseContactEntries(safetyCallContactNumbers)

    fun smsContacts(): List<String> = smsContactEntries().filter { it.isNotEmpty() }
    fun callContacts(): List<String> = callContactEntries().filter { it.isNotEmpty() }
    fun safetySmsContacts(): List<String> = safetySmsContactEntries().filter { it.isNotEmpty() }
    fun safetyCallContacts(): List<String> = safetyCallContactEntries().filter { it.isNotEmpty() }

    fun withSmsContacts(list: List<String>) = copy(smsContactNumbers = joinContactEntries(list))
    fun withCallContacts(list: List<String>) = copy(callContactNumbers = joinContactEntries(list))
    fun withSafetySmsContacts(list: List<String>) =
        copy(safetySmsContactNumbers = joinContactEntries(list))
    fun withSafetyCallContacts(list: List<String>) =
        copy(safetyCallContactNumbers = joinContactEntries(list))

    fun scheduleLabel(): String {
        val base = when (repeatKind) {
            RepeatKind.SINGLE -> "Within ${formatDurationVerbose(intervalMs)}"
            RepeatKind.COUNT ->
                "Every ${formatDurationVerbose(intervalMs)}, $repeatCount times"
            RepeatKind.WINDOW ->
                "Every ${formatDurationVerbose(intervalMs)} for ${formatDurationVerbose(windowMs)}"
            RepeatKind.BY_TIME -> "By %02d:%02d".format(checkInHour, checkInMinute)
        }
        return base + graceSuffix(graceDurationMs)
    }

    private fun graceSuffix(graceMs: Long): String =
        if (graceMs > 0L) " (${formatDurationVerbose(graceMs)} grace)" else ""

    fun summaryLine(): String = scheduleLabel()

    companion object {
        const val DEFAULT_NAME = "Default"
        /** Loop alarm sound until Alarm/Safety is halted. */
        const val SOUND_UNTIL_DISMISSED = -1L
        const val FLASHLIGHT_STEADY = "steady"
        const val FLASHLIGHT_FLICKER = "flicker"
        const val FLASHLIGHT_SOS = "sos"

        fun normalizeFlashlightMode(raw: String): String = when (raw) {
            FLASHLIGHT_FLICKER, FLASHLIGHT_SOS -> raw
            else -> FLASHLIGHT_STEADY
        }

        private fun parseContactEntries(raw: String): List<String> {
            if (raw.isEmpty()) return emptyList()
            return raw.split(',', ';', '\n').map { it.trim() }
        }

        private fun joinContactEntries(list: List<String>): String = when {
            list.isEmpty() -> ""
            else -> list.joinToString(",") { it.ifEmpty { " " } }
        }

        fun formatSoundDuration(ms: Long): String =
            if (ms == SOUND_UNTIL_DISMISSED) "Until dismissed" else formatDuration(ms)

        fun formatFlickerPair(onMs: Long, offMs: Long): String =
            "${formatDuration(onMs.coerceAtLeast(0L))} on, ${formatDuration(offMs.coerceAtLeast(0L))} off"

        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return "0s"
            val totalSecExact = ms / 1000.0
            return when {
                ms % 3_600_000L == 0L -> "${ms / 3_600_000L}h"
                ms % 60_000L == 0L -> "${ms / 60_000L}m"
                ms % 1_000L == 0L && ms < 60_000L -> "${ms / 1_000L}s"
                ms >= 3_600_000L -> trimDecimal(totalSecExact / 3600.0) + "h"
                ms >= 60_000L -> trimDecimal(totalSecExact / 60.0) + "m"
                else -> trimDecimal(totalSecExact) + "s"
            }
        }

        fun formatDurationVerbose(ms: Long): String {
            if (ms <= 0L) return "0 sec"
            val totalSecExact = ms / 1000.0
            return when {
                ms % 3_600_000L == 0L -> pluralUnit(ms / 3_600_000L, "hr", "hrs")
                ms % 60_000L == 0L -> pluralUnit(ms / 60_000L, "min", "min")
                ms % 1_000L == 0L && ms < 60_000L -> pluralUnit(ms / 1_000L, "sec", "sec")
                ms >= 3_600_000L -> pluralUnitDecimal(totalSecExact / 3600.0, "hr", "hrs")
                ms >= 60_000L -> pluralUnitDecimal(totalSecExact / 60.0, "min", "min")
                else -> pluralUnitDecimal(totalSecExact, "sec", "sec")
            }
        }

        private fun pluralUnit(count: Long, singular: String, plural: String): String {
            val unit = if (count == 1L) singular else plural
            return "$count $unit"
        }

        private fun pluralUnitDecimal(value: Double, singular: String, plural: String): String {
            val asLong = value.toLong()
            val text = if (value == asLong.toDouble()) asLong.toString()
            else trimDecimal(value)
            val unit = if (value == 1.0) singular else plural
            return "$text $unit"
        }

        private fun trimDecimal(value: Double): String {
            val asLong = value.toLong()
            return if (value == asLong.toDouble()) asLong.toString()
            else value.toString().trimEnd('0').trimEnd('.')
        }

        fun parseDurationInput(raw: String): Long {
            val trimmed = raw.trim().lowercase().replace(',', '.')
            if (trimmed.isEmpty()) return 0L
            val (number, multiplier) = when {
                trimmed.endsWith('s') -> trimmed.dropLast(1).trim() to 1_000.0
                trimmed.endsWith('m') -> trimmed.dropLast(1).trim() to 60_000.0
                trimmed.endsWith('h') -> trimmed.dropLast(1).trim() to 3_600_000.0
                else -> trimmed to 60_000.0
            }
            val value = number.toDoubleOrNull() ?: return 0L
            return (value * multiplier).toLong().coerceAtLeast(0L)
        }

        fun formatDurationInput(ms: Long): String = formatDuration(ms)

        fun msUntilCheckIn(hour: Int, minute: Int, afterSuccess: Boolean = false): Long {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(java.util.Calendar.MINUTE, minute.coerceIn(0, 59))
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (afterSuccess || !target.after(now)) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
        }
    }
}

sealed class AppState {
    data object Wading : AppState()

    data class Overwatch(
        val config: OverwatchConfig,
        val submode: Submode,
        val deadlineElapsedMs: Long,
        val intervalMs: Long,
        val repeatsLeft: Int?,
        val windowEndElapsedMs: Long?,
        val remainingMs: Long,
        val pinBuffer: String = "",
        val errorFlag: Boolean = false,
        val errorMessage: String? = null,
        val startedByPanic: Boolean = false,
        val graceDeadlineElapsedMs: Long? = null,
        val enteredAlarmThisRun: Boolean = false,
        val secretAlarm: Boolean = false,
        /** Shade obfuscation for this run (Hide action / panic). */
        val notificationHidden: Boolean = false,
        /** Wall-clock when this run was enabled. */
        val enabledAtEpochMs: Long = 0L,
        /** Wall-clock when Alarm Mode started (0 if not yet). */
        val alarmAtEpochMs: Long = 0L,
        /** Human-readable trigger line, e.g. Panic, Crash Detect / No check-in. */
        val alarmTriggerLabel: String = "",
        /** Wall-clock of most recent successful check-in this run (0 if none). */
        val lastCheckInAtEpochMs: Long = 0L,
    ) : AppState()
}

enum class LeaveReason {
    LoopComplete,
    Cancel,
    HardQuit,
}

enum class RecordCameraMode {
    BACK,
    FRONT,
    BOTH,
}

data class AppSettings(
    val panicActivation: PanicActivation = PanicActivation.DOUBLE_TAP,
    val panicHardwareKey: HardwareKeyOption = HardwareKeyOption.NONE,
    val idlePanicConfigId: Long? = null,
    val lightTheme: Boolean = false,
    val batteryPrompted: Boolean = false,
    val pinHash: String = "",
    val duressDigit: String = "",
    val duressPrefix: Boolean = false,
    val preventCloseOnOverwatch: Boolean = false,
    val hardwareKeysOutsideApp: Boolean = false,
    val turnLocationOn: Boolean = false,
    val notifyToasts: Boolean = true,
    val videoClipSeconds: Int = 20,
    val audioClipSeconds: Int = 20,
    val recordCameraMode: RecordCameraMode = RecordCameraMode.FRONT,
    val darkCardArgb: Int = AppearanceDefaults.DARK_GROUP,
    val widgetArgb: Int = AppearanceDefaults.DARK_WIDGET,
    val actionArgb: Int = AppearanceDefaults.DARK_ACTION,
    val fontScale: Float = 1f,
    val recentLocationMinutes: Int = 1,
    val recentLocationPoints: Int = 3,
    val continuousLocationMinutes: Int = 1,
    val panicOnTwoWrongPins: Boolean = false,
    val failSecretly: Boolean = false,
    val failSecretPhrase: String = "Okay",
    val maxAlarmDurationMinutes: Int = 60,
    val maxAlarmSms: Int = 20,
    val maxAlarmCalls: Int = 10,
    val verboseLogging: Boolean = false,
    val alwaysLogEvents: Boolean = false,
)

object AppearanceDefaults {
    const val DARK_GROUP = 0xFF121212.toInt()
    const val DARK_WIDGET = 0xFFE8EAED.toInt()
    const val DARK_ACTION = 0xFF4A4458.toInt()
    const val LIGHT_GROUP = 0xFFE8E6DC.toInt()
    const val LIGHT_WIDGET = 0xFF5F6368.toInt()
    const val LIGHT_ACTION = 0xFFD0BCFF.toInt()

    fun group(light: Boolean): Int = if (light) LIGHT_GROUP else DARK_GROUP
    fun widget(light: Boolean): Int = if (light) LIGHT_WIDGET else DARK_WIDGET
    fun action(light: Boolean): Int = if (light) LIGHT_ACTION else DARK_ACTION

    fun withSchemeDefaults(light: Boolean): Triple<Int, Int, Int> =
        Triple(group(light), widget(light), action(light))
}

data class AlarmLogEntry(
    val id: Long = 0L,
    val atEpochMs: Long,
    val configName: String,
    val message: String,
)
