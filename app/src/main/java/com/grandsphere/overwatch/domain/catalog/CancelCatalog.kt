package com.grandsphere.overwatch.domain.catalog

/**
 * Cancel Mode methods (multi-select like Dismiss).
 * [same_as_dismiss] expands to the config's dismissEffectIds at runtime.
 */
interface CancelEffect {
    val id: String
    val label: String
    val conflictsWith: Set<String>
        get() = emptySet()
}

object CancelCatalog {
    const val SAME_AS_DISMISS = "same_as_dismiss"

    val all: List<CancelEffect> = listOf(
        PinCancel,
        FingerprintCancel,
        AutoFingerprintCancel,
        VolumeDownCancel,
        PowerButtonCancel,
        TurnoverCancel,
        ShakeCancel,
        SameAsDismissCancel,
    )

    fun byId(id: String): CancelEffect? = all.find { it.id == id }

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

    fun collapseFingerprint(ids: List<String>): List<String> =
        DismissCatalog.collapseFingerprint(ids)

    fun addFingerprint(ids: List<String>, auto: Boolean = true): List<String> =
        DismissCatalog.addFingerprint(ids, auto)

    fun removeFingerprint(ids: List<String>): List<String> =
        DismissCatalog.removeFingerprint(ids)

    fun setFingerprintAuto(ids: List<String>, auto: Boolean): List<String> =
        DismissCatalog.setFingerprintAuto(ids, auto)

    fun fingerprintAutoLabel(ids: Collection<String>): String =
        DismissCatalog.fingerprintAutoLabel(ids)

    fun conflicts(selected: Collection<String>): Set<String> {
        val set = selected.toSet()
        val bad = mutableSetOf<String>()
        for (id in set) {
            val effect = byId(id) ?: continue
            if (effect.conflictsWith.any { it in set }) bad += id
        }
        return bad
    }

    /** Resolved proof ids (dismiss catalog ids), expanding [SAME_AS_DISMISS]. */
    fun resolvedProofIds(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): List<String> {
        if (SAME_AS_DISMISS in cancelEffectIds) return dismissEffectIds.toList()
        return cancelEffectIds.filter { it != SAME_AS_DISMISS && byId(it) != null }
    }

    fun usesPin(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        "pin" in resolvedProofIds(cancelEffectIds, dismissEffectIds)

    fun usesTap(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        "tap" in resolvedProofIds(cancelEffectIds, dismissEffectIds)

    fun usesFingerprint(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        DismissCatalog.usesFingerprint(resolvedProofIds(cancelEffectIds, dismissEffectIds))

    fun usesAutoFingerprint(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        DismissCatalog.usesAutoFingerprint(resolvedProofIds(cancelEffectIds, dismissEffectIds))

    fun usesVolumeDown(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        "volume_down" in resolvedProofIds(cancelEffectIds, dismissEffectIds)

    fun usesPowerButton(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        "power_button" in resolvedProofIds(cancelEffectIds, dismissEffectIds)

    fun usesTurnover(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        "turnover" in resolvedProofIds(cancelEffectIds, dismissEffectIds)

    fun usesShake(cancelEffectIds: Collection<String>, dismissEffectIds: Collection<String>): Boolean =
        "shake" in resolvedProofIds(cancelEffectIds, dismissEffectIds)

    fun fromLegacyCancelMethod(method: String): List<String> = when (method) {
        "PIN" -> listOf("pin")
        "FINGERPRINT" -> listOf("fingerprint")
        "DISMISS_METHODS", "CONFIRM" -> listOf(SAME_AS_DISMISS)
        else -> listOf(SAME_AS_DISMISS)
    }
}

object PinCancel : CancelEffect {
    override val id = "pin"
    override val label = "PIN"
    override val conflictsWith = setOf(CancelCatalog.SAME_AS_DISMISS)
}

object FingerprintCancel : CancelEffect {
    override val id = "fingerprint"
    override val label = "Fingerprint"
    override val conflictsWith = setOf(CancelCatalog.SAME_AS_DISMISS)
}

object AutoFingerprintCancel : CancelEffect {
    override val id = "auto_fingerprint"
    override val label = "Auto fingerprint"
    override val conflictsWith = setOf(CancelCatalog.SAME_AS_DISMISS)
}

object VolumeDownCancel : CancelEffect {
    override val id = "volume_down"
    override val label = "Volume down (in-app)"
    override val conflictsWith = setOf(CancelCatalog.SAME_AS_DISMISS)
}

object PowerButtonCancel : CancelEffect {
    override val id = "power_button"
    override val label = "Power Button"
    override val conflictsWith = setOf(CancelCatalog.SAME_AS_DISMISS)
}

object TurnoverCancel : CancelEffect {
    override val id = "turnover"
    override val label = "Turnover"
    override val conflictsWith = setOf(CancelCatalog.SAME_AS_DISMISS)
}

object ShakeCancel : CancelEffect {
    override val id = "shake"
    override val label = "Shake"
    override val conflictsWith = setOf(CancelCatalog.SAME_AS_DISMISS)
}

object SameAsDismissCancel : CancelEffect {
    override val id = CancelCatalog.SAME_AS_DISMISS
    override val label = "Same as dismiss"
    override val conflictsWith = setOf(
        "pin",
        "fingerprint",
        "auto_fingerprint",
        "volume_down",
        "power_button",
        "turnover",
        "shake",
    )
}
