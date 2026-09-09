package com.grandsphere.overwatch.domain.catalog

/**
 * Add a future Dismiss Mode method:
 * 1. Implement [DismissEffect]
 * 2. Append to [DismissCatalog.all]
 */
interface DismissEffect {
    val id: String
    val label: String
    val conflictsWith: Set<String>
        get() = emptySet()
}

object DismissCatalog {
    val all: List<DismissEffect> = listOf(
        TapDismiss,
        PinDismiss,
        FingerprintDismiss,
        AutoFingerprintDismiss,
        VolumeDownDismiss,
        PowerButtonDismiss,
        TurnoverDismiss,
        ShakeDismiss,
    )

    fun byId(id: String): DismissEffect? = all.find { it.id == id }

    fun labelOf(id: String): String = when (id) {
        "fingerprint", "auto_fingerprint" -> "Fingerprint"
        else -> byId(id)?.label ?: "unknown ($id)"
    }

    fun menuOptions(): List<Pair<String, String>> =
        EffectMenus.sorted(
            all.filter { it.id != "auto_fingerprint" }.map { it.id to it.label },
        )

    fun chipIds(ids: List<String>): List<String> =
        ids.map { if (it == "auto_fingerprint") "fingerprint" else it }.distinct()

    fun usesFingerprint(ids: Collection<String>): Boolean =
        ids.any { it == "fingerprint" || it == "auto_fingerprint" }

    fun usesAutoFingerprint(ids: Collection<String>): Boolean = "auto_fingerprint" in ids

    fun usesPowerButton(ids: Collection<String>): Boolean = "power_button" in ids
    fun usesTurnover(ids: Collection<String>): Boolean = "turnover" in ids
    fun usesShake(ids: Collection<String>): Boolean = "shake" in ids

    /** Prefer auto when both present. */
    fun collapseFingerprint(ids: List<String>): List<String> {
        if (!usesFingerprint(ids)) return ids
        val rest = ids.filter { it != "fingerprint" && it != "auto_fingerprint" }
        return rest + if (usesAutoFingerprint(ids)) listOf("auto_fingerprint") else listOf("fingerprint")
    }

    fun addFingerprint(ids: List<String>, auto: Boolean = true): List<String> {
        val rest = ids.filter { it != "fingerprint" && it != "auto_fingerprint" }
        return rest + if (auto) listOf("auto_fingerprint") else listOf("fingerprint")
    }

    fun removeFingerprint(ids: List<String>): List<String> =
        ids.filter { it != "fingerprint" && it != "auto_fingerprint" }

    fun setFingerprintAuto(ids: List<String>, auto: Boolean): List<String> =
        addFingerprint(ids, auto = auto)

    fun fingerprintAutoLabel(ids: Collection<String>): String =
        if (usesAutoFingerprint(ids)) "Fingerprint: Auto" else "Fingerprint: Tap"

    fun conflicts(selected: Collection<String>): Set<String> {
        val set = selected.toSet()
        val bad = mutableSetOf<String>()
        for (id in set) {
            val effect = byId(id) ?: continue
            if (effect.conflictsWith.any { it in set }) bad += id
        }
        return bad
    }
}

object TapDismiss : DismissEffect {
    override val id = "tap"
    override val label = "Tap"
}

object PinDismiss : DismissEffect {
    override val id = "pin"
    override val label = "PIN"
}

object FingerprintDismiss : DismissEffect {
    override val id = "fingerprint"
    override val label = "Fingerprint"
}

object AutoFingerprintDismiss : DismissEffect {
    override val id = "auto_fingerprint"
    override val label = "Auto fingerprint"
}

object VolumeDownDismiss : DismissEffect {
    override val id = "volume_down"
    override val label = "Volume down (in-app)"
}

object PowerButtonDismiss : DismissEffect {
    override val id = "power_button"
    override val label = "Power Button"
}

object TurnoverDismiss : DismissEffect {
    override val id = "turnover"
    override val label = "Turnover"
}

object ShakeDismiss : DismissEffect {
    override val id = "shake"
    override val label = "Shake"
}
