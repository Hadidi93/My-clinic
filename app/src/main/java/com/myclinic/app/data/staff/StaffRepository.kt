package com.myclinic.app.data.staff

import com.myclinic.app.data.files.ClinicalFileStore
import com.myclinic.app.data.files.PickedFile
import com.myclinic.domain.record.LabValue
import com.myclinic.domain.record.RecordJson
import com.myclinic.domain.record.StaffRequestDetail
import com.myclinic.domain.record.WorklistItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The lab/radiology side. Everything goes through the staff_* database
 * functions, which only show requests sent to the staff member's own
 * department, and only until the doctor has reviewed the result.
 * Nothing is stored on the phone (online only).
 */
interface StaffRepository {
    suspend fun worklist(): Result<List<WorklistItem>>
    suspend fun detail(requestId: String): Result<StaffRequestDetail>
    suspend fun markSampleTaken(requestId: String): Result<Unit>
    suspend fun submitResult(
        request: StaffRequestDetail,
        title: String,
        resultDate: String,
        reportText: String,
        values: List<LabValue>,
        files: List<PickedFile>,
    ): Result<Unit>
}

@Singleton
class SupabaseStaffRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val fileStore: ClinicalFileStore,
) : StaffRepository {

    override suspend fun worklist(): Result<List<WorklistItem>> = call {
        val result = supabase.postgrest.rpc("staff_worklist")
        RecordJson.decodeFromString(ListSerializer(WorklistItem.serializer()), result.data)
    }

    override suspend fun detail(requestId: String): Result<StaffRequestDetail> = call {
        val result = supabase.postgrest.rpc("staff_request_detail", buildJsonObject { put("p_request_id", requestId) })
        RecordJson.decodeFromString(StaffRequestDetail.serializer(), result.data)
    }

    override suspend fun markSampleTaken(requestId: String): Result<Unit> = call {
        supabase.postgrest.rpc("staff_mark_sample_taken", buildJsonObject { put("p_request_id", requestId) })
        Unit
    }

    override suspend fun submitResult(
        request: StaffRequestDetail,
        title: String,
        resultDate: String,
        reportText: String,
        values: List<LabValue>,
        files: List<PickedFile>,
    ): Result<Unit> = call {
        // Files first (allowed while the request is open), then the result that refers to them.
        val uploaded = files.map { f ->
            val path = fileStore.newPath("investigations", request.patientId, f.mimeType)
            fileStore.upload(path, f.bytes, f.mimeType)
            buildJsonObject {
                put("storage_path", path)
                put("mime_type", f.mimeType)
                put("file_name", f.fileName)
            }
        }
        supabase.postgrest.rpc(
            "staff_submit_result",
            buildJsonObject {
                put("p_request_id", request.id)
                put("p_title", title.trim())
                put("p_result_date", resultDate.ifBlank { null })
                put("p_report_text", reportText.trim())
                put("p_lab_values", RecordJson.encodeToJsonElement(ListSerializer(LabValue.serializer()), values.filter { it.test.isNotBlank() }))
                put("p_files", buildJsonArray { uploaded.forEach { add(it) } })
            },
        )
        Unit
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
