package dev.context.app.storage.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    suspend fun getAll(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE enabled = 1")
    suspend fun getEnabled(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: Long): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutomationEntity): Long

    @Update
    suspend fun update(entity: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE automations SET lastFiredAt = :lastFiredAt, lastSignature = :lastSignature, currentState = :currentState WHERE id = :id")
    suspend fun updateRuntimeState(id: Long, lastFiredAt: Long, lastSignature: String, currentState: Boolean)

    @Query("DELETE FROM automations")
    suspend fun deleteAll()
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name ASC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY name ASC")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: Long): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocationEntity): Long

    @Delete
    suspend fun delete(entity: LocationEntity)

    @Query("DELETE FROM locations")
    suspend fun deleteAll()
}

@Dao
interface LogDao {
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ExecutionLogEntity>

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionLogEntity>>

    @Insert
    suspend fun insert(entity: ExecutionLogEntity): Long

    @Query("DELETE FROM execution_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM execution_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM execution_logs WHERE automationId = :automationId")
    suspend fun deleteForAutomation(automationId: Long)

    @Query("DELETE FROM execution_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM execution_logs")
    suspend fun count(): Int
}

@Dao
interface VariableDao {
    @Query("SELECT * FROM variables ORDER BY key ASC")
    fun observeAll(): Flow<List<VariableEntity>>

    @Query("SELECT * FROM variables ORDER BY key ASC")
    suspend fun getAll(): List<VariableEntity>

    @Query("SELECT * FROM variables WHERE key = :key")
    suspend fun get(key: String): VariableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VariableEntity)

    @Query("DELETE FROM variables WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM variables")
    suspend fun deleteAll()
}