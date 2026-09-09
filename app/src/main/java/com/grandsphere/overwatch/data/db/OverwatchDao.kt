package com.grandsphere.overwatch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OverwatchDao {
    @Query("SELECT * FROM overwatch_configs ORDER BY CASE name WHEN 'Default' THEN 0 ELSE 1 END, name")
    fun observeConfigs(): Flow<List<OverwatchConfigEntity>>

    @Query("SELECT * FROM overwatch_configs ORDER BY CASE name WHEN 'Default' THEN 0 ELSE 1 END, name")
    suspend fun listConfigs(): List<OverwatchConfigEntity>

    @Query("SELECT * FROM overwatch_configs WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): OverwatchConfigEntity?

    @Query("SELECT * FROM overwatch_configs WHERE id = :id")
    suspend fun getConfig(id: Long): OverwatchConfigEntity?

    @Insert
    suspend fun insert(entity: OverwatchConfigEntity): Long

    @Update
    suspend fun update(entity: OverwatchConfigEntity)

    @Query("DELETE FROM overwatch_configs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM overwatch_configs")
    suspend fun deleteAllConfigs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entity: AlarmLogEntity): Long

    @Query("SELECT * FROM alarm_log ORDER BY atEpochMs DESC LIMIT 100")
    fun observeLog(): Flow<List<AlarmLogEntity>>

    @Query("SELECT * FROM alarm_log ORDER BY atEpochMs DESC LIMIT :limit")
    suspend fun listLog(limit: Int = 100): List<AlarmLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(entity: MetaEntity)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun getMeta(key: String): String?
}
