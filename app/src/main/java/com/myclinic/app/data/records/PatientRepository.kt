package com.myclinic.app.data.records

import com.myclinic.app.data.files.ClinicalFileStore
import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.data.local.CachedRowEntity
import com.myclinic.app.data.local.LocalDao
import com.myclinic.app.data.local.PendingOpEntity
import com.myclinic.app.data.sync.SyncEngine
import com.myclinic.app.data.sync.SyncScheduler
import com.myclinic.app.data.sync.SyncStatus
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.FormSpecs
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.CachedRow
import com.myclinic.domain.record.PatientRecord
import com.myclinic.domain.record.PatientRecordAssembler
import com.myclinic.domain.record.RecordJson
import com.myclinic.domain.record.RecordTable
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Patient records for the screens. Reads come from the encrypted offline
 * cache (instant, works without internet). Writes go to the cache and the
 * outbox at once, then a background sync uploads them.
 */
interface PatientRepository {
    /** Every cached patient (including deleted ones, for the trash). */
    val records: Flow<List<PatientRecord>>
    fun record(patientId: String): Flow<PatientRecord?>

    /** The stored row, for filling an edit form. */
    suspend fun row(table: RecordTable, id: String): JsonObject?

    /** Creates or updates a row from form values. Returns the row id. */
    suspend fun save(table: RecordTable, patientId: String?, rowId: String?, values: Map<String, String>): String

    /**
     * Saves [changes] over the stored row (or a new row), for changes that
     * don't come from a form: a request's status, a new attachment. Returns the row id.
     */
    suspend fun saveChanges(table: RecordTable, patientId: String, rowId: String?, changes: JsonObject): String

    /** Attaches a photo/PDF to a result or a post-op follow-up. Works offline: the file uploads with the next sync. */
    suspend fun addAttachment(patientId: String, section: RecordSection, resultId: String?, followupId: String?, file: PickedFile): String

    /** The bytes of an attached file (from the phone if not uploaded yet). */
    suspend fun loadFile(storagePath: String): ByteArray

    /** "Delete" = mark as entered in error. The row stays in the medical record. */
    suspend fun markDeleted(table: RecordTable, id: String)
    suspend fun setPatientDeleted(patientId: String, deleted: Boolean)

    val pendingChanges: Flow<Int>
    val failedChanges: Flow<Int>
    val syncStatus: Flow<SyncStatus>
    suspend fun discardFailedChanges()

    /** Records in the audit log that this doctor opened the patient's record (when online). */
    fun logView(patientId: String)

    /** Starts background sync (now, and every 15 minutes). */
    fun startSync()

    /** Unsynced local changes that would be lost by signing out. */
    suspend fun unsyncedChangeCount(): Int

    /** Deletes the offline copy and stops syncing (on sign-out). */
    suspend fun clearLocalData()
}

@Singleton
class CachedPatientRepository @Inject constructor(
    private val dao: LocalDao,
    private val supabase: SupabaseClient,
    private val scheduler: SyncScheduler,
    private val engine: SyncEngine,
    private val files: ClinicalFileStore,
) : PatientRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val records: Flow<List<PatientRecord>> = dao.observeAllRows()
        .map { rows -> rows.groupBy { it.patientId }.values.mapNotNull { assemble(it) } }
        .flowOn(Dispatchers.Default)

    override fun record(patientId: String): Flow<PatientRecord?> = dao.observePatientRows(patientId)
        .map { assemble(it) }
        .flowOn(Dispatchers.Default)

    private fun assemble(rows: List<CachedRowEntity>): PatientRecord? {
        val patientRow = rows.firstOrNull { it.tableName == RecordTable.PATIENTS.tableName } ?: return null
        val cached = rows.mapNotNull { r ->
            RecordTable.fromTableName(r.tableName)
                ?.takeIf { it != RecordTable.PATIENTS }
                ?.let { t -> parse(r.json)?.let { CachedRow(t, it) } }
        }
        return parse(patientRow.json)?.let { PatientRecordAssembler.assemble(it, cached) }
    }

    override suspend fun row(table: RecordTable, id: String): JsonObject? =
        dao.getRow(table.tableName, id)?.json?.let(::parse)

    override suspend fun save(table: RecordTable, patientId: String?, rowId: String?, values: Map<String, String>): String {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val existing = rowId?.let { dao.getRow(table.tableName, it) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val ownerPatientId = if (table == RecordTable.PATIENTS) id else requireNotNull(patientId)
        val now = Instant.now().toString()

        val base: JsonObject = existing?.json?.let(::parse) ?: buildJsonObject {
            put("id", id)
            if (table == RecordTable.PATIENTS) put("owner_id", userId) else {
                put("patient_id", ownerPatientId)
                put("created_by", userId)
            }
            put("created_at", now)
            put("deleted_at", JsonNull)
        }
        val row = FormCodec.toJson(FormSpecs.forTable(table), values, base).withLocalUpdatedAt(now)
        write(table, id, ownerPatientId, row, onServer = existing?.onServer == true)
        return id
    }

    override suspend fun saveChanges(table: RecordTable, patientId: String, rowId: String?, changes: JsonObject): String {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val existing = rowId?.let { dao.getRow(table.tableName, it) }
        val id = existing?.id ?: rowId ?: UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val base: JsonObject = existing?.json?.let(::parse) ?: buildJsonObject {
            put("id", id)
            put("patient_id", patientId)
            put("created_by", userId)
            put("created_at", now)
            put("deleted_at", JsonNull)
        }
        write(table, id, patientId, JsonObject(base + changes).withLocalUpdatedAt(now), onServer = existing?.onServer == true)
        return id
    }

    override suspend fun addAttachment(
        patientId: String,
        section: RecordSection,
        resultId: String?,
        followupId: String?,
        file: PickedFile,
    ): String {
        val path = files.newPath(section.dbValue, patientId, file.mimeType)
        files.stage(path, file.bytes)
        return saveChanges(
            RecordTable.ATTACHMENTS, patientId, null,
            buildJsonObject {
                put("section", section.dbValue)
                put("result_id", resultId)
                put("followup_id", followupId)
                put("storage_path", path)
                put("mime_type", file.mimeType)
                put("file_name", file.fileName)
                put("caption", JsonNull)
                put("taken_at", Instant.now().toString())
            },
        )
    }

    override suspend fun loadFile(storagePath: String): ByteArray = files.load(storagePath)

    override suspend fun markDeleted(table: RecordTable, id: String) = setDeletedAt(table, id, Instant.now().toString())

    override suspend fun setPatientDeleted(patientId: String, deleted: Boolean) =
        setDeletedAt(RecordTable.PATIENTS, patientId, if (deleted) Instant.now().toString() else null)

    private suspend fun setDeletedAt(table: RecordTable, id: String, value: String?) {
        val existing = dao.getRow(table.tableName, id) ?: return
        val json = parse(existing.json) ?: return
        val updated = JsonObject(json + ("deleted_at" to (value?.let { JsonPrimitive(it) } ?: JsonNull)))
            .withLocalUpdatedAt(Instant.now().toString())
        write(table, id, existing.patientId, updated, existing.onServer)
    }

    private suspend fun write(table: RecordTable, id: String, patientId: String, row: JsonObject, onServer: Boolean) {
        val text = row.toString()
        dao.saveLocalChange(
            CachedRowEntity(table.tableName, id, patientId, text, row["updated_at"]?.toString()?.trim('"'), onServer),
            PendingOpEntity(tableName = table.tableName, rowId = id, patientId = patientId, json = text,
                createdAtMillis = System.currentTimeMillis()),
        )
        scheduler.requestSync()
    }

    override val pendingChanges: Flow<Int> = dao.observePendingCount()
    override val failedChanges: Flow<Int> = dao.observeFailedCount()
    override val syncStatus: Flow<SyncStatus> = engine.status
    override suspend fun discardFailedChanges() = dao.deleteFailedOps()

    override fun logView(patientId: String) {
        scope.launch {
            runCatching {
                supabase.postgrest.rpc(
                    "log_record_view",
                    buildJsonObject {
                        put("p_patient_id", patientId)
                        put("p_section", RecordSection.IDENTIFIERS.dbValue)
                    },
                )
            } // offline or not yet uploaded: skip (the database logs every change anyway)
        }
    }

    override fun startSync() {
        scheduler.schedulePeriodic()
        scheduler.requestSync()
    }

    override suspend fun unsyncedChangeCount(): Int = dao.countAllOps()

    override suspend fun clearLocalData() {
        scheduler.cancelAll()
        engine.clearLocalData()
    }

    private fun parse(text: String): JsonObject? = runCatching { RecordJson.parseToJsonElement(text).jsonObject }.getOrNull()

    /** Local timestamp so the lists sort sensibly before the server sets the real one. */
    private fun JsonObject.withLocalUpdatedAt(now: String) = JsonObject(this + ("updated_at" to JsonPrimitive(now)))
}
