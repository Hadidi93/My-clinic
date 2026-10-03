package com.myclinic.app.ui.patients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.ui.navigation.PatientDetailRoute
import com.myclinic.domain.model.PatientRef
import com.myclinic.domain.permissions.AccessPolicy
import com.myclinic.domain.record.Dates
import com.myclinic.domain.record.PatientRecord
import com.myclinic.domain.record.TimelineBuilder
import com.myclinic.domain.record.TimelineEvent
import com.myclinic.domain.record.LabTrends
import com.myclinic.domain.record.VitalsSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PatientDetailUiState(
    val loading: Boolean = true,
    val record: PatientRecord? = null,
    val ageYears: Int? = null,
    /** Primary doctor: may delete/restore (co-managers may only edit entries). */
    val isOwner: Boolean = false,
    val timeline: List<TimelineEvent> = emptyList(),
    /** Vital signs and lab tests that have values, for the trends tab. */
    val trends: List<Trend> = emptyList(),
    val selectedTrend: Trend? = null,
)

@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PatientRepository,
    doctorRepository: DoctorRepository,
) : ViewModel() {

    val patientId: String = savedStateHandle.toRoute<PatientDetailRoute>().patientId
    private val selectedTrend = MutableStateFlow<Trend?>(null)

    val state: StateFlow<PatientDetailUiState> = combine(
        repository.record(patientId),
        doctorRepository.myProfile,
        selectedTrend,
    ) { record, me, selected ->
        if (record == null) return@combine PatientDetailUiState(loading = false)
        val trends = VitalsSeries.available(record.examinations).map { Trend.Vital(it) } +
            LabTrends.available(record.investigationResults).map { Trend.Lab(it) }
        PatientDetailUiState(
            loading = false,
            record = record,
            ageYears = Dates.age(record.patient.dateOfBirth, record.patient.ageYears, LocalDate.now()),
            isOwner = me != null && AccessPolicy.canManagePatient(
                me.id, PatientRef(record.patient.id, record.patient.ownerId, record.patient.isDeleted),
            ),
            timeline = TimelineBuilder.build(record),
            trends = trends,
            selectedTrend = selected?.takeIf { it in trends } ?: trends.firstOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientDetailUiState())

    init {
        repository.logView(patientId) // audit: who opened which record, and when
    }

    fun selectTrend(trend: Trend) {
        selectedTrend.value = trend
    }

    fun setDeleted(deleted: Boolean) {
        viewModelScope.launch { repository.setPatientDeleted(patientId, deleted) }
    }
}
