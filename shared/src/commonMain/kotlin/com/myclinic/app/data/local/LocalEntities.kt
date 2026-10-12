package com.myclinic.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row of any record table, cached on the phone as the JSON the server
 * sends. Keeping one generic table (instead of one per section) keeps the
 * offline layer small; the typed models live in core/domain.
 */
@Entity(
    tableName = "cached_rows",
    primaryKeys = ["tableName", "id"],
    indices = [Index("patientId")],
)
data class CachedRowEntity(
    val tableName: String,
    val id: String,
    val patientId: String,
    val json: String,
    val updatedAt: String?,
    /** True once the server has this row (pulled from it, or uploaded successfully). */
    val onServer: Boolean,
)

/** A local change waiting to be uploaded ("outbox"), applied in [opId] order. */
@Entity(tableName = "pending_ops", indices = [Index("tableName", "rowId")])
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val tableName: String,
    val rowId: String,
    val patientId: String,
    val json: String,
    val createdAtMillis: Long,
    /** Set when the server refused the change (not a network problem); shown to the user. */
    val failed: Boolean = false,
    val lastError: String? = null,
)

/** Small key/value store for sync cursors and the owning user id. */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
