package com.myclinic.app.data.audit

import com.myclinic.domain.record.AuditEntry
import com.myclinic.domain.record.AuditJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Access logs (online only). The server decides who may see which log. */
interface AuditRepository {
    /** Who opened, changed, shared or exported this patient's record. Main doctor or admin. */
    suspend fun patientLog(patientId: String): Result<List<AuditEntry>>
    /** Everything in the app, newest first; [before] pages back in time. Admin only. */
    suspend fun activityLog(before: String? = null): Result<List<AuditEntry>>
    /** Must succeed before a PDF of the record is made. */
    suspend fun logExport(patientId: String): Result<Unit>
}

@Singleton
class SupabaseAuditRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : AuditRepository {

    override suspend fun patientLog(patientId: String) = call {
        val r = supabase.postgrest.rpc("patient_access_log", buildJsonObject { put("p_patient_id", patientId) })
        AuditJson.decodeFromString(ListSerializer(AuditEntry.serializer()), r.data)
    }

    override suspend fun activityLog(before: String?) = call {
        val r = supabase.postgrest.rpc("admin_activity_log", buildJsonObject {
            put("p_limit", PAGE)
            put("p_before", before)
        })
        AuditJson.decodeFromString(ListSerializer(AuditEntry.serializer()), r.data)
    }

    override suspend fun logExport(patientId: String) = call {
        supabase.postgrest.rpc("log_record_export", buildJsonObject { put("p_patient_id", patientId) })
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

    companion object {
        const val PAGE = 200
    }
}
