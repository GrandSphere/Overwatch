package com.grandsphere.overwatch.domain.catalog

import android.content.Context
import com.grandsphere.overwatch.domain.model.OverwatchConfig

/**
 * Add a future Notify Mode cue:
 * 1. Create a file implementing [NotifyEffect]
 * 2. Append it to [NotifyCatalog.all]
 * 3. Declare extra permissions next to that object if needed
 *
 * Room stores [id], never an enum.
 */
interface NotifyEffect {
    val id: String
    val label: String
    val conflictsWith: Set<String>
        get() = emptySet()

    fun run(context: Context, covert: Boolean) {}
}

object NotifyCatalog {
    val all: List<NotifyEffect> = listOf(
        NoneNotify,
        NotificationNotify,
        SoundNotify,
        LiveNotify,
        ClockAlarmNotify,
        VibrateNotify,
        FlashlightNotify,
    )

    fun byId(id: String): NotifyEffect? = all.find { it.id == id }

    fun labelOf(id: String): String = byId(id)?.label ?: "unknown ($id)"

    fun menuOptions(): List<Pair<String, String>> =
        EffectMenus.sorted(all.map { it.id to it.label })

    fun conflicts(selected: Collection<String>, covert: Boolean = false): Set<String> {
        val set = selected.toSet()
        val bad = mutableSetOf<String>()
        for (id in set) {
            val effect = byId(id) ?: continue
            if (effect.conflictsWith.any { it in set }) bad += id
        }
        if ("none" in set && set.size > 1) bad += set
        if (covert && "clock_alarm" in set) bad += "clock_alarm"
        return bad
    }

    fun sanitizeIds(ids: List<String>): List<String> {
        val next = ids.filter {
            it != "popup" && it != "flicker_flashlight" && it != "sos_flashlight"
        }.toMutableList()
        if (ids.any { it == "flicker_flashlight" || it == "sos_flashlight" } && "flashlight" !in next) {
            next += "flashlight"
        }
        return next.filter { byId(it) != null }.distinct().ifEmpty { listOf("none") }
    }

    fun flashlightModeFromLegacy(ids: List<String>, stored: String): String {
        if ("sos_flashlight" in ids) return OverwatchConfig.FLASHLIGHT_SOS
        if ("flicker_flashlight" in ids) return OverwatchConfig.FLASHLIGHT_FLICKER
        return OverwatchConfig.normalizeFlashlightMode(stored)
    }
}

object NoneNotify : NotifyEffect {
    override val id = "none"
    override val label = "None"
    override val conflictsWith = setOf(
        "notification", "sound", "vibrate", "flashlight", "clock_alarm", "live_notify",
    )
}

object NotificationNotify : NotifyEffect {
    override val id = "notification"
    override val label = "Notification"
}

object SoundNotify : NotifyEffect {
    override val id = "sound"
    override val label = "Sound"
}

object LiveNotify : NotifyEffect {
    override val id = "live_notify"
    override val label = "Live notify"
}

object ClockAlarmNotify : NotifyEffect {
    override val id = "clock_alarm"
    override val label = "Set Alarm"
    override val conflictsWith = setOf("none")
}

object VibrateNotify : NotifyEffect {
    override val id = "vibrate"
    override val label = "Vibrate"
}

object FlashlightNotify : NotifyEffect {
    override val id = "flashlight"
    override val label = "Flashlight"
}
