package com.myclinic.app.data.consults

import com.myclinic.app.data.files.PickedFile
import com.myclinic.domain.consult.ConsultMessage
import com.myclinic.domain.consult.ConsultPatient
import com.myclinic.domain.consult.ConsultRules
import com.myclinic.domain.consult.ConsultSummary
import com.myclinic.domain.consult.DoctorCard
import com.myclinic.domain.consult.ReferralSummary
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.CachedRow
import com.myclinic.domain.record.PatientRecord
import com.myclinic.domain.record.PatientRecordAssembler
import com.myclinic.domain.record.RecordJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The answers on the "Ask a colleague" form. */
data class NewConsult(
    val patientId: String,
    val consultantId: String,
    val question: String,
    val sections: Set<RecordSection>,
    val anonymize: Boolean,
    val durationDays: Int,
    val urgency: String,
    val consentDate: LocalDate,
)

/**
 * Consults and referrals between doctors. Online only: nothing shared by a
 * colleague is ever stored on the phone, so access ends the moment it is
 * revoked, closed or expires.
 */
interface ConsultRepository {
    suspend fun searchDoctors(query: String): Result<List<DoctorCard>>

    suspend fun myConsults(): Result<List<ConsultSummary>>
    suspend fun createConsult(c: NewConsult): Result<String>
    suspend fun messages(consultId: String): Result<List<ConsultMessage>>
    suspend fun sendMessage(consultId: String, body: String, files: List<PickedFile>): Result<Unit>
    suspend fun revoke(consultId: String): Result<Unit>
    suspend fun close(consultId: String): Result<Unit>
    /** For the consultant: who the patient is (masked as the requester chose). */
    suspend fun consultPatient(consultId: String): Result<ConsultPatient>
    /** For the consultant: the shared sections of the record, read live from the server. */
    suspend fun sharedRecord(consult: ConsultSummary, patient: ConsultPatient): Result<PatientRecord>
    suspend fun loadConsultFile(path: String): ByteArray

    suspend fun myReferrals(): Result<List<ReferralSummary>>
    suspend fun createReferral(patientId: String, toDoctorId: String, kind: String, note: String, consentDate: LocalDate): Result<String>
    suspend fun respondReferral(referralId: String, accept: Boolean): Result<Unit>
    suspend fun endReferral(referralId: String): Result<Unit>

    val myUserId: String?
}

@Singleton
class SupabaseConsultRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : ConsultRepository {

    override val myUserId: String? get() = supabase.auth.currentUserOrNull()?.id

    override suspend fun searchDoctors(query: String) = call {
        val result = supabase.postgrest.rpc("search_doctors", buildJsonObject { put("p_query", query.trim()) })
        RecordJson.decodeFromString(ListSerializer(DoctorCard.serializer()), result.data)
    }

    override suspend fun myConsults() = call {
        RecordJson.decodeFromString(ListSerializer(ConsultSummary.serializer()), supabase.postgrest.rpc("my_consults").data)
    }

    override suspend fun createConsult(c: NewConsult) = call {
        val result = supabase.postgrest.rpc(
            "create_consult",
            buildJsonObject {
                put("p_patient_id", c.patientId)
                put("p_consultant_id", c.consultantId)
                put("p_question", c.question.trim())
                put("p_sections", buildJsonArray { c.sections.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.dbValue)) } })
                put("p_anonymize", c.anonymize)
                put("p_duration_days", c.durationDays)
                put("p_consent_confirmed", true)
                put("p_consent_date", c.consentDate.toString())
                put("p_urgency", c.urgency)
            },
        )
        result.data.trim('"')
    }

    override suspend fun messages(consultId: String) = call {
        val result = supabase.from("consult_messages").select {
            filter { eq("consult_id", consultId) }
            order("created_at", Order.ASCENDING)
        }
        RecordJson.decodeFromString(ListSerializer(ConsultMessage.serializer()), result.data)
    }

    override suspend fun sendMessage(consultId: String, body: String, files: List<PickedFile>) = call {
        // Files first (allowed while the consult is open), then the message pointing to them.
        val paths = files.map { f ->
            val ext = if (f.isPdf) "pdf" else "jpg"
            val path = "$consultId/${UUID.randomUUID()}.$ext"
            supabase.storage.from(CONSULT_FILES).upload(path, f.bytes) {
                upsert = false
                contentType = ContentType.parse(f.mimeType)
            }
            path
        }
        supabase.from("consult_messages").insert(
            buildJsonObject {
                put("consult_id", consultId)
                put("body", body.trim())
                put("attachment_paths", buildJsonArray { paths.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            },
        )
        Unit
    }

    override suspend fun revoke(consultId: String) = rpcUnit("revoke_consult", consultId)
    override suspend fun close(consultId: String) = rpcUnit("close_consult", consultId)

    private suspend fun rpcUnit(name: String, consultId: String) = call {
        supabase.postgrest.rpc(name, buildJsonObject { put("p_consult_id", consultId) })
        Unit
    }

    override suspend fun consultPatient(consultId: String) = call {
        val result = supabase.postgrest.rpc("get_consult_patient", buildJsonObject { put("p_consult_id", consultId) })
        RecordJson.decodeFromString(ConsultPatient.serializer(), result.data)
    }

    override suspend fun sharedRecord(consult: ConsultSummary, patient: ConsultPatient) = call {
        // The server returns only rows of the shared sections (row-level security);
        // asking only for those tables just saves time.
        val rows = ConsultRules.tablesFor(consult.sharedSections).flatMap { table ->
            val result = supabase.from(table.tableName).select { filter { eq("patient_id", consult.patientId) } }
            (RecordJson.parseToJsonElement(result.data) as JsonArray).map { CachedRow(table, it.jsonObject) }
        }
        // Audit trail: which sections the consultant opened, and when.
        consult.sharedSections.forEach { section ->
            runCatching {
                supabase.postgrest.rpc("log_record_view", buildJsonObject {
                    put("p_patient_id", consult.patientId)
                    put("p_section", section.dbValue)
                })
            }
        }
        val patientJson = buildJsonObject {
            put("id", consult.patientId)
            put("owner_id", "")
            put("full_name", patient.fullName ?: "")
            put("sex", patient.sex)
            put("age_years", patient.ageYears)
            put("file_number", patient.fileNumber)
        }
        PatientRecordAssembler.assemble(patientJson, rows) ?: error("Could not read the shared record")
    }

    override suspend fun loadConsultFile(path: String): ByteArray =
        supabase.storage.from(CONSULT_FILES).downloadAuthenticated(path)

    override suspend fun myReferrals() = call {
        RecordJson.decodeFromString(ListSerializer(ReferralSummary.serializer()), supabase.postgrest.rpc("my_referrals").data)
    }

    override suspend fun createReferral(patientId: String, toDoctorId: String, kind: String, note: String, consentDate: LocalDate) = call {
        val result = supabase.postgrest.rpc(
            "create_referral",
            buildJsonObject {
                put("p_patient_id", patientId)
                put("p_to_doctor_id", toDoctorId)
                put("p_kind", kind)
                put("p_note", note.trim())
                put("p_consent_confirmed", true)
                put("p_consent_date", consentDate.toString())
            },
        )
        result.data.trim('"')
    }

    override suspend fun respondReferral(referralId: String, accept: Boolean) = call {
        supabase.postgrest.rpc("respond_referral", buildJsonObject {
            put("p_referral_id", referralId)
            put("p_accept", accept)
        })
        Unit
    }

    override suspend fun endReferral(referralId: String) = call {
        supabase.postgrest.rpc("end_referral", buildJsonObject { put("p_referral_id", referralId) })
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

    private companion object {
        const val CONSULT_FILES = "consult-files"
    }
}

