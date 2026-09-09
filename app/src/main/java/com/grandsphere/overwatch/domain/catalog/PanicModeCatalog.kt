package com.grandsphere.overwatch.domain.catalog

/**
 * Per-Overwatch Panic Mode options (editor). Settings Panic hardware keys are separate.
 * Room stores string ids.
 */
interface PanicModeEffect {
    val id: String
    val label: String
    val conflictsWith: Set<String>
        get() = emptySet()
}

object PanicModeCatalog {
    const val NONE = "none"
    const val POWER_BUTTON = "power_button"
    const val TURNOVER = "turnover"
    const val SHAKE = "shake"
    const val CRASH_DETECT = "crash_detect"

    val all: List<PanicModeEffect> = listOf(
        NonePanic,
        PowerButtonPanic,
        TurnoverPanic,
        ShakePanic,
        CrashDetectPanic,
    )

    fun byId(id: String): PanicModeEffect? = all.find { it.id == id }

    fun labelOf(id: String): String = byId(id)?.label ?: "unknown ($id)"

    fun menuOptions(): List<Pair<String, String>> =
        EffectMenus.sorted(all.map { it.id to it.label })

    fun sanitize(ids: Collection<String>): List<String> {
        val known = ids.filter { byId(it) != null }.distinct()
        if (known.isEmpty()) return listOf(NONE)
        return if (NONE in known && known.size > 1) known.filter { it != NONE } else known
    }

    fun conflicts(selected: Collection<String>): Set<String> {
        val set = selected.toSet()
        val bad = mutableSetOf<String>()
        for (id in set) {
            val effect = byId(id) ?: continue
            if (effect.conflictsWith.any { it in set }) bad += id
        }
        return bad
    }

    fun usesPower(ids: Collection<String>): Boolean = POWER_BUTTON in ids
    fun usesTurnover(ids: Collection<String>): Boolean = TURNOVER in ids
    fun usesShake(ids: Collection<String>): Boolean = SHAKE in ids
    fun usesCrash(ids: Collection<String>): Boolean = CRASH_DETECT in ids
}

object NonePanic : PanicModeEffect {
    override val id = PanicModeCatalog.NONE
    override val label = "None"
    override val conflictsWith = setOf(
        PanicModeCatalog.POWER_BUTTON,
        PanicModeCatalog.TURNOVER,
        PanicModeCatalog.SHAKE,
        PanicModeCatalog.CRASH_DETECT,
    )
}

object PowerButtonPanic : PanicModeEffect {
    override val id = PanicModeCatalog.POWER_BUTTON
    override val label = "Power Button"
    override val conflictsWith = setOf(PanicModeCatalog.NONE)
}

object TurnoverPanic : PanicModeEffect {
    override val id = PanicModeCatalog.TURNOVER
    override val label = "Turnover"
    override val conflictsWith = setOf(PanicModeCatalog.NONE)
}

object ShakePanic : PanicModeEffect {
    override val id = PanicModeCatalog.SHAKE
    override val label = "Shake"
    override val conflictsWith = setOf(PanicModeCatalog.NONE)
}

object CrashDetectPanic : PanicModeEffect {
    override val id = PanicModeCatalog.CRASH_DETECT
    override val label = "Crash Detect"
    override val conflictsWith = setOf(PanicModeCatalog.NONE)
}
