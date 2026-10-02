package com.myclinic.app

import android.content.Intent
import com.myclinic.app.data.auth.AuthFailure
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.data.auth.AuthState
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.domain.error.AuthError
import com.myclinic.domain.model.AppRole
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.VerificationStatus
import com.myclinic.domain.validation.ProfileInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory stand-ins for the Supabase repositories, for ViewModel tests. Demo data only. */
class FakeAuthRepository : AuthRepository {
    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState = state
    override val passwordRecoveryPending = MutableStateFlow(false)
    override val linkMessage = MutableStateFlow<com.myclinic.app.data.auth.LinkMessage?>(null)
    override fun clearLinkMessage() { linkMessage.value = null }

    var signInError: AuthError? = null
    val signInCalls = mutableListOf<Pair<String, String>>()

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signInCalls += email to password
        return signInError?.let { Result.failure(AuthFailure(it)) }
            ?: Result.success(Unit).also { state.value = AuthState.SignedIn("demo-id", email) }
    }

    override suspend fun signUp(fullName: String, email: String, password: String, language: String) = Result.success(Unit)
    override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
    override suspend fun updatePassword(newPassword: String) = Result.success(Unit)
    override suspend fun signOut() { state.value = AuthState.SignedOut }
    override fun handleDeepLink(intent: Intent?) = Unit
}

class FakeDoctorRepository(var stored: Doctor? = null) : DoctorRepository {
    private val profile = MutableStateFlow<Doctor?>(null)
    override val myProfile: StateFlow<Doctor?> = profile
    var failRefresh = false

    override suspend fun refreshMyProfile(): Result<Doctor> {
        if (failRefresh) return Result.failure(RuntimeException("offline"))
        val d = stored ?: return Result.failure(RuntimeException("missing"))
        profile.value = d
        return Result.success(d)
    }

    override suspend fun updateMyProfile(input: ProfileInput, language: String): Result<Doctor> {
        stored = stored!!.copy(
            fullName = input.fullName, specialty = input.specialty, hospital = input.hospital,
            licenseNumber = input.licenseNumber, phone = input.phone, preferredLanguage = language,
        )
        return refreshMyProfile()
    }

    override suspend fun uploadMyPhoto(jpegBytes: ByteArray) = refreshMyProfile()
    override suspend fun uploadMyLicenseDocument(jpegBytes: ByteArray) = refreshMyProfile()
    override suspend fun photoUrl(path: String): String? = null
    override suspend fun licenseDocumentUrl(path: String): String? = null
    override suspend fun doctorsAwaitingVerification() = Result.success(emptyList<Doctor>())
    override suspend fun setVerification(doctorId: String, status: VerificationStatus, note: String?) = Result.success(Unit)
    override fun clear() { profile.value = null }
}

fun demoDoctor(complete: Boolean = true) = Doctor(
    id = "demo-id",
    email = "dr.demo@example.test",
    fullName = "Dr Demo",
    specialty = if (complete) "General Surgery" else null,
    hospital = if (complete) "Demo Hospital" else null,
    licenseNumber = if (complete) "LIC-0001" else null,
    phone = if (complete) "+201000000000" else null,
    photoPath = null,
    licenseDocumentPath = null,
    preferredLanguage = "en",
    role = AppRole.DOCTOR,
    verificationStatus = VerificationStatus.PENDING,
    verificationNote = null,
)

class FakePatientRepository : com.myclinic.app.data.records.PatientRepository {
    var cleared = 0
    var syncStarted = 0
    var unsynced = 0
    override val records = MutableStateFlow(emptyList<com.myclinic.domain.record.PatientRecord>())
    override fun record(patientId: String) = MutableStateFlow<com.myclinic.domain.record.PatientRecord?>(null)
    override suspend fun row(table: com.myclinic.domain.record.RecordTable, id: String): kotlinx.serialization.json.JsonObject? = null
    override suspend fun save(table: com.myclinic.domain.record.RecordTable, patientId: String?, rowId: String?, values: Map<String, String>): String {
        saved += table to values
        return rowId ?: "new-id"
    }
    override suspend fun markDeleted(table: com.myclinic.domain.record.RecordTable, id: String) = Unit
    override suspend fun setPatientDeleted(patientId: String, deleted: Boolean) = Unit
    override val pendingChanges = MutableStateFlow(0)
    override val failedChanges = MutableStateFlow(0)
    override val syncStatus = MutableStateFlow(com.myclinic.app.data.sync.SyncStatus())
    val saved = mutableListOf<Pair<com.myclinic.domain.record.RecordTable, Map<String, String>>>()
    override suspend fun discardFailedChanges() = Unit
    override fun logView(patientId: String) = Unit
    override fun startSync() { syncStarted++ }
    override suspend fun unsyncedChangeCount() = unsynced
    override suspend fun clearLocalData() { cleared++ }
}
