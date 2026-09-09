package com.grandsphere.overwatch.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.grandsphere.overwatch.data.db.OverwatchConfigEntity
import com.grandsphere.overwatch.data.db.OverwatchDatabase
import com.grandsphere.overwatch.domain.catalog.AlarmCatalog
import com.grandsphere.overwatch.domain.catalog.CancelCatalog
import com.grandsphere.overwatch.domain.catalog.DismissCatalog
import com.grandsphere.overwatch.domain.catalog.GraceCatalog
import com.grandsphere.overwatch.domain.catalog.NotifyCatalog
import com.grandsphere.overwatch.domain.catalog.PanicModeCatalog
import com.grandsphere.overwatch.domain.model.CrashSensitivity
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.NotificationUrgency
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.RepeatKind
import com.grandsphere.overwatch.domain.model.ShakeStrength
import java.io.File

/**
 * Repairs a **copy** of an imported SQLite file: drop unknown tables/columns,
 * add missing columns, coerce invalid values to current defaults.
 */
object ImportDatabaseValidator {
    private const val CONFIGS = "overwatch_configs"
    private const val META = "meta"
    private const val CONFIGS_NEW = "overwatch_configs_new"

    private data class Col(
        val name: String,
        val ddl: String,
        val missingExpr: String,
    )

    private val configColumns = listOf(
        Col("id", "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", "NULL"),
        Col("name", "`name` TEXT NOT NULL", "'Imported'"),
        Col("intervalMs", "`intervalMs` INTEGER NOT NULL", "300000"),
        Col("repeatKind", "`repeatKind` TEXT NOT NULL", "'SINGLE'"),
        Col("repeatCount", "`repeatCount` INTEGER NOT NULL", "0"),
        Col("windowMs", "`windowMs` INTEGER NOT NULL", "0"),
        Col("checkInHour", "`checkInHour` INTEGER NOT NULL DEFAULT 15", "15"),
        Col("checkInMinute", "`checkInMinute` INTEGER NOT NULL DEFAULT 0", "0"),
        Col("covert", "`covert` INTEGER NOT NULL", "0"),
        Col("cancelMethod", "`cancelMethod` TEXT NOT NULL", "'CONFIRM'"),
        Col("cancelEffectIds", "`cancelEffectIds` TEXT NOT NULL DEFAULT ''", "''"),
        Col("pinHash", "`pinHash` TEXT", "NULL"),
        Col("graceDurationMs", "`graceDurationMs` INTEGER NOT NULL", "60000"),
        Col("notifyEffectIds", "`notifyEffectIds` TEXT NOT NULL", "''"),
        Col("dismissEffectIds", "`dismissEffectIds` TEXT NOT NULL", "''"),
        Col("graceNotifyEffectIds", "`graceNotifyEffectIds` TEXT NOT NULL", "''"),
        Col("graceDismissEffectIds", "`graceDismissEffectIds` TEXT NOT NULL", "''"),
        Col("alarmEffectIds", "`alarmEffectIds` TEXT NOT NULL", "''"),
        Col("contactNumbers", "`contactNumbers` TEXT NOT NULL", "''"),
        Col("smsContactNumbers", "`smsContactNumbers` TEXT NOT NULL DEFAULT ''", "''"),
        Col("callContactNumbers", "`callContactNumbers` TEXT NOT NULL DEFAULT ''", "''"),
        Col("alarmSmsBody", "`alarmSmsBody` TEXT NOT NULL DEFAULT ''", "''"),
        Col("notifySoundUri", "`notifySoundUri` TEXT NOT NULL DEFAULT ''", "''"),
        Col(
            "notifySoundDurationMs",
            "`notifySoundDurationMs` INTEGER NOT NULL DEFAULT -1",
            "-1",
        ),
        Col("alarmSoundUri", "`alarmSoundUri` TEXT NOT NULL DEFAULT ''", "''"),
        Col(
            "alarmSoundDurationMs",
            "`alarmSoundDurationMs` INTEGER NOT NULL DEFAULT -1",
            "-1",
        ),
        Col("dismissHardwareKey", "`dismissHardwareKey` TEXT NOT NULL", "'NONE'"),
        Col("locationRecent", "`locationRecent` INTEGER NOT NULL DEFAULT 0", "0"),
        Col("locationContinuous", "`locationContinuous` INTEGER NOT NULL DEFAULT 0", "0"),
        Col("safetyEffectIds", "`safetyEffectIds` TEXT NOT NULL DEFAULT ''", "''"),
        Col("safetySmsBody", "`safetySmsBody` TEXT NOT NULL DEFAULT ''", "''"),
        Col(
            "safetySmsContactNumbers",
            "`safetySmsContactNumbers` TEXT NOT NULL DEFAULT ''",
            "''",
        ),
        Col(
            "safetyCallContactNumbers",
            "`safetyCallContactNumbers` TEXT NOT NULL DEFAULT ''",
            "''",
        ),
        Col("safetyLocationRecent", "`safetyLocationRecent` INTEGER NOT NULL DEFAULT 0", "0"),
        Col("safetyLocationContinuous", "`safetyLocationContinuous` INTEGER NOT NULL DEFAULT 0", "0"),
        Col("safetyOnCancel", "`safetyOnCancel` INTEGER NOT NULL DEFAULT 0", "0"),
        Col("safetySoundUri", "`safetySoundUri` TEXT NOT NULL DEFAULT ''", "''"),
        Col(
            "safetySoundDurationMs",
            "`safetySoundDurationMs` INTEGER NOT NULL DEFAULT 30000",
            "30000",
        ),
        Col("notifyFlickerOnMs", "`notifyFlickerOnMs` INTEGER NOT NULL DEFAULT 2000", "2000"),
        Col("notifyFlickerOffMs", "`notifyFlickerOffMs` INTEGER NOT NULL DEFAULT 2000", "2000"),
        Col("alarmFlickerOnMs", "`alarmFlickerOnMs` INTEGER NOT NULL DEFAULT 2000", "2000"),
        Col("alarmFlickerOffMs", "`alarmFlickerOffMs` INTEGER NOT NULL DEFAULT 2000", "2000"),
        Col("safetyFlickerOnMs", "`safetyFlickerOnMs` INTEGER NOT NULL DEFAULT 2000", "2000"),
        Col("safetyFlickerOffMs", "`safetyFlickerOffMs` INTEGER NOT NULL DEFAULT 2000", "2000"),
        Col("panicEffectIds", "`panicEffectIds` TEXT NOT NULL DEFAULT 'none'", "'none'"),
        Col("panicPowerTaps", "`panicPowerTaps` INTEGER NOT NULL DEFAULT 3", "3"),
        Col("dismissPowerTaps", "`dismissPowerTaps` INTEGER NOT NULL DEFAULT 3", "3"),
        Col("cancelPowerTaps", "`cancelPowerTaps` INTEGER NOT NULL DEFAULT 3", "3"),
        Col("shakeStrength", "`shakeStrength` TEXT NOT NULL DEFAULT 'MEDIUM'", "'MEDIUM'"),
        Col("dismissShakeStrength", "`dismissShakeStrength` TEXT NOT NULL DEFAULT 'MEDIUM'", "'MEDIUM'"),
        Col("cancelShakeStrength", "`cancelShakeStrength` TEXT NOT NULL DEFAULT 'MEDIUM'", "'MEDIUM'"),
        Col("shakeCount", "`shakeCount` INTEGER NOT NULL DEFAULT 3", "3"),
        Col("dismissShakeCount", "`dismissShakeCount` INTEGER NOT NULL DEFAULT 3", "3"),
        Col("cancelShakeCount", "`cancelShakeCount` INTEGER NOT NULL DEFAULT 3", "3"),
        Col("crashThresholdG", "`crashThresholdG` REAL NOT NULL DEFAULT 8", "8"),
        Col("crashStillnessMs", "`crashStillnessMs` INTEGER NOT NULL DEFAULT 50000", "50000"),
        Col(
            "persistentPanicNotification",
            "`persistentPanicNotification` INTEGER NOT NULL DEFAULT 0",
            "0",
        ),
        Col("liveNotifyShowName", "`liveNotifyShowName` INTEGER NOT NULL DEFAULT 0", "0"),
        Col("alarmNotificationBody", "`alarmNotificationBody` TEXT NOT NULL DEFAULT ''", "''"),
        Col("safetyNotificationBody", "`safetyNotificationBody` TEXT NOT NULL DEFAULT ''", "''"),
        Col("sendTriggerMode", "`sendTriggerMode` INTEGER NOT NULL DEFAULT 1", "1"),
        Col(
            "notifyVibrateDurationMs",
            "`notifyVibrateDurationMs` INTEGER NOT NULL DEFAULT 10000",
            "10000",
        ),
        Col(
            "alarmVibrateDurationMs",
            "`alarmVibrateDurationMs` INTEGER NOT NULL DEFAULT -1",
            "-1",
        ),
        Col(
            "safetyVibrateDurationMs",
            "`safetyVibrateDurationMs` INTEGER NOT NULL DEFAULT 10000",
            "10000",
        ),
        Col(
            "notifyFlashlightDurationMs",
            "`notifyFlashlightDurationMs` INTEGER NOT NULL DEFAULT 5000",
            "5000",
        ),
        Col(
            "alarmFlashlightDurationMs",
            "`alarmFlashlightDurationMs` INTEGER NOT NULL DEFAULT -1",
            "-1",
        ),
        Col(
            "safetyFlashlightDurationMs",
            "`safetyFlashlightDurationMs` INTEGER NOT NULL DEFAULT 5000",
            "5000",
        ),
        Col(
            "notifyFlashlightMode",
            "`notifyFlashlightMode` TEXT NOT NULL DEFAULT 'steady'",
            "'steady'",
        ),
        Col(
            "alarmFlashlightMode",
            "`alarmFlashlightMode` TEXT NOT NULL DEFAULT 'steady'",
            "'steady'",
        ),
        Col(
            "safetyFlashlightMode",
            "`safetyFlashlightMode` TEXT NOT NULL DEFAULT 'steady'",
            "'steady'",
        ),
        Col("notifyNotificationBody", "`notifyNotificationBody` TEXT NOT NULL DEFAULT ''", "''"),
        Col(
            "notifyNotificationUrgency",
            "`notifyNotificationUrgency` TEXT NOT NULL DEFAULT 'DEFAULT'",
            "'DEFAULT'",
        ),
        Col(
            "alarmNotificationUrgency",
            "`alarmNotificationUrgency` TEXT NOT NULL DEFAULT 'DEFAULT'",
            "'DEFAULT'",
        ),
        Col(
            "safetyNotificationUrgency",
            "`safetyNotificationUrgency` TEXT NOT NULL DEFAULT 'DEFAULT'",
            "'DEFAULT'",
        ),
        Col("turnoverHoldMs", "`turnoverHoldMs` INTEGER NOT NULL DEFAULT 700", "700"),
        Col("dismissTurnoverHoldMs", "`dismissTurnoverHoldMs` INTEGER NOT NULL DEFAULT 700", "700"),
        Col("cancelTurnoverHoldMs", "`cancelTurnoverHoldMs` INTEGER NOT NULL DEFAULT 700", "700"),
    )

    fun sanitizeAndRead(copy: File): List<OverwatchConfigEntity> {
        val sqlite = SQLiteDatabase.openDatabase(
            copy.path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            sqlite.beginTransaction()
            try {
                if (!tableExists(sqlite, CONFIGS)) {
                    throw IllegalStateException("Not an Overwatch database")
                }
                dropUnknownObjects(sqlite)
                rebuildConfigsTable(sqlite)
                rebuildMeta(sqlite)
                val sanitized = readRows(sqlite).map { sanitizeEntity(it) }
                sqlite.execSQL("DELETE FROM `$CONFIGS`")
                sanitized.forEach { sqlite.insert(CONFIGS, null, it.toValues()) }
                sqlite.setTransactionSuccessful()
                return sanitized.map { it.copy(id = 0) }
            } finally {
                sqlite.endTransaction()
            }
        } finally {
            sqlite.close()
        }
    }

    internal fun sanitizeEntity(raw: OverwatchConfigEntity): OverwatchConfigEntity {
        val covert = raw.covert
        val repeatKind = enumOr(raw.repeatKind, RepeatKind.SINGLE)
        var repeatCount = raw.repeatCount.coerceAtLeast(0)
        var windowMs = raw.windowMs.coerceAtLeast(0L)
        when (repeatKind) {
            RepeatKind.SINGLE, RepeatKind.BY_TIME -> {
                repeatCount = 0
                windowMs = 0L
            }
            RepeatKind.COUNT -> {
                windowMs = 0L
                if (repeatCount <= 0) repeatCount = 1
            }
            RepeatKind.WINDOW -> {
                repeatCount = 0
                if (windowMs <= 0L) windowMs = 30 * 60_000L
            }
        }
        val cancelMethod = legacyCancelMethod(raw.cancelMethod)
        var cancelIds = dropUnknown(csv(raw.cancelEffectIds)) { CancelCatalog.byId(it) != null }
        cancelIds = cancelIds - CancelCatalog.conflicts(cancelIds)
        if (cancelIds.isEmpty()) {
            cancelIds = CancelCatalog.fromLegacyCancelMethod(cancelMethod)
        }
        if (CancelCatalog.SAME_AS_DISMISS in cancelIds && cancelIds.size > 1) {
            cancelIds = cancelIds.filter { it != CancelCatalog.SAME_AS_DISMISS }
        }
        if (cancelIds.isEmpty()) {
            cancelIds = listOf(CancelCatalog.SAME_AS_DISMISS)
        }
        val pinHash = raw.pinHash?.takeIf { it.isNotBlank() }
        var alarmIds = AlarmCatalog.sanitizeAlarmIds(csv(raw.alarmEffectIds))
        var locationRecent = raw.locationRecent
        var locationContinuous = raw.locationContinuous
        if ("continuous_location" in csv(raw.alarmEffectIds)) {
            locationContinuous = true
        }
        alarmIds = dropUnknown(alarmIds) { AlarmCatalog.byId(it) != null }
        alarmIds = alarmIds - AlarmCatalog.conflicts(alarmIds, covert)
        if ("location" in alarmIds && "sms" !in alarmIds && "log" !in alarmIds) {
            alarmIds = alarmIds.filter { it != "location" }
            locationRecent = false
            locationContinuous = false
        }
        if ("location" !in alarmIds) {
            locationRecent = false
            locationContinuous = false
        }
        if (alarmIds.isEmpty()) alarmIds = listOf("log")

        val rawNotify = csv(raw.notifyEffectIds)
        var notifyIds = NotifyCatalog.sanitizeIds(rawNotify)
        notifyIds = dropUnknown(notifyIds) { NotifyCatalog.byId(it) != null }
        notifyIds = notifyIds - NotifyCatalog.conflicts(notifyIds, covert)
        if (notifyIds.isEmpty()) notifyIds = listOf("none")

        var dismissIds = dropUnknown(csv(raw.dismissEffectIds)) { DismissCatalog.byId(it) != null }
        dismissIds = dismissIds - DismissCatalog.conflicts(dismissIds)
        if (dismissIds.isEmpty()) dismissIds = listOf("tap")

        var graceNotify = NotifyCatalog.sanitizeIds(csv(raw.graceNotifyEffectIds))
        graceNotify = dropUnknown(graceNotify) {
            it == GraceCatalog.NOTIFICATION_ID || NotifyCatalog.byId(it) != null
        }
        graceNotify = graceNotify - NotifyCatalog.conflicts(graceNotify, covert)

        var graceDismiss = dropUnknown(csv(raw.graceDismissEffectIds)) {
            DismissCatalog.byId(it) != null
        }
        graceDismiss = graceDismiss - DismissCatalog.conflicts(graceDismiss)

        val rawSafety = csv(raw.safetyEffectIds)
        var safetyIds = AlarmCatalog.sanitizeSafetyIds(rawSafety)
        safetyIds = dropUnknown(safetyIds) { AlarmCatalog.isAllowedInSafety(it) }
        safetyIds = safetyIds - AlarmCatalog.conflicts(safetyIds, covert)
        if ("location" in safetyIds && "sms" !in safetyIds && "log" !in safetyIds) {
            safetyIds = safetyIds.filter { it != "location" }
        }
        if (safetyIds.isEmpty()) safetyIds = listOf("none")

        var panicIds = PanicModeCatalog.sanitize(
            dropUnknown(csv(raw.panicEffectIds)) { PanicModeCatalog.byId(it) != null },
        )
        panicIds = panicIds - PanicModeCatalog.conflicts(panicIds)

        var alarmSoundDurationMs = raw.alarmSoundDurationMs
        if (alarmSoundDurationMs != OverwatchConfig.SOUND_UNTIL_DISMISSED &&
            alarmSoundDurationMs < 1_000L
        ) {
            alarmSoundDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED
        }
        var notifySoundDurationMs = raw.notifySoundDurationMs
        if (notifySoundDurationMs != OverwatchConfig.SOUND_UNTIL_DISMISSED &&
            notifySoundDurationMs < 1_000L
        ) {
            notifySoundDurationMs = OverwatchConfig.SOUND_UNTIL_DISMISSED
        }
        val safetySoundDurationMs =
            if (raw.safetySoundDurationMs < 1_000L) 30_000L else raw.safetySoundDurationMs

        fun sanitizeVibrate(ms: Long, allowUntil: Boolean, default: Long): Long =
            when {
                allowUntil && ms == OverwatchConfig.SOUND_UNTIL_DISMISSED -> ms
                ms < 1_000L -> default
                else -> ms
            }

        fun sanitizeFlashDuration(ms: Long, allowUntil: Boolean, default: Long): Long =
            when {
                allowUntil && ms == OverwatchConfig.SOUND_UNTIL_DISMISSED -> ms
                ms < 1_000L -> default
                else -> ms
            }

        val crashThresholdG = raw.crashThresholdG.coerceIn(1f, 16f)
        val crashStillnessMs = raw.crashStillnessMs.coerceAtLeast(1_000L)

        val name = raw.name.trim().ifBlank { "Imported" }
        val intervalMs = if (raw.intervalMs > 0L) raw.intervalMs else 5 * 60_000L
        val graceDurationMs = if (raw.graceDurationMs > 0L) raw.graceDurationMs else 60_000L

        return raw.copy(
            name = name,
            intervalMs = intervalMs,
            repeatKind = repeatKind.name,
            repeatCount = repeatCount,
            windowMs = windowMs,
            checkInHour = raw.checkInHour.coerceIn(0, 23),
            checkInMinute = raw.checkInMinute.coerceIn(0, 59),
            covert = covert,
            cancelMethod = cancelMethod,
            cancelEffectIds = cancelIds.joinToString(","),
            pinHash = pinHash,
            graceDurationMs = graceDurationMs,
            notifyEffectIds = notifyIds.joinToString(","),
            dismissEffectIds = dismissIds.joinToString(","),
            graceNotifyEffectIds = graceNotify.joinToString(","),
            graceDismissEffectIds = graceDismiss.joinToString(","),
            alarmEffectIds = alarmIds.joinToString(","),
            contactNumbers = raw.contactNumbers,
            smsContactNumbers = raw.smsContactNumbers.ifBlank { raw.contactNumbers },
            callContactNumbers = raw.callContactNumbers.ifBlank { raw.contactNumbers },
            alarmSmsBody = raw.alarmSmsBody,
            notifySoundUri = raw.notifySoundUri,
            notifySoundDurationMs = notifySoundDurationMs,
            alarmSoundUri = raw.alarmSoundUri,
            alarmSoundDurationMs = alarmSoundDurationMs,
            dismissHardwareKey = enumOr(raw.dismissHardwareKey, HardwareKeyOption.NONE).name,
            locationRecent = locationRecent,
            locationContinuous = locationContinuous,
            safetyEffectIds = safetyIds.joinToString(","),
            safetySmsBody = raw.safetySmsBody,
            safetySmsContactNumbers = raw.safetySmsContactNumbers,
            safetyCallContactNumbers = raw.safetyCallContactNumbers,
            safetyLocationRecent = false,
            safetyLocationContinuous = false,
            safetyOnCancel = raw.safetyOnCancel,
            safetySoundUri = raw.safetySoundUri,
            safetySoundDurationMs = safetySoundDurationMs,
            notifyFlickerOnMs = raw.notifyFlickerOnMs.coerceAtLeast(0L),
            notifyFlickerOffMs = raw.notifyFlickerOffMs.coerceAtLeast(0L),
            alarmFlickerOnMs = raw.alarmFlickerOnMs.coerceAtLeast(0L),
            alarmFlickerOffMs = raw.alarmFlickerOffMs.coerceAtLeast(0L),
            safetyFlickerOnMs = raw.safetyFlickerOnMs.coerceAtLeast(0L),
            safetyFlickerOffMs = raw.safetyFlickerOffMs.coerceAtLeast(0L),
            panicEffectIds = panicIds.joinToString(","),
            panicPowerTaps = raw.panicPowerTaps.coerceIn(2, 10),
            dismissPowerTaps = raw.dismissPowerTaps.coerceIn(2, 10),
            cancelPowerTaps = raw.cancelPowerTaps.coerceIn(2, 10),
            shakeStrength = enumOr(raw.shakeStrength, ShakeStrength.MEDIUM).name,
            dismissShakeStrength = enumOr(raw.dismissShakeStrength, ShakeStrength.MEDIUM).name,
            cancelShakeStrength = enumOr(raw.cancelShakeStrength, ShakeStrength.MEDIUM).name,
            shakeCount = raw.shakeCount.coerceIn(1, 10),
            dismissShakeCount = raw.dismissShakeCount.coerceIn(1, 10),
            cancelShakeCount = raw.cancelShakeCount.coerceIn(1, 10),
            crashThresholdG = crashThresholdG,
            crashStillnessMs = crashStillnessMs,
            persistentPanicNotification = raw.persistentPanicNotification,
            liveNotifyShowName = raw.liveNotifyShowName,
            alarmNotificationBody = raw.alarmNotificationBody,
            safetyNotificationBody = raw.safetyNotificationBody,
            notifyNotificationBody = raw.notifyNotificationBody,
            notifyNotificationUrgency = enumOr(raw.notifyNotificationUrgency, NotificationUrgency.DEFAULT).name,
            alarmNotificationUrgency = enumOr(raw.alarmNotificationUrgency, NotificationUrgency.DEFAULT).name,
            safetyNotificationUrgency = enumOr(raw.safetyNotificationUrgency, NotificationUrgency.DEFAULT).name,
            turnoverHoldMs = raw.turnoverHoldMs.coerceAtLeast(100L),
            dismissTurnoverHoldMs = raw.dismissTurnoverHoldMs.coerceAtLeast(100L),
            cancelTurnoverHoldMs = raw.cancelTurnoverHoldMs.coerceAtLeast(100L),
            sendTriggerMode = raw.sendTriggerMode,
            notifyVibrateDurationMs = sanitizeVibrate(raw.notifyVibrateDurationMs, true, 10_000L),
            alarmVibrateDurationMs = sanitizeVibrate(
                raw.alarmVibrateDurationMs,
                true,
                OverwatchConfig.SOUND_UNTIL_DISMISSED,
            ),
            safetyVibrateDurationMs = sanitizeVibrate(raw.safetyVibrateDurationMs, false, 10_000L),
            notifyFlashlightDurationMs = sanitizeFlashDuration(
                raw.notifyFlashlightDurationMs,
                true,
                5_000L,
            ),
            alarmFlashlightDurationMs = sanitizeFlashDuration(
                raw.alarmFlashlightDurationMs,
                true,
                OverwatchConfig.SOUND_UNTIL_DISMISSED,
            ),
            safetyFlashlightDurationMs = sanitizeFlashDuration(
                raw.safetyFlashlightDurationMs,
                false,
                5_000L,
            ),
            notifyFlashlightMode = NotifyCatalog.flashlightModeFromLegacy(
                rawNotify,
                raw.notifyFlashlightMode,
            ),
            alarmFlashlightMode = NotifyCatalog.flashlightModeFromLegacy(
                csv(raw.alarmEffectIds),
                raw.alarmFlashlightMode,
            ),
            safetyFlashlightMode = NotifyCatalog.flashlightModeFromLegacy(
                rawSafety,
                raw.safetyFlashlightMode,
            ),
        )
    }

    private fun rebuildConfigsTable(db: SQLiteDatabase) {
        val existing = columnNames(db, CONFIGS)
        db.execSQL("DROP TABLE IF EXISTS `$CONFIGS_NEW`")
        db.execSQL(
            "CREATE TABLE `$CONFIGS_NEW` (${configColumns.joinToString(", ") { it.ddl }})",
        )
        val insertCols = configColumns.joinToString(", ") { "`${it.name}`" }
        val selectCols = configColumns.joinToString(", ") { col ->
            if (col.name in existing) "`${col.name}`" else col.missingExpr
        }
        db.execSQL(
            "INSERT INTO `$CONFIGS_NEW` ($insertCols) SELECT $selectCols FROM `$CONFIGS`",
        )
        db.execSQL("DROP TABLE `$CONFIGS`")
        db.execSQL("ALTER TABLE `$CONFIGS_NEW` RENAME TO `$CONFIGS`")
    }

    private fun rebuildMeta(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `$META`")
        db.execSQL(
            "CREATE TABLE `$META` (`key` TEXT PRIMARY KEY NOT NULL, `value` TEXT NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `$META` (`key`, `value`) VALUES (?, ?)",
            arrayOf(OverwatchDatabase.SCHEMA_VERSION_KEY, OverwatchDatabase.SCHEMA_VERSION),
        )
    }

    private fun dropUnknownObjects(db: SQLiteDatabase) {
        val keep = setOf(CONFIGS, META, "android_metadata", "sqlite_sequence")
        val names = mutableListOf<Pair<String, String>>()
        db.rawQuery(
            "SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val type = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name in keep) continue
                names += type to name
            }
        }
        names.forEach { (type, name) ->
            when (type) {
                "table" -> db.execSQL("DROP TABLE IF EXISTS `$name`")
                "view" -> db.execSQL("DROP VIEW IF EXISTS `$name`")
                "trigger" -> db.execSQL("DROP TRIGGER IF EXISTS `$name`")
                "index" -> db.execSQL("DROP INDEX IF EXISTS `$name`")
            }
        }
    }

    private fun readRows(db: SQLiteDatabase): List<OverwatchConfigEntity> {
        val rows = mutableListOf<OverwatchConfigEntity>()
        db.rawQuery("SELECT * FROM `$CONFIGS`", null).use { c ->
            while (c.moveToNext()) {
                rows += OverwatchConfigEntity(
                    id = 0,
                    name = c.optionalString("name"),
                    intervalMs = c.optionalLong("intervalMs", 5 * 60_000L),
                    repeatKind = c.optionalString("repeatKind", RepeatKind.SINGLE.name),
                    repeatCount = c.optionalInt("repeatCount", 0),
                    windowMs = c.optionalLong("windowMs", 0L),
                    checkInHour = c.optionalInt("checkInHour", 15),
                    checkInMinute = c.optionalInt("checkInMinute", 0),
                    covert = c.bool01("covert"),
                    cancelMethod = c.optionalString("cancelMethod", "CONFIRM"),
                    cancelEffectIds = c.optionalString("cancelEffectIds"),
                    pinHash = c.optionalNullableString("pinHash"),
                    graceDurationMs = c.optionalLong("graceDurationMs", 60_000L),
                    notifyEffectIds = c.optionalString("notifyEffectIds"),
                    dismissEffectIds = c.optionalString("dismissEffectIds"),
                    graceNotifyEffectIds = c.optionalString("graceNotifyEffectIds"),
                    graceDismissEffectIds = c.optionalString("graceDismissEffectIds"),
                    alarmEffectIds = c.optionalString("alarmEffectIds"),
                    contactNumbers = c.optionalString("contactNumbers"),
                    smsContactNumbers = c.optionalString("smsContactNumbers"),
                    callContactNumbers = c.optionalString("callContactNumbers"),
                    alarmSmsBody = c.optionalString("alarmSmsBody"),
                    notifySoundUri = c.optionalString("notifySoundUri"),
                    notifySoundDurationMs = c.optionalLong(
                        "notifySoundDurationMs",
                        OverwatchConfig.SOUND_UNTIL_DISMISSED,
                    ),
                    alarmSoundUri = c.optionalString("alarmSoundUri"),
                    alarmSoundDurationMs = c.optionalLong(
                        "alarmSoundDurationMs",
                        OverwatchConfig.SOUND_UNTIL_DISMISSED,
                    ),
                    dismissHardwareKey = c.optionalString(
                        "dismissHardwareKey",
                        HardwareKeyOption.NONE.name,
                    ),
                    locationRecent = c.bool01("locationRecent"),
                    locationContinuous = c.bool01("locationContinuous"),
                    safetyEffectIds = c.optionalString("safetyEffectIds"),
                    safetySmsBody = c.optionalString("safetySmsBody"),
                    safetySmsContactNumbers = c.optionalString("safetySmsContactNumbers"),
                    safetyCallContactNumbers = c.optionalString("safetyCallContactNumbers"),
                    safetyLocationRecent = false,
                    safetyLocationContinuous = false,
                    safetyOnCancel = c.bool01("safetyOnCancel"),
                    safetySoundUri = c.optionalString("safetySoundUri"),
                    safetySoundDurationMs = c.optionalLong("safetySoundDurationMs", 30_000L),
                    notifyFlickerOnMs = c.optionalLong("notifyFlickerOnMs", 2_000L),
                    notifyFlickerOffMs = c.optionalLong("notifyFlickerOffMs", 2_000L),
                    alarmFlickerOnMs = c.optionalLong("alarmFlickerOnMs", 2_000L),
                    alarmFlickerOffMs = c.optionalLong("alarmFlickerOffMs", 2_000L),
                    safetyFlickerOnMs = c.optionalLong("safetyFlickerOnMs", 2_000L),
                    safetyFlickerOffMs = c.optionalLong("safetyFlickerOffMs", 2_000L),
                    panicEffectIds = c.optionalString("panicEffectIds", "none"),
                    panicPowerTaps = c.optionalInt("panicPowerTaps", 3),
                    dismissPowerTaps = c.optionalInt("dismissPowerTaps", c.optionalInt("panicPowerTaps", 3)),
                    cancelPowerTaps = c.optionalInt("cancelPowerTaps", c.optionalInt("panicPowerTaps", 3)),
                    shakeStrength = c.optionalString("shakeStrength", ShakeStrength.MEDIUM.name),
                    dismissShakeStrength = c.optionalString(
                        "dismissShakeStrength",
                        c.optionalString("shakeStrength", ShakeStrength.MEDIUM.name),
                    ),
                    cancelShakeStrength = c.optionalString(
                        "cancelShakeStrength",
                        c.optionalString("shakeStrength", ShakeStrength.MEDIUM.name),
                    ),
                    shakeCount = c.optionalInt("shakeCount", 3),
                    dismissShakeCount = c.optionalInt("dismissShakeCount", c.optionalInt("shakeCount", 3)),
                    cancelShakeCount = c.optionalInt("cancelShakeCount", c.optionalInt("shakeCount", 3)),
                    crashThresholdG = c.optionalCrashThresholdG(),
                    crashStillnessMs = c.optionalCrashStillnessMs(),
                    persistentPanicNotification = c.bool01("persistentPanicNotification"),
                    liveNotifyShowName = c.bool01("liveNotifyShowName"),
                    alarmNotificationBody = c.optionalString("alarmNotificationBody"),
                    safetyNotificationBody = c.optionalString("safetyNotificationBody"),
                    notifyNotificationBody = c.optionalString("notifyNotificationBody"),
                    notifyNotificationUrgency = c.optionalString(
                        "notifyNotificationUrgency",
                        NotificationUrgency.DEFAULT.name,
                    ),
                    alarmNotificationUrgency = c.optionalString(
                        "alarmNotificationUrgency",
                        NotificationUrgency.DEFAULT.name,
                    ),
                    safetyNotificationUrgency = c.optionalString(
                        "safetyNotificationUrgency",
                        NotificationUrgency.DEFAULT.name,
                    ),
                    turnoverHoldMs = c.optionalLong("turnoverHoldMs", 700L),
                    dismissTurnoverHoldMs = c.optionalLong(
                        "dismissTurnoverHoldMs",
                        c.optionalLong("turnoverHoldMs", 700L),
                    ),
                    cancelTurnoverHoldMs = c.optionalLong(
                        "cancelTurnoverHoldMs",
                        c.optionalLong("turnoverHoldMs", 700L),
                    ),
                    sendTriggerMode = c.bool01("sendTriggerMode", default = true),
                    notifyVibrateDurationMs = c.optionalLong("notifyVibrateDurationMs", 10_000L),
                    alarmVibrateDurationMs = c.optionalLong(
                        "alarmVibrateDurationMs",
                        OverwatchConfig.SOUND_UNTIL_DISMISSED,
                    ),
                    safetyVibrateDurationMs = c.optionalLong("safetyVibrateDurationMs", 10_000L),
                    notifyFlashlightDurationMs = c.optionalLong("notifyFlashlightDurationMs", 5_000L),
                    alarmFlashlightDurationMs = c.optionalLong(
                        "alarmFlashlightDurationMs",
                        OverwatchConfig.SOUND_UNTIL_DISMISSED,
                    ),
                    safetyFlashlightDurationMs = c.optionalLong("safetyFlashlightDurationMs", 5_000L),
                    notifyFlashlightMode = c.optionalString("notifyFlashlightMode", "steady"),
                    alarmFlashlightMode = c.optionalString("alarmFlashlightMode", "steady"),
                    safetyFlashlightMode = c.optionalString("safetyFlashlightMode", "steady"),
                )
            }
        }
        return rows
    }

    private fun OverwatchConfigEntity.toValues(): ContentValues = ContentValues().apply {
        put("name", name)
        put("intervalMs", intervalMs)
        put("repeatKind", repeatKind)
        put("repeatCount", repeatCount)
        put("windowMs", windowMs)
        put("checkInHour", checkInHour)
        put("checkInMinute", checkInMinute)
        put("covert", if (covert) 1 else 0)
        put("cancelMethod", cancelMethod)
        put("cancelEffectIds", cancelEffectIds)
        if (pinHash == null) putNull("pinHash") else put("pinHash", pinHash)
        put("graceDurationMs", graceDurationMs)
        put("notifyEffectIds", notifyEffectIds)
        put("dismissEffectIds", dismissEffectIds)
        put("graceNotifyEffectIds", graceNotifyEffectIds)
        put("graceDismissEffectIds", graceDismissEffectIds)
        put("alarmEffectIds", alarmEffectIds)
        put("contactNumbers", contactNumbers)
        put("smsContactNumbers", smsContactNumbers)
        put("callContactNumbers", callContactNumbers)
        put("alarmSmsBody", alarmSmsBody)
        put("notifySoundUri", notifySoundUri)
        put("notifySoundDurationMs", notifySoundDurationMs)
        put("alarmSoundUri", alarmSoundUri)
        put("alarmSoundDurationMs", alarmSoundDurationMs)
        put("dismissHardwareKey", dismissHardwareKey)
        put("locationRecent", if (locationRecent) 1 else 0)
        put("locationContinuous", if (locationContinuous) 1 else 0)
        put("safetyEffectIds", safetyEffectIds)
        put("safetySmsBody", safetySmsBody)
        put("safetySmsContactNumbers", safetySmsContactNumbers)
        put("safetyCallContactNumbers", safetyCallContactNumbers)
        put("safetyLocationRecent", 0)
        put("safetyLocationContinuous", 0)
        put("safetyOnCancel", if (safetyOnCancel) 1 else 0)
        put("safetySoundUri", safetySoundUri)
        put("safetySoundDurationMs", safetySoundDurationMs)
        put("notifyFlickerOnMs", notifyFlickerOnMs)
        put("notifyFlickerOffMs", notifyFlickerOffMs)
        put("alarmFlickerOnMs", alarmFlickerOnMs)
        put("alarmFlickerOffMs", alarmFlickerOffMs)
        put("safetyFlickerOnMs", safetyFlickerOnMs)
        put("safetyFlickerOffMs", safetyFlickerOffMs)
        put("panicEffectIds", panicEffectIds)
        put("panicPowerTaps", panicPowerTaps)
        put("dismissPowerTaps", dismissPowerTaps)
        put("cancelPowerTaps", cancelPowerTaps)
        put("shakeStrength", shakeStrength)
        put("dismissShakeStrength", dismissShakeStrength)
        put("cancelShakeStrength", cancelShakeStrength)
        put("shakeCount", shakeCount)
        put("dismissShakeCount", dismissShakeCount)
        put("cancelShakeCount", cancelShakeCount)
        put("crashThresholdG", crashThresholdG)
        put("crashStillnessMs", crashStillnessMs)
        put("persistentPanicNotification", if (persistentPanicNotification) 1 else 0)
        put("liveNotifyShowName", if (liveNotifyShowName) 1 else 0)
        put("alarmNotificationBody", alarmNotificationBody)
        put("safetyNotificationBody", safetyNotificationBody)
        put("notifyNotificationBody", notifyNotificationBody)
        put("notifyNotificationUrgency", notifyNotificationUrgency)
        put("alarmNotificationUrgency", alarmNotificationUrgency)
        put("safetyNotificationUrgency", safetyNotificationUrgency)
        put("turnoverHoldMs", turnoverHoldMs)
        put("dismissTurnoverHoldMs", dismissTurnoverHoldMs)
        put("cancelTurnoverHoldMs", cancelTurnoverHoldMs)
        put("sendTriggerMode", if (sendTriggerMode) 1 else 0)
        put("notifyVibrateDurationMs", notifyVibrateDurationMs)
        put("alarmVibrateDurationMs", alarmVibrateDurationMs)
        put("safetyVibrateDurationMs", safetyVibrateDurationMs)
        put("notifyFlashlightDurationMs", notifyFlashlightDurationMs)
        put("alarmFlashlightDurationMs", alarmFlashlightDurationMs)
        put("safetyFlashlightDurationMs", safetyFlashlightDurationMs)
        put("notifyFlashlightMode", notifyFlashlightMode)
        put("alarmFlashlightMode", alarmFlashlightMode)
        put("safetyFlashlightMode", safetyFlashlightMode)
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(name),
        ).use { return it.moveToFirst() }
    }

    private fun columnNames(db: SQLiteDatabase, table: String): Set<String> {
        val names = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                names += c.getString(idx)
            }
        }
        return names
    }

    private fun csv(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun migrateAlarmIds(ids: List<String>): List<String> =
        AlarmCatalog.sanitizeAlarmIds(ids)

    private fun dropUnknown(ids: List<String>, keep: (String) -> Boolean): List<String> =
        ids.filter(keep).distinct()

    private fun legacyCancelMethod(raw: String): String = when (raw.trim().uppercase()) {
        "PIN", "FINGERPRINT", "DISMISS_METHODS", "CONFIRM" -> raw.trim().uppercase()
        else -> "CONFIRM"
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
}

private fun Cursor.optionalString(name: String, default: String = ""): String {
    val i = getColumnIndex(name)
    if (i < 0 || isNull(i)) return default
    return getString(i) ?: default
}

private fun Cursor.optionalNullableString(name: String): String? {
    val i = getColumnIndex(name)
    if (i < 0 || isNull(i)) return null
    return getString(i)?.takeIf { it.isNotBlank() }
}

private fun Cursor.optionalInt(name: String, default: Int): Int {
    val i = getColumnIndex(name)
    if (i < 0 || isNull(i)) return default
    return getInt(i)
}

private fun Cursor.optionalLong(name: String, default: Long): Long {
    val i = getColumnIndex(name)
    if (i < 0 || isNull(i)) return default
    return getLong(i)
}

private fun Cursor.optionalFloat(name: String, default: Float): Float {
    val i = getColumnIndex(name)
    if (i < 0 || isNull(i)) return default
    return getFloat(i)
}

private fun Cursor.optionalCrashThresholdG(): Float {
    val i = getColumnIndex("crashThresholdG")
    if (i >= 0 && !isNull(i)) return getFloat(i).coerceIn(1f, 16f)
    return when (optionalString("crashSensitivity", CrashSensitivity.MEDIUM.name)) {
        CrashSensitivity.HIGH.name -> CrashSensitivity.HIGH.thresholdG
        CrashSensitivity.LOW.name -> CrashSensitivity.LOW.thresholdG
        else -> CrashSensitivity.MEDIUM.thresholdG
    }
}

private fun Cursor.optionalCrashStillnessMs(): Long {
    val i = getColumnIndex("crashStillnessMs")
    if (i >= 0 && !isNull(i)) return getLong(i).coerceAtLeast(1_000L)
    return when (optionalString("crashSensitivity", CrashSensitivity.MEDIUM.name)) {
        CrashSensitivity.HIGH.name -> CrashSensitivity.HIGH.defaultStillnessMs
        CrashSensitivity.LOW.name -> CrashSensitivity.LOW.defaultStillnessMs
        else -> CrashSensitivity.MEDIUM.defaultStillnessMs
    }
}

private fun Cursor.bool01(name: String, default: Boolean = false): Boolean {
    val i = getColumnIndex(name)
    if (i < 0 || isNull(i)) return default
    return getInt(i) == 1
}
