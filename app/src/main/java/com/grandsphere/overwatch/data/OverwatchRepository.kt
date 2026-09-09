package com.grandsphere.overwatch.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.grandsphere.overwatch.data.db.AlarmLogEntity
import com.grandsphere.overwatch.data.db.MetaEntity
import com.grandsphere.overwatch.data.db.OverwatchConfigEntity
import com.grandsphere.overwatch.data.db.OverwatchDatabase
import com.grandsphere.overwatch.data.db.SeedConfigs
import com.grandsphere.overwatch.domain.model.AlarmLogEntry
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class OverwatchRepository(
    private val context: Context,
    private val db: OverwatchDatabase,
) {
    private val dao = db.dao()

    val configs: Flow<List<OverwatchConfig>> =
        dao.observeConfigs().map { list -> list.map { it.toDomain() } }

    val log: Flow<List<AlarmLogEntry>> =
        dao.observeLog().map { list ->
            list.map { AlarmLogEntry(it.id, it.atEpochMs, it.configName, it.message) }
        }

    suspend fun seedIfEmpty() {
        if (dao.listConfigs().isEmpty()) {
            dao.insert(OverwatchConfigEntity.from(SeedConfigs.defaultOverwatch))
            dao.insert(OverwatchConfigEntity.from(SeedConfigs.driving))
            dao.insert(OverwatchConfigEntity.from(SeedConfigs.gym))
            dao.insert(OverwatchConfigEntity.from(SeedConfigs.publicTransport))
            dao.putMeta(MetaEntity(OverwatchDatabase.SCHEMA_VERSION_KEY, OverwatchDatabase.SCHEMA_VERSION))
        }
        ensureDefault()
    }

    suspend fun ensureDefault() {
        if (dao.getByName(OverwatchConfig.DEFAULT_NAME) == null) {
            dao.insert(OverwatchConfigEntity.from(SeedConfigs.defaultOverwatch))
        }
    }

    suspend fun defaultConfig(): OverwatchConfig? =
        dao.getByName(OverwatchConfig.DEFAULT_NAME)?.toDomain()

    suspend fun list(): List<OverwatchConfig> = dao.listConfigs().map { it.toDomain() }

    suspend fun get(id: Long): OverwatchConfig? = dao.getConfig(id)?.toDomain()

    suspend fun save(config: OverwatchConfig): Long {
        val entity = OverwatchConfigEntity.from(config)
        val id = if (config.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            config.id
        }
        com.grandsphere.overwatch.widget.refreshOverwatchWidgets(context)
        return id
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        com.grandsphere.overwatch.widget.refreshOverwatchWidgets(context)
    }

    suspend fun clearOverwatchesKeepDefault() {
        db.withTransaction {
            dao.deleteAllConfigs()
            dao.insert(OverwatchConfigEntity.from(SeedConfigs.defaultOverwatch))
        }
        com.grandsphere.overwatch.widget.refreshOverwatchWidgets(context)
    }

    suspend fun appendLog(configName: String, message: String) {
        dao.insertLog(
            AlarmLogEntity(
                atEpochMs = System.currentTimeMillis(),
                configName = configName,
                message = message,
            ),
        )
        com.grandsphere.overwatch.runtime.DocumentsLog.append(context, configName, message)
    }

    suspend fun ensureUserLogShareable(): File? {
        val existing = com.grandsphere.overwatch.runtime.DocumentsLog.ensureShareableCopy(context)
        if (existing != null) return existing
        val rows = dao.listLog(10_000).asReversed()
        if (rows.isEmpty()) return null
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        for (row in rows) {
            val line = "${stamp.format(java.util.Date(row.atEpochMs))}\t${row.configName}\t${row.message}\n"
            com.grandsphere.overwatch.runtime.DocumentsLog.appendRaw(context, line)
        }
        return com.grandsphere.overwatch.runtime.DocumentsLog.ensureShareableCopy(context)
    }

    suspend fun recentLog(limit: Int = 100): List<AlarmLogEntry> =
        dao.listLog(limit).map { AlarmLogEntry(it.id, it.atEpochMs, it.configName, it.message) }


    /**
     * Export configs only (no alarm log) as a shareable SQLite file.
     */
    suspend fun exportConfigsDb(): File {
        db.withTransaction {
            dao.putMeta(MetaEntity(OverwatchDatabase.SCHEMA_VERSION_KEY, OverwatchDatabase.SCHEMA_VERSION))
        }
        db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).close()
        val src = context.getDatabasePath(OverwatchDatabase.NAME)
        val dest = File(context.cacheDir, "overwatch-export.db")
        src.copyTo(dest, overwrite = true)
        return dest
    }

    suspend fun importConfigs(uri: Uri) {
        val tmp = File(context.cacheDir, "overwatch-import.db")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read import")
        try {
            val rows = ImportDatabaseValidator.sanitizeAndRead(tmp)
            db.withTransaction {
                for (row in rows) dao.insert(row)
            }
        } finally {
            tmp.delete()
        }
        com.grandsphere.overwatch.widget.refreshOverwatchWidgets(context)
    }
}
