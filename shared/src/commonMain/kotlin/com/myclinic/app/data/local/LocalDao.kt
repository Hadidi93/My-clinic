package com.myclinic.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocalDao {

    // ---- cached rows ----
    @Query("SELECT * FROM cached_rows")
    abstract fun observeAllRows(): Flow<List<CachedRowEntity>>

    @Query("SELECT * FROM cached_rows WHERE patientId = :patientId")
    abstract fun observePatientRows(patientId: String): Flow<List<CachedRowEntity>>

    @Query("SELECT * FROM cached_rows WHERE tableName = :table AND id = :id")
    abstract suspend fun getRow(table: String, id: String): CachedRowEntity?

    @Upsert
    abstract suspend fun upsertRows(rows: List<CachedRowEntity>)

    @Query("UPDATE cached_rows SET onServer = 1 WHERE tableName = :table AND id = :id")
    abstract suspend fun markOnServer(table: String, id: String)

    @Query("SELECT DISTINCT patientId FROM cached_rows WHERE onServer = 1")
    abstract suspend fun syncedPatientIds(): List<String>

    @Query("DELETE FROM cached_rows WHERE patientId = :patientId")
    abstract suspend fun deletePatientRows(patientId: String)

    // ---- outbox ----
    @Insert
    abstract suspend fun insertOp(op: PendingOpEntity): Long

    @Query("SELECT * FROM pending_ops WHERE failed = 0 ORDER BY opId LIMIT :limit")
    abstract suspend fun nextOps(limit: Int): List<PendingOpEntity>

    @Query("DELETE FROM pending_ops WHERE opId = :opId")
    abstract suspend fun deleteOp(opId: Long)

    @Query("UPDATE pending_ops SET failed = 1, lastError = :error WHERE opId = :opId")
    abstract suspend fun markOpFailed(opId: Long, error: String)

    @Query("SELECT COUNT(*) FROM pending_ops WHERE tableName = :table AND rowId = :id")
    abstract suspend fun pendingOpsFor(table: String, id: String): Int

    @Query("SELECT COUNT(*) FROM pending_ops WHERE patientId = :patientId")
    abstract suspend fun pendingOpsForPatient(patientId: String): Int

    @Query("SELECT COUNT(*) FROM pending_ops WHERE failed = 0")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_ops WHERE failed = 1")
    abstract fun observeFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_ops")
    abstract suspend fun countAllOps(): Int

    @Query("DELETE FROM pending_ops WHERE failed = 1")
    abstract suspend fun deleteFailedOps()

    // ---- meta ----
    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setMeta(meta: SyncMetaEntity)

    // ---- wipe ----
    @Query("DELETE FROM cached_rows")
    abstract suspend fun clearRows()

    @Query("DELETE FROM pending_ops")
    abstract suspend fun clearOps()

    @Query("DELETE FROM sync_meta")
    abstract suspend fun clearMeta()

    @Transaction
    open suspend fun clearEverything() {
        clearRows()
        clearOps()
        clearMeta()
    }

    /** Saves a local edit and queues it for upload in one step, so neither can happen without the other. */
    @Transaction
    open suspend fun saveLocalChange(row: CachedRowEntity, op: PendingOpEntity) {
        upsertRows(listOf(row))
        insertOp(op)
    }
}
