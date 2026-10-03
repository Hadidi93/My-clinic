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
import kotlinx.coroutines.flow.map

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

    override suspend fun updateMyProfile(
        input: ProfileInput,
        language: String,
        accountType: com.myclinic.domain.model.AccountType,
        facilityId: String?,
        grade: String?,
    ): Result<Doctor> {
        stored = stored!!.copy(
            fullName = input.fullName, specialty = input.specialty, hospital = input.hospital,
            licenseNumber = input.licenseNumber, phone = input.phone, preferredLanguage = language,
            accountType = accountType, facilityId = facilityId, grade = grade,
        )
        return refreshMyProfile()
    }

    override suspend fun uploadMyPhoto(jpegBytes: ByteArray) = refreshMyProfile()
    override suspend fun uploadMyLicenseDocument(jpegBytes: ByteArray) = refreshMyProfile()
    override suspend fun photoUrl(path: String): String? = null
    override suspend fun licenseDocumentUrl(path: String): String? = null
    override suspend fun doctorsAwaitingVerification() = Result.success(emptyList<Doctor>())
    override suspend fun setVerification(doctorId: String, status: VerificationStatus, note: String?) = Result.success(Unit)
    override suspend fun reviewGrade(doctorId: String, approve: Boolean) = Result.success(Unit)
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
    override fun record(patientId: String): kotlinx.coroutines.flow.Flow<com.myclinic.domain.record.PatientRecord?> =
        records.map { list -> list.firstOrNull { it.patient.id == patientId } }
    override suspend fun row(table: com.myclinic.domain.record.RecordTable, id: String): kotlinx.serialization.json.JsonObject? = null
    override suspend fun save(table: com.myclinic.domain.record.RecordTable, patientId: String?, rowId: String?, values: Map<String, String>): String {
        saved += table to values
        return rowId ?: "new-id"
    }
    val changes = mutableListOf<Triple<com.myclinic.domain.record.RecordTable, String?, kotlinx.serialization.json.JsonObject>>()
    override suspend fun saveChanges(
        table: com.myclinic.domain.record.RecordTable,
        patientId: String,
        rowId: String?,
        changes: kotlinx.serialization.json.JsonObject,
    ): String {
        this.changes += Triple(table, rowId, changes)
        return rowId ?: "new-id"
    }
    val attached = mutableListOf<com.myclinic.app.data.files.PickedFile>()
    override suspend fun addAttachment(
        patientId: String,
        section: com.myclinic.domain.model.RecordSection,
        resultId: String?,
        followupId: String?,
        file: com.myclinic.app.data.files.PickedFile,
    ): String {
        attached += file
        return "attachment-id"
    }
    override suspend fun loadFile(storagePath: String) = ByteArray(0)
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

class FakeFacilityRepository : com.myclinic.app.data.facilities.FacilityRepository {
    override val facilities = MutableStateFlow(
        listOf(com.myclinic.domain.record.Facility("f1", "Demo Main Lab", "lab", "Demo Hospital")),
    )
    override suspend fun refresh() = Result.success(facilities.value)
    override suspend fun add(name: String, kind: String, hospital: String?) =
        Result.success(com.myclinic.domain.record.Facility("f-new", name, kind, hospital)).also { r ->
            facilities.value = facilities.value + r.getOrThrow()
        }
}

class FakeNotificationRepository : com.myclinic.app.data.notifications.NotificationRepository {
    var registered = 0
    var unregistered = 0
    override val notifications = MutableStateFlow(emptyList<com.myclinic.domain.consult.AppNotification>())
    override val unreadCount = MutableStateFlow(0)
    override suspend fun refresh() = Result.success(Unit)
    override suspend fun markRead(ids: Collection<String>) = Unit
    override suspend fun markAllRead() = Unit
    override suspend fun registerDevice() { registered++ }
    override suspend fun unregisterDevice() { unregistered++ }
    override fun clear() { notifications.value = emptyList() }
    override val pushReceived = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    override val openRequest = MutableStateFlow<String?>(null)
    override fun onOpenHandled() { openRequest.value = null }
}
