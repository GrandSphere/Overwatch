package com.grandsphere.overwatch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.RepeatKind
import com.grandsphere.overwatch.domain.catalog.CancelCatalog

@Database(
    entities = [OverwatchConfigEntity::class, AlarmLogEntity::class, MetaEntity::class],
        version = 17,
    exportSchema = false,
)
abstract class OverwatchDatabase : RoomDatabase() {
    abstract fun dao(): OverwatchDao

    companion object {
        const val NAME = "overwatch.db"
        const val SCHEMA_VERSION_KEY = "schema_version"
        const val SCHEMA_VERSION = "17"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmSmsBody TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN checkInHour INTEGER NOT NULL DEFAULT 15",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN checkInMinute INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifySoundUri TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmSoundUri TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN locationRecent INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN locationContinuous INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyEffectIds TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetySmsBody TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyLocationRecent INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyLocationContinuous INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyOnCancel INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetySoundUri TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmSoundDurationMs INTEGER NOT NULL DEFAULT -1",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetySoundDurationMs INTEGER NOT NULL DEFAULT 30000",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifySoundDurationMs INTEGER NOT NULL DEFAULT -1",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN smsContactNumbers TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN callContactNumbers TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "UPDATE overwatch_configs SET smsContactNumbers = contactNumbers, callContactNumbers = contactNumbers",
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN cancelEffectIds TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN persistentPanicNotification INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN liveNotifyShowName INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmNotificationBody TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyNotificationBody TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    """
                    UPDATE overwatch_configs SET cancelEffectIds = CASE cancelMethod
                        WHEN 'PIN' THEN 'pin'
                        WHEN 'FINGERPRINT' THEN 'fingerprint'
                        WHEN 'DISMISS_METHODS' THEN 'same_as_dismiss'
                        WHEN 'CONFIRM' THEN 'same_as_dismiss'
                        ELSE 'same_as_dismiss'
                    END
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifyFlickerOnMs INTEGER NOT NULL DEFAULT 2000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifyFlickerOffMs INTEGER NOT NULL DEFAULT 2000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmFlickerOnMs INTEGER NOT NULL DEFAULT 2000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmFlickerOffMs INTEGER NOT NULL DEFAULT 2000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyFlickerOnMs INTEGER NOT NULL DEFAULT 2000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyFlickerOffMs INTEGER NOT NULL DEFAULT 2000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN panicEffectIds TEXT NOT NULL DEFAULT 'none'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN panicPowerTaps INTEGER NOT NULL DEFAULT 3",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN shakeStrength TEXT NOT NULL DEFAULT 'MEDIUM'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN shakeCount INTEGER NOT NULL DEFAULT 3",
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN crashSensitivity TEXT NOT NULL DEFAULT 'HIGH'",
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN crashThresholdG REAL NOT NULL DEFAULT 8",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN crashStillnessMs INTEGER NOT NULL DEFAULT 50000",
                )
                db.execSQL(
                    """
                    UPDATE overwatch_configs SET
                      crashThresholdG = CASE crashSensitivity
                        WHEN 'MEDIUM' THEN 8
                        WHEN 'LOW' THEN 12
                        ELSE 5.5
                      END,
                      crashStillnessMs = CASE crashSensitivity
                        WHEN 'MEDIUM' THEN 50000
                        WHEN 'LOW' THEN 60000
                        ELSE 40000
                      END
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN sendTriggerMode INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifyVibrateDurationMs INTEGER NOT NULL DEFAULT 10000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmVibrateDurationMs INTEGER NOT NULL DEFAULT -1",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyVibrateDurationMs INTEGER NOT NULL DEFAULT 10000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifyFlashlightDurationMs INTEGER NOT NULL DEFAULT 5000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmFlashlightDurationMs INTEGER NOT NULL DEFAULT -1",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyFlashlightDurationMs INTEGER NOT NULL DEFAULT 5000",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifyFlashlightMode TEXT NOT NULL DEFAULT 'steady'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmFlashlightMode TEXT NOT NULL DEFAULT 'steady'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyFlashlightMode TEXT NOT NULL DEFAULT 'steady'",
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetySmsContactNumbers TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyCallContactNumbers TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifyNotificationBody TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN notifyNotificationUrgency TEXT NOT NULL DEFAULT 'DEFAULT'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN alarmNotificationUrgency TEXT NOT NULL DEFAULT 'DEFAULT'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN safetyNotificationUrgency TEXT NOT NULL DEFAULT 'DEFAULT'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN turnoverHoldMs INTEGER NOT NULL DEFAULT 700",
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN dismissPowerTaps INTEGER NOT NULL DEFAULT 3",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN cancelPowerTaps INTEGER NOT NULL DEFAULT 3",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN dismissShakeStrength TEXT NOT NULL DEFAULT 'MEDIUM'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN cancelShakeStrength TEXT NOT NULL DEFAULT 'MEDIUM'",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN dismissShakeCount INTEGER NOT NULL DEFAULT 3",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN cancelShakeCount INTEGER NOT NULL DEFAULT 3",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN dismissTurnoverHoldMs INTEGER NOT NULL DEFAULT 700",
                )
                db.execSQL(
                    "ALTER TABLE overwatch_configs ADD COLUMN cancelTurnoverHoldMs INTEGER NOT NULL DEFAULT 700",
                )
                db.execSQL(
                    """
                    UPDATE overwatch_configs SET
                      dismissPowerTaps = panicPowerTaps,
                      cancelPowerTaps = panicPowerTaps,
                      dismissShakeStrength = shakeStrength,
                      cancelShakeStrength = shakeStrength,
                      dismissShakeCount = shakeCount,
                      cancelShakeCount = shakeCount,
                      dismissTurnoverHoldMs = turnoverHoldMs,
                      cancelTurnoverHoldMs = turnoverHoldMs
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): OverwatchDatabase =
            Room.databaseBuilder(context, OverwatchDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                )
                .build()
    }
}

object SeedConfigs {
    val defaultOverwatch: OverwatchConfig = OverwatchConfig(
        name = OverwatchConfig.DEFAULT_NAME,
        intervalMs = 5 * 60_000L,
        repeatKind = RepeatKind.WINDOW,
        windowMs = 30 * 60_000L,
        covert = false,
        cancelEffectIds = listOf(CancelCatalog.SAME_AS_DISMISS),
        pinHash = null,
        graceDurationMs = 30_000L,
        notifyEffectIds = listOf("sound"),
        dismissEffectIds = listOf("tap"),
        graceNotifyEffectIds = emptyList(),
        graceDismissEffectIds = emptyList(),
        alarmEffectIds = listOf("log", "siren"),
        notifySoundDurationMs = 5_000L,
        alarmSoundDurationMs = 20_000L,
        dismissHardwareKey = HardwareKeyOption.NONE,
    )

    val driving: OverwatchConfig = OverwatchConfig(
        name = "Driving",
        intervalMs = 20 * 60_000L,
        repeatKind = RepeatKind.WINDOW,
        windowMs = 60 * 60_000L,
        covert = false,
        cancelEffectIds = listOf(CancelCatalog.SAME_AS_DISMISS),
        pinHash = null,
        graceDurationMs = 10 * 60_000L,
        notifyEffectIds = listOf("sound", "live_notify"),
        dismissEffectIds = listOf("auto_fingerprint"),
        graceNotifyEffectIds = emptyList(),
        graceDismissEffectIds = emptyList(),
        alarmEffectIds = listOf("location", "log"),
        locationRecent = true,
        locationContinuous = true,
        notifySoundDurationMs = 5_000L,
        dismissHardwareKey = HardwareKeyOption.NONE,
        panicEffectIds = listOf("none"),
        safetyEffectIds = listOf("none"),
    )

    val gym: OverwatchConfig = OverwatchConfig(
        name = "Gym",
        intervalMs = 10 * 60_000L,
        repeatKind = RepeatKind.WINDOW,
        windowMs = 60 * 60_000L,
        covert = false,
        cancelEffectIds = listOf(CancelCatalog.SAME_AS_DISMISS),
        pinHash = null,
        graceDurationMs = 60_000L,
        notifyEffectIds = listOf("sound", "live_notify"),
        dismissEffectIds = listOf("tap"),
        graceNotifyEffectIds = emptyList(),
        graceDismissEffectIds = emptyList(),
        alarmEffectIds = listOf("siren"),
        notifySoundDurationMs = 5_000L,
        alarmSoundDurationMs = 20_000L,
        dismissHardwareKey = HardwareKeyOption.NONE,
        panicEffectIds = listOf("none"),
        safetyEffectIds = listOf("none"),
    )

    val publicTransport: OverwatchConfig = OverwatchConfig(
        name = "Public Transport",
        intervalMs = 60_000L,
        repeatKind = RepeatKind.WINDOW,
        windowMs = 30 * 60_000L,
        covert = true,
        cancelEffectIds = listOf(CancelCatalog.SAME_AS_DISMISS),
        pinHash = null,
        graceDurationMs = 30_000L,
        notifyEffectIds = listOf("none"),
        dismissEffectIds = listOf("auto_fingerprint"),
        graceNotifyEffectIds = emptyList(),
        graceDismissEffectIds = emptyList(),
        alarmEffectIds = listOf("location", "log"),
        locationRecent = true,
        dismissHardwareKey = HardwareKeyOption.NONE,
        panicEffectIds = listOf("none"),
        safetyEffectIds = listOf("none"),
    )

    fun blankNew(): OverwatchConfig = OverwatchConfig(
        id = 0,
        name = "New",
        intervalMs = 5 * 60_000L,
        repeatKind = RepeatKind.SINGLE,
        cancelEffectIds = listOf(CancelCatalog.SAME_AS_DISMISS),
        dismissEffectIds = listOf("tap"),
        graceDurationMs = 60_000L,
    )
}
