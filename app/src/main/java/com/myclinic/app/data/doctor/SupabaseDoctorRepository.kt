package com.myclinic.app.data.doctor

import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.VerificationStatus
import com.myclinic.domain.validation.ProfileInput
import com.myclinic.domain.validation.ProfileValidator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class SupabaseDoctorRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : DoctorRepository {

    private val profile = MutableStateFlow<Doctor?>(null)
    override val myProfile: StateFlow<Doctor?> = profile.asStateFlow()

    private fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not signed in")

    override suspend fun refreshMyProfile(): Result<Doctor> = call {
        val doctor = supabase.from(TABLE)
            .select { filter { eq("id", currentUserId()) } }
            .decodeSingle<DoctorDto>()
            .toDomain()
        profile.value = doctor
        doctor
    }

    override suspend fun updateMyProfile(input: ProfileInput, language: String): Result<Doctor> {
        val update = DoctorProfileUpdate(
            fullName = input.fullName.trim(),
            specialty = input.specialty.trim(),
            hospital = input.hospital.trim(),
            licenseNumber = ProfileValidator.cleanLicense(input.licenseNumber),
            phone = ProfileValidator.cleanPhone(input.phone),
            preferredLanguage = language,
        )
        return call { supabase.from(TABLE).update(update) { filter { eq("id", currentUserId()) } } }
            .mapCatching { refreshMyProfile().getOrThrow() }
    }

    override suspend fun uploadMyPhoto(jpegBytes: ByteArray): Result<Doctor> {
        return call {
            val path = "${currentUserId()}/avatar.jpg"
            supabase.storage.from(AVATARS).upload(path, jpegBytes) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
            supabase.from(TABLE).update(DoctorPhotoUpdate(path)) { filter { eq("id", currentUserId()) } }
        }.mapCatching { refreshMyProfile().getOrThrow() }
    }

    override suspend fun uploadMyLicenseDocument(jpegBytes: ByteArray): Result<Doctor> {
        return call {
            // A new file name each time, so the path changes and the database
            // sends the account back for re-verification.
            val path = "${currentUserId()}/license-${System.currentTimeMillis()}.jpg"
            supabase.storage.from(LICENSES).upload(path, jpegBytes) {
                contentType = ContentType.Image.JPEG
            }
            supabase.from(TABLE).update(DoctorLicenseDocumentUpdate(path)) { filter { eq("id", currentUserId()) } }
        }.mapCatching { refreshMyProfile().getOrThrow() }
    }

    override suspend fun photoUrl(path: String): String? =
        runCatching { supabase.storage.from(AVATARS).createSignedUrl(path, 10.minutes) }.getOrNull()

    override suspend fun licenseDocumentUrl(path: String): String? =
        runCatching { supabase.storage.from(LICENSES).createSignedUrl(path, 5.minutes) }.getOrNull()

    override suspend fun doctorsAwaitingVerification(): Result<List<Doctor>> = call {
        supabase.from(TABLE)
            .select {
                filter { eq("verification_status", VerificationStatus.PENDING.dbValue) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<DoctorDto>()
            .map { it.toDomain() }
    }

    override suspend fun setVerification(doctorId: String, status: VerificationStatus, note: String?): Result<Unit> =
        call {
            supabase.postgrest.rpc(
                "admin_set_verification",
                VerificationRequest(doctorId, status.dbValue, note?.trim()?.takeIf { it.isNotEmpty() }),
            )
            Unit
        }

    override fun clear() {
        profile.value = null
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
        const val TABLE = "doctors"
        const val AVATARS = "avatars"
        const val LICENSES = "licenses"
    }
}
