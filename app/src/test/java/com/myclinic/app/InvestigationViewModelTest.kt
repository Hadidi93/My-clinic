package com.myclinic.app

import androidx.lifecycle.SavedStateHandle
import com.myclinic.app.ui.patients.InvestigationViewModel
import com.myclinic.domain.record.InvestigationRequest
import com.myclinic.domain.record.InvestigationResult
import com.myclinic.domain.record.InvestigationStatus
import com.myclinic.domain.record.Patient
import com.myclinic.domain.record.PatientRecord
import com.myclinic.domain.record.RecordTable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The doctor's investigation request screen. Demo data only. */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestigationViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakePatientRepository().apply {
        records.value = listOf(
            PatientRecord(
                patient = Patient(id = "p1", ownerId = "demo-id", fullName = "Demo Patient"),
                investigationRequests = listOf(
                    InvestigationRequest(id = "r1", patientId = "p1", tests = listOf("CBC"), facilityId = "f1",
                        status = InvestigationStatus.RESULT_UPLOADED),
                ),
                investigationResults = listOf(
                    InvestigationResult(id = "x1", patientId = "p1", requestId = "r1", title = "CBC", source = "staff"),
                    InvestigationResult(id = "x2", patientId = "p1", title = "Other result"),
                ),
            ),
        )
    }

    private val vm by lazy {
        InvestigationViewModel(
            SavedStateHandle(mapOf("patientId" to "p1", "requestId" to "r1")),
            repo,
            FakeFacilityRepository(),
        )
    }

    @Test
    fun `shows the request with only its own results and its department`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.first { !it.loading }
        assertEquals("r1", state.request?.id)
        assertEquals(listOf("x1"), state.results.map { it.id })
        assertEquals("Demo Main Lab", state.facility?.name)
    }

    @Test
    fun `marking reviewed saves the new status for upload`() = runTest {
        vm.markReviewed()
        val (table, rowId, changes) = repo.changes.single()
        assertEquals(RecordTable.INVESTIGATION_REQUESTS, table)
        assertEquals("r1", rowId)
        assertEquals(JsonPrimitive(InvestigationStatus.REVIEWED), changes["status"])
    }
}
