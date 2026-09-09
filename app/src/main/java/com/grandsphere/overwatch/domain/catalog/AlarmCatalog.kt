package com.grandsphere.overwatch.domain.catalog

import android.content.Context
import com.grandsphere.overwatch.domain.model.OverwatchConfig

/**
 * Add a future Alarm Mode action:
 * 1. Implement [AlarmEffect]
 * 2. Append to [AlarmCatalog.all]
 */
interface AlarmEffect {
    val id: String
    val label: String
    val conflictsWith: Set<String>
        get() = emptySet()

    fun run(context: Context, config: OverwatchConfig) {}
}

object AlarmCatalog {
    val all: List<AlarmEffect> = listOf(
        LogAlarm,
        SmsAlarm,
        CallAlarm,
        SirenAlarm,
        VibrateAlarm,
        ClockAlarmAction,
        LocationAlarm,
        FlashlightAlarm,
        RecordVideoAlarm,
        RecordAudioAlarm,
        NotificationAlarm,
        QuitAlarm,
    )

    /** Safety Mode: subset without quiet call, external alarm, record. */
    val safety: List<AlarmEffect> = listOf(
        NoneSafety,
        LogAlarm,
        SmsAlarm,
        CallAlarm,
        SirenAlarm,
        VibrateAlarm,
        LocationAlarm,
        FlashlightAlarm,
        NotificationAlarm,
        QuitAlarm,
    )

    private val safetyIds: Set<String> = safety.map { it.id }.toSet()

    fun isAllowedInSafety(id: String): Boolean = id in safetyIds

    fun byId(id: String): AlarmEffect? = all.find { it.id == id } ?: safety.find { it.id == id }

    fun labelOf(id: String): String = byId(id)?.label ?: "unknown ($id)"

    /** IDs shown as editor/preview chips; quiet_call is a Call-dialog toggle only. */
    fun chipIds(ids: List<String>): List<String> = ids.filter { it != "quiet_call" }

    fun menuOptions(list: List<AlarmEffect>): List<Pair<String, String>> =
        EffectMenus.sorted(list.map { it.id to it.label })

    fun conflicts(selected: Collection<String>, covert: Boolean): Set<String> {
        val set = selected.toSet()
        val bad = mutableSetOf<String>()
        for (id in set) {
            val effect = byId(id) ?: continue
            if (effect.conflictsWith.any { it in set }) bad += id
        }
        if (covert && "siren" in set) bad += "siren"
        if (covert && "clock_alarm" in set) bad += "clock_alarm"
        if (covert && "flashlight" in set) bad += "flashlight"
        if (covert && "vibrate" in set) bad += "vibrate"
        if ("none" in set && set.size > 1) bad += set
        if ("location" in set && "sms" !in set && "log" !in set) bad += "location"
        return bad
    }

    /** Drop removed ids; promote flicker/sos to flashlight. */
    fun sanitizeAlarmIds(ids: List<String>): List<String> {
        val next = ids.toMutableList()
        next.removeAll { it == "go_silent" || it == "continuous_location" }
        val hadFlicker = next.removeAll { it == "flicker_flashlight" }
        val hadSos = next.removeAll { it == "sos_flashlight" }
        if ((hadFlicker || hadSos) && "flashlight" !in next) next += "flashlight"
        return next.distinct()
    }

    fun sanitizeSafetyIds(ids: List<String>): List<String> {
        val next = sanitizeAlarmIds(ids).filter { isAllowedInSafety(it) }.toMutableList()
        return next.ifEmpty { listOf("none") }
    }
}

object EffectMenus {
    fun sorted(options: List<Pair<String, String>>): List<Pair<String, String>> {
        val pinnedFirst = setOf("none", CancelCatalog.SAME_AS_DISMISS)
        val pinned = options.filter { it.first in pinnedFirst }
        val quit = options.filter { it.first == "quit" }
        val rest = options
            .filter { it.first !in pinnedFirst && it.first != "quit" }
            .sortedBy { it.second.lowercase() }
        return pinned + rest + quit
    }
}

object NoneSafety : AlarmEffect {
    override val id = "none"
    override val label = "None"
    override val conflictsWith = setOf(
        "log", "sms", "call", "siren", "vibrate", "location", "flashlight",
        "notification", "quit",
    )
}

object LogAlarm : AlarmEffect {
    override val id = "log"
    override val label = "Log"
}

object SmsAlarm : AlarmEffect {
    override val id = "sms"
    override val label = "SMS"
}

object CallAlarm : AlarmEffect {
    override val id = "call"
    override val label = "Call"
}

object QuietCallAlarm : AlarmEffect {
    override val id = "quiet_call"
    override val label = "Quiet call (best effort)"
}

object SirenAlarm : AlarmEffect {
    override val id = "siren"
    override val label = "Sound"
}

object VibrateAlarm : AlarmEffect {
    override val id = "vibrate"
    override val label = "Vibrate"
}

object ClockAlarmAction : AlarmEffect {
    override val id = "clock_alarm"
    override val label = "Set Alarm"
}

object LocationAlarm : AlarmEffect {
    override val id = "location"
    override val label = "Location"
}

object FlashlightAlarm : AlarmEffect {
    override val id = "flashlight"
    override val label = "Flashlight"
    override val conflictsWith = setOf("record_video")
}

object RecordVideoAlarm : AlarmEffect {
    override val id = "record_video"
    override val label = "Record video"
    override val conflictsWith = setOf("flashlight")
}

object RecordAudioAlarm : AlarmEffect {
    override val id = "record_audio"
    override val label = "Record audio"
}

object NotificationAlarm : AlarmEffect {
    override val id = "notification"
    override val label = "Notification"
}

object QuitAlarm : AlarmEffect {
    override val id = "quit"
    override val label = "Quit"
}
