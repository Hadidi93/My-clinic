package com.myclinic.app.data.sync

import com.myclinic.app.data.local.CachedRowEntity
import com.myclinic.app.data.local.LocalDao
import com.myclinic.app.data.local.SyncMetaEntity
import com.myclinic.domain.record.RecordJson
import com.myclinic.domain.record.RecordTable
import com.myclinic.domain.record.forUpload
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val running: Boolean = false,
    val offline: Boolean = false,
    val lastSyncedAt: Instant? = null,
)

enum class SyncOutcome { SUCCESS, NOT_SIGNED_IN, NETWORK_ERROR, ERROR }

/**
 * Keeps the encrypted offline copy in step with the server:
 *  1. PUSH  local changes (the outbox) in the order they were made.
 *  2. PULL  rows changed on the server since the last sync, page by page.
 *  3. PURGE patients this doctor can no longer edit (co-management ended,
 *           transferred away), so their data doesn't stay on the phone.
 *
 * Conflicts: if a row has an un-uploaded local change, the local version
 * wins until it is uploaded; otherwise the server version wins.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val supabase: SupabaseClient,
    private val dao: LocalDao,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    suspend fun syncNow(): SyncOutcome = mutex.withLock {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return SyncOutcome.NOT_SIGNED_IN
        _status.update { it.copy(running = true) }
        try {
            ensureCacheBelongsTo(userId)
            push()
            pull()
            purge()
            _status.update { it.copy(running = false, offline = false, lastSyncedAt = Instant.now()) }
            SyncOutcome.SUCCESS
        } catch (e: CancellationException) {
            _status.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            val network = e.isNetworkError()
            _status.update { it.copy(running = false, offline = network) }
            if (network) SyncOutcome.NETWORK_ERROR else SyncOutcome.ERROR
        }
    }

    /** If a different doctor signs in on this phone, the previous doctor's cache is wiped first. */
    private suspend fun ensureCacheBelongsTo(userId: String) {
        val owner = dao.meta(META_OWNER)
        if (owner != null && owner != userId) dao.clearEverything()
        if (owner != userId) dao.setMeta(SyncMetaEntity(META_OWNER, userId))
    }

    private suspend fun push() {
        while (true) {
            val ops = dao.nextOps(50)
            if (ops.isEmpty()) return
            for (op in ops) {
                val row = RecordJson.parseToJsonElement(op.json).jsonObject
                val onServer = dao.getRow(op.tableName, op.rowId)?.onServer == true
                try {
                    if (onServer) update(op.tableName, op.rowId, row) else insertOrUpdate(op.tableName, op.rowId, row)
                    dao.deleteOp(op.opId)
                    dao.markOnServer(op.tableName, op.rowId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.isNetworkError()) throw e
                    // The server refused this change (e.g. access was removed).
                    // Keep it aside and tell the user rather than retrying forever.
                    dao.markOpFailed(op.opId, e.message.orEmpty().take(300))
                }
            }
        }
    }

    private suspend fun insertOrUpdate(table: String, id: String, row: JsonObject) {
        try {
            supabase.from(table).insert(row.forUpload())
        } catch (e: Exception) {
            // Already there (e.g. an earlier upload succeeded but the reply was lost).
            if (e.message.orEmpty().contains("23505") || e.message.orEmpty().contains("duplicate key")) {
                update(table, id, row)
            } else {
                throw e
            }
        }
    }

    private suspend fun update(table: String, id: String, row: JsonObject) {
        val payload = JsonObject(row.filterKeys { it !in NOT_UPDATABLE })
        supabase.from(table).update(payload) { filter { eq("id", id) } }
    }

    private suspend fun pull() {
        for (table in RecordTable.entries) {
            val cursorKey = "cursor:${table.tableName}"
            // Re-read the last two minutes each time: rows committed slightly
            // out of order are then never missed (re-applying is harmless).
            var since: String? = dao.meta(cursorKey)?.let { Instant.parse(it).minusSeconds(120).toString() }
            var afterId = ZERO_UUID
            var newest: String? = null
            while (true) {
                val result = supabase.postgrest.rpc(
                    "sync_pull",
                    buildJsonObject {
                        put("p_table", table.tableName)
                        put("p_since", since)
                        put("p_after_id", afterId)
                        put("p_limit", PAGE_SIZE)
                    },
                )
                val rows = (RecordJson.parseToJsonElement(result.data) as? JsonArray).orEmpty().map { it.jsonObject }
                apply(table, rows)
                val last = rows.lastOrNull() ?: break
                since = last.text("updated_at")
                afterId = last.text("id") ?: break
                newest = since
                if (rows.size < PAGE_SIZE) break
            }
            newest?.let { value ->
                runCatching { Instant.parse(normalizeInstant(value)) }.getOrNull()?.let {
                    dao.setMeta(SyncMetaEntity(cursorKey, it.toString()))
                }
            }
        }
    }

    private suspend fun apply(table: RecordTable, rows: List<JsonObject>) {
        val toStore = mutableListOf<CachedRowEntity>()
        for (row in rows) {
            val id = row.text("id") ?: continue
            val patientId = (if (table == RecordTable.PATIENTS) id else row.text("patient_id")) ?: continue
            if (dao.pendingOpsFor(table.tableName, id) > 0) {
                dao.markOnServer(table.tableName, id) // local edit wins until uploaded
                continue
            }
            toStore += CachedRowEntity(table.tableName, id, patientId, row.toString(), row.text("updated_at"), onServer = true)
        }
        if (toStore.isNotEmpty()) dao.upsertRows(toStore)
    }

    private suspend fun purge() {
        val result = supabase.postgrest.rpc("my_patient_ids")
        val allowed = (RecordJson.parseToJsonElement(result.data) as? JsonArray)
            .orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        for (patientId in dao.syncedPatientIds()) {
            if (patientId !in allowed && dao.pendingOpsForPatient(patientId) == 0) {
                dao.deletePatientRows(patientId)
            }
        }
    }

    suspend fun clearLocalData() = mutex.withLock { dao.clearEverything() }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Postgres writes "+00:00"; java.time.Instant wants "Z". */
    private fun normalizeInstant(value: String): String =
        java.time.OffsetDateTime.parse(value).toInstant().toString()

    private companion object {
        const val META_OWNER = "owner_user_id"
        const val PAGE_SIZE = 500
        const val ZERO_UUID = "00000000-0000-0000-0000-000000000000"
        /** Columns never sent in an update (fixed at creation, or set by the server). */
        val NOT_UPDATABLE = setOf("id", "owner_id", "created_by", "created_at", "updated_at")
    }
}

fun Throwable.isNetworkError(): Boolean =
    this is HttpRequestException || this is HttpRequestTimeoutException || this is IOException ||
        cause?.let { it !== this && it.isNetworkError() } == true
