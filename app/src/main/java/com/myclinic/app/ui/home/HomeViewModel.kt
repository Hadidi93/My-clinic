package com.myclinic.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.domain.record.Dashboard
import com.myclinic.domain.record.PatientSummaries
import com.myclinic.domain.record.UpcomingOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val todaysPatients: Int = 0,
    val totalPatients: Int = 0,
    val upcomingOperations: List<UpcomingOperation> = emptyList(),
    val pendingChanges: Int = 0,
    val failedChanges: Int = 0,
    val offline: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PatientRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repository.records,
        repository.pendingChanges,
        repository.failedChanges,
        repository.syncStatus,
    ) { records, pending, failed, sync ->
        val today = LocalDate.now()
        val live = records.filterNot { it.patient.isDeleted }
        HomeUiState(
            loading = false,
            todaysPatients = Dashboard.todaysPatients(live.map { PatientSummaries.from(it, today) }, today).size,
            totalPatients = live.size,
            upcomingOperations = Dashboard.upcomingOperations(live, today),
            pendingChanges = pending,
            failedChanges = failed,
            offline = sync.offline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun discardFailedChanges() {
        viewModelScope.launch { repository.discardFailedChanges() }
    }
}
