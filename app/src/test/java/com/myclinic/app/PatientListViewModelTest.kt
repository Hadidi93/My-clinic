package com.myclinic.app

import com.myclinic.app.ui.patients.PatientListViewModel
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.record.DateFilter
import com.myclinic.domain.record.Patient
import com.myclinic.domain.record.PatientRecord
import com.myclinic.domain.record.RecordTable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatientListViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakePatientRepository()
    // Created lazily, i.e. after MainDispatcherRule has installed the test main
    // thread: this ViewModel starts collecting data as soon as it is built.
    private val vm by lazy { PatientListViewModel(repo) }

    @Test
    fun `quick add needs a name and saves without other fields`() {
        vm.openQuickAdd()
        vm.saveQuickAdd(openAfter = false)
        assertEquals(FieldError.REQUIRED, vm.quickAdd.value.errors["full_name"])
        assertTrue(repo.saved.isEmpty())

        vm.onQuickValue("full_name", "Demo Quick Patient")
        vm.saveQuickAdd(openAfter = false)
        assertEquals(RecordTable.PATIENTS, repo.saved.single().first)
        assertEquals("Demo Quick Patient", repo.saved.single().second["full_name"])
        assertFalse(vm.quickAdd.value.open)
    }

    @Test
    fun `search filters the list`() = runTest {
        repo.records.value = listOf(
            PatientRecord(Patient(id = "1", ownerId = "me", fullName = "Demo Alpha")),
            PatientRecord(Patient(id = "2", ownerId = "me", fullName = "Demo Beta")),
        )
        // Keep the state flow collected, as the screen would.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.onQueryText("beta")
        advanceUntilIdle()
        assertEquals(listOf("2"), vm.state.value.results.map { it.patient.id })
        assertEquals(2, vm.state.value.totalPatients)
    }

    @Test
    fun `initial options are applied only once`() {
        vm.initialize(DateFilter.TODAY, startWithQuickAdd = true)
        vm.closeQuickAdd()
        vm.initialize(DateFilter.TODAY, startWithQuickAdd = true) // e.g. returning from a patient
        assertFalse(vm.quickAdd.value.open)
    }
}
