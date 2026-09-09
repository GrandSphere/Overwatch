package com.grandsphere.overwatch.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.grandsphere.overwatch.domain.catalog.AlarmCatalog
import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.DismissCatalog
import com.grandsphere.overwatch.domain.catalog.NotifyCatalog
import com.grandsphere.overwatch.domain.catalog.PanicModeCatalog
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.NotificationUrgency
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.RepeatKind
import com.grandsphere.overwatch.domain.model.ShakeStrength

@Entity(tableName = "overwatch_configs")
data class OverwatchConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val intervalMs: Long,
    val repeatKind: String,
    val repeatCount: Int,
    val windowMs: Long,
    val checkInHour: Int = 15,
    val checkInMinute: Int = 0,
    val covert: Boolean,
    /** Legacy column kept for import; prefer [cancelEffectIds]. */
    val cancelMethod: String = "CONFIRM",
    val cancelEffectIds: String = "",
    val pinHash: String?,
    val graceDurationMs: Long,
    val notifyEffectIds: String,
    val dismissEffectIds: String,
    val graceNotifyEffectIds: String,
    val graceDismissEffectIds: String,
    val alarmEffectIds: String,
    val contactNumbers: String,
    val smsContactNumbers: String = "",
    val callContactNumbers: String = "",
    val alarmSmsBody: String = "",
    val notifySoundUri: String = "",
    val notifySoundDurationMs: Long = OverwatchConfig.SOUND_UNTIL_DISMISSED,
    val alarmSoundUri: String = "",
    val alarmSoundDurationMs: Long = OverwatchConfig.SOUND_UNTIL_DISMISSED,
    val dismissHardwareKey: String,
    val locationRecent: Boolean = false,
    val locationContinuous: Boolean = false,
    val safetyEffectIds: String = "none",
    val safetySmsBody: String = "",
    val safetySmsContactNumbers: String = "",
    val safetyCallContactNumbers: String = "",
    val safetyLocationRecent: Boolean = false,
    val safetyLocationContinuous: Boolean = false,
    val safetyOnCancel: Boolean = false,
    val safetySoundUri: String = "",
    val safetySoundDurationMs: Long = 30_000L,
    val notifyFlickerOnMs: Long = 2_000L,
    val notifyFlickerOffMs: Long = 2_000L,
    val alarmFlickerOnMs: Long = 2_000L,
    val alarmFlickerOffMs: Long = 2_000L,
    val safetyFlickerOnMs: Long = 2_000L,
    val safetyFlickerOffMs: Long = 2_000L,
    val panicEffectIds: String = "none",
    val panicPowerTaps: Int = 3,
    val dismissPowerTaps: Int = 3,
    val cancelPowerTaps: Int = 3,
    val shakeStrength: String = "MEDIUM",
    val dismissShakeStrength: String = "MEDIUM",
    val cancelShakeStrength: String = "MEDIUM",
    val shakeCount: Int = 3,
    val dismissShakeCount: Int = 3,
    val cancelShakeCount: Int = 3,
    val crashThresholdG: Float = 8f,
    val crashStillnessMs: Long = 50_000L,
    val persistentPanicNotification: Boolean = false,
    val liveNotifyShowName: Boolean = false,
    val alarmNotificationBody: String = "",
    val safetyNotificationBody: String = "",
    val notifyNotificationBody: String = "",
    val notifyNotificationUrgency: String = NotificationUrgency.DEFAULT.name,
    val alarmNotificationUrgency: String = NotificationUrgency.DEFAULT.name,
    val safetyNotificationUrgency: String = NotificationUrgency.DEFAULT.name,
    val turnoverHoldMs: Long = 700L,
    val dismissTurnoverHoldMs: Long = 700L,
    val cancelTurnoverHoldMs: Long = 700L,
    val sendTriggerMode: Boolean = true,
    val notifyVibrateDurationMs: Long = 10_000L,
    val alarmVibrateDurationMs: Long = OverwatchConfig.SOUND_UNTIL_DISMISSED,
    val safetyVibrateDurationMs: Long = 10_000L,
    val notifyFlashlightDurationMs: Long = 5_000L,
    val alarmFlashlightDurationMs: Long = OverwatchConfig.SOUND_UNTIL_DISMISSED,
    val safetyFlashlightDurationMs: Long = 5_000L,
    val notifyFlashlightMode: String = OverwatchConfig.FLASHLIGHT_STEADY,
    val alarmFlashlightMode: String = OverwatchConfig.FLASHLIGHT_STEADY,
    val safetyFlashlightMode: String = OverwatchConfig.FLASHLIGHT_STEADY,
) {
    fun toDomain(): OverwatchConfig {
        val rawAlarm = csv(alarmEffectIds)
        val rawNotify = csv(notifyEffectIds)
        val rawSafety = csv(safetyEffectIds)
        val alarmIds = AlarmCatalog.sanitizeAlarmIds(rawAlarm).toMutableList()
        var continuous = locationContinuous
        if ("continuous_location" in rawAlarm) {
            continuous = true
            if ("location" !in alarmIds) alarmIds += "location"
        }
        val safetyIds = AlarmCatalog.sanitizeSafetyIds(rawSafety).toMutableList()
        val cancelIds = csv(cancelEffectIds).ifEmpty {
            CancelCatalog.fromLegacyCancelMethod(cancelMethod)
        }.let { ids ->
            when {
                ids.isEmpty() -> listOf(CancelCatalog.SAME_AS_DISMISS)
                CancelCatalog.SAME_AS_DISMISS in ids && ids.size > 1 ->
                    ids.filter { it != CancelCatalog.SAME_AS_DISMISS }
                else -> ids
            }
        }
        val notifyIds = NotifyCatalog.sanitizeIds(rawNotify)
        return OverwatchConfig(
            id = id,
            name = name,
            intervalMs = intervalMs,
            repeatKind = RepeatKind.valueOf(repeatKind),
            repeatCount = repeatCount,
            windowMs = windowMs,
            checkInHour = checkInHour,
            checkInMinute = checkInMinute,
            covert = covert,
            cancelEffectIds = DismissCatalog.collapseFingerprint(cancelIds),
            pinHash = pinHash,
            graceDurationMs = graceDurationMs,
            notifyEffectIds = notifyIds,
            dismissEffectIds = DismissCatalog.collapseFingerprint(csv(dismissEffectIds)),
            graceNotifyEffectIds = NotifyCatalog.sanitizeIds(csv(graceNotifyEffectIds)),
            graceDismissEffectIds = csv(graceDismissEffectIds),
            alarmEffectIds = alarmIds,
            contactNumbers = contactNumbers,
            smsContactNumbers = smsContactNumbers.ifBlank { contactNumbers },
            callContactNumbers = callContactNumbers.ifBlank { contactNumbers },
            alarmSmsBody = alarmSmsBody,
            notifySoundUri = notifySoundUri,
            notifySoundDurationMs = notifySoundDurationMs,
            alarmSoundUri = alarmSoundUri,
            alarmSoundDurationMs = alarmSoundDurationMs,
            dismissHardwareKey = HardwareKeyOption.valueOf(dismissHardwareKey),
            locationRecent = locationRecent,
            locationContinuous = continuous,
            safetyEffectIds = safetyIds,
            safetySmsBody = safetySmsBody,
            safetySmsContactNumbers = safetySmsContactNumbers,
            safetyCallContactNumbers = safetyCallContactNumbers,
            safetyLocationRecent = false,
            safetyLocationContinuous = false,
            safetyOnCancel = safetyOnCancel,
            safetySoundUri = safetySoundUri,
            safetySoundDurationMs = safetySoundDurationMs.coerceAtLeast(1_000L),
            notifyFlickerOnMs = notifyFlickerOnMs.coerceAtLeast(0L),
            notifyFlickerOffMs = notifyFlickerOffMs.coerceAtLeast(0L),
            alarmFlickerOnMs = alarmFlickerOnMs.coerceAtLeast(0L),
            alarmFlickerOffMs = alarmFlickerOffMs.coerceAtLeast(0L),
            safetyFlickerOnMs = safetyFlickerOnMs.coerceAtLeast(0L),
            safetyFlickerOffMs = safetyFlickerOffMs.coerceAtLeast(0L),
            panicEffectIds = PanicModeCatalog.sanitize(csv(panicEffectIds)),
            panicPowerTaps = panicPowerTaps.coerceIn(2, 10),
            dismissPowerTaps = dismissPowerTaps.coerceIn(2, 10),
            cancelPowerTaps = cancelPowerTaps.coerceIn(2, 10),
            shakeStrength = runCatching { ShakeStrength.valueOf(shakeStrength) }.getOrDefault(ShakeStrength.MEDIUM),
            dismissShakeStrength = runCatching { ShakeStrength.valueOf(dismissShakeStrength) }
                .getOrDefault(ShakeStrength.MEDIUM),
            cancelShakeStrength = runCatching { ShakeStrength.valueOf(cancelShakeStrength) }
                .getOrDefault(ShakeStrength.MEDIUM),
            shakeCount = shakeCount.coerceIn(1, 10),
            dismissShakeCount = dismissShakeCount.coerceIn(1, 10),
            cancelShakeCount = cancelShakeCount.coerceIn(1, 10),
            crashThresholdG = crashThresholdG.coerceIn(1f, 16f),
            crashStillnessMs = crashStillnessMs.coerceAtLeast(1_000L),
            persistentPanicNotification = persistentPanicNotification,
            liveNotifyShowName = liveNotifyShowName ||
                (persistentPanicNotification && "live_notify" in notifyIds),
            alarmNotificationBody = alarmNotificationBody,
            safetyNotificationBody = safetyNotificationBody,
            notifyNotificationBody = notifyNotificationBody,
            notifyNotificationUrgency = parseUrgency(notifyNotificationUrgency),
            alarmNotificationUrgency = parseUrgency(alarmNotificationUrgency),
            safetyNotificationUrgency = parseUrgency(safetyNotificationUrgency),
            turnoverHoldMs = turnoverHoldMs.coerceAtLeast(100L),
            dismissTurnoverHoldMs = dismissTurnoverHoldMs.coerceAtLeast(100L),
            cancelTurnoverHoldMs = cancelTurnoverHoldMs.coerceAtLeast(100L),
            sendTriggerMode = sendTriggerMode,
            notifyVibrateDurationMs = notifyVibrateDurationMs,
            alarmVibrateDurationMs = alarmVibrateDurationMs,
            safetyVibrateDurationMs = safetyVibrateDurationMs.coerceAtLeast(1_000L),
            notifyFlashlightDurationMs = notifyFlashlightDurationMs,
            alarmFlashlightDurationMs = alarmFlashlightDurationMs,
            safetyFlashlightDurationMs = safetyFlashlightDurationMs.coerceAtLeast(1_000L),
            notifyFlashlightMode = NotifyCatalog.flashlightModeFromLegacy(rawNotify, notifyFlashlightMode),
            alarmFlashlightMode = NotifyCatalog.flashlightModeFromLegacy(rawAlarm, alarmFlashlightMode),
            safetyFlashlightMode = NotifyCatalog.flashlightModeFromLegacy(rawSafety, safetyFlashlightMode),
        )
    }

    companion object {
        fun from(config: OverwatchConfig): OverwatchConfigEntity {
            val alarmIds = AlarmCatalog.sanitizeAlarmIds(config.alarmEffectIds)
            var continuous = config.locationContinuous
            if ("continuous_location" in config.alarmEffectIds) {
                continuous = true
            }
            val safetyIds = AlarmCatalog.sanitizeSafetyIds(config.safetyEffectIds)
            val notifyIds = NotifyCatalog.sanitizeIds(config.notifyEffectIds)
            return OverwatchConfigEntity(
                id = config.id,
                name = config.name,
                intervalMs = config.intervalMs,
                repeatKind = config.repeatKind.name,
                repeatCount = config.repeatCount,
                windowMs = config.windowMs,
                checkInHour = config.checkInHour,
                checkInMinute = config.checkInMinute,
                covert = config.covert,
                cancelMethod = "CONFIRM",
                cancelEffectIds = config.cancelEffectIds.joinToString(","),
                pinHash = config.pinHash,
                graceDurationMs = config.graceDurationMs,
                notifyEffectIds = notifyIds.joinToString(","),
                dismissEffectIds = config.dismissEffectIds.joinToString(","),
                graceNotifyEffectIds = NotifyCatalog.sanitizeIds(config.graceNotifyEffectIds).joinToString(","),
                graceDismissEffectIds = config.graceDismissEffectIds.joinToString(","),
                alarmEffectIds = alarmIds.joinToString(","),
                contactNumbers = config.contactNumbers,
                smsContactNumbers = config.smsContactNumbers,
                callContactNumbers = config.callContactNumbers,
                alarmSmsBody = config.alarmSmsBody,
                notifySoundUri = config.notifySoundUri,
                notifySoundDurationMs = config.notifySoundDurationMs,
                alarmSoundUri = config.alarmSoundUri,
                alarmSoundDurationMs = config.alarmSoundDurationMs,
                dismissHardwareKey = config.dismissHardwareKey.name,
                locationRecent = config.locationRecent,
                locationContinuous = continuous,
                safetyEffectIds = safetyIds.joinToString(","),
                safetySmsBody = config.safetySmsBody,
                safetySmsContactNumbers = config.safetySmsContactNumbers,
                safetyCallContactNumbers = config.safetyCallContactNumbers,
                safetyLocationRecent = false,
                safetyLocationContinuous = false,
                safetyOnCancel = config.safetyOnCancel,
                safetySoundUri = config.safetySoundUri,
                safetySoundDurationMs = config.safetySoundDurationMs.coerceAtLeast(1_000L),
                notifyFlickerOnMs = config.notifyFlickerOnMs.coerceAtLeast(0L),
                notifyFlickerOffMs = config.notifyFlickerOffMs.coerceAtLeast(0L),
                alarmFlickerOnMs = config.alarmFlickerOnMs.coerceAtLeast(0L),
                alarmFlickerOffMs = config.alarmFlickerOffMs.coerceAtLeast(0L),
                safetyFlickerOnMs = config.safetyFlickerOnMs.coerceAtLeast(0L),
                safetyFlickerOffMs = config.safetyFlickerOffMs.coerceAtLeast(0L),
                panicEffectIds = PanicModeCatalog.sanitize(config.panicEffectIds).joinToString(","),
                panicPowerTaps = config.panicPowerTaps.coerceIn(2, 10),
                dismissPowerTaps = config.dismissPowerTaps.coerceIn(2, 10),
                cancelPowerTaps = config.cancelPowerTaps.coerceIn(2, 10),
                shakeStrength = config.shakeStrength.name,
                dismissShakeStrength = config.dismissShakeStrength.name,
                cancelShakeStrength = config.cancelShakeStrength.name,
                shakeCount = config.shakeCount.coerceIn(1, 10),
                dismissShakeCount = config.dismissShakeCount.coerceIn(1, 10),
                cancelShakeCount = config.cancelShakeCount.coerceIn(1, 10),
                crashThresholdG = config.crashThresholdG.coerceIn(1f, 16f),
                crashStillnessMs = config.crashStillnessMs.coerceAtLeast(1_000L),
                persistentPanicNotification = config.persistentPanicNotification,
                liveNotifyShowName = config.liveNotifyShowName,
                alarmNotificationBody = config.alarmNotificationBody,
                safetyNotificationBody = config.safetyNotificationBody,
                notifyNotificationBody = config.notifyNotificationBody,
                notifyNotificationUrgency = config.notifyNotificationUrgency.name,
                alarmNotificationUrgency = config.alarmNotificationUrgency.name,
                safetyNotificationUrgency = config.safetyNotificationUrgency.name,
                turnoverHoldMs = config.turnoverHoldMs.coerceAtLeast(100L),
                dismissTurnoverHoldMs = config.dismissTurnoverHoldMs.coerceAtLeast(100L),
                cancelTurnoverHoldMs = config.cancelTurnoverHoldMs.coerceAtLeast(100L),
                sendTriggerMode = config.sendTriggerMode,
                notifyVibrateDurationMs = config.notifyVibrateDurationMs,
                alarmVibrateDurationMs = config.alarmVibrateDurationMs,
                safetyVibrateDurationMs = config.safetyVibrateDurationMs.coerceAtLeast(1_000L),
                notifyFlashlightDurationMs = config.notifyFlashlightDurationMs,
                alarmFlashlightDurationMs = config.alarmFlashlightDurationMs,
                safetyFlashlightDurationMs = config.safetyFlashlightDurationMs.coerceAtLeast(1_000L),
                notifyFlashlightMode = OverwatchConfig.normalizeFlashlightMode(config.notifyFlashlightMode),
                alarmFlashlightMode = OverwatchConfig.normalizeFlashlightMode(config.alarmFlashlightMode),
                safetyFlashlightMode = OverwatchConfig.normalizeFlashlightMode(config.safetyFlashlightMode),
            )
        }

        private fun csv(raw: String): List<String> =
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        private fun parseUrgency(raw: String): NotificationUrgency =
            runCatching { NotificationUrgency.valueOf(raw) }.getOrDefault(NotificationUrgency.DEFAULT)
    }
}

@Entity(tableName = "alarm_log")
data class AlarmLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atEpochMs: Long,
    val configName: String,
    val message: String,
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
