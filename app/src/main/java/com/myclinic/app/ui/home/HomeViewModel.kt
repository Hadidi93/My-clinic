package com.myclinic.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.notifications.NotificationRepository
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.domain.consult.NotificationKind
import com.myclinic.domain.record.Dashboard
import com.myclinic.domain.record.InvestigationCounts
import com.myclinic.domain.record.InvestigationDashboard
import com.myclinic.domain.record.ResultToReview
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
    val investigations: InvestigationCounts = InvestigationCounts(0, 0),
    val resultsToReview: List<ResultToReview> = emptyList(),
    val pendingChanges: Int = 0,
    val failedChanges: Int = 0,
    val offline: Boolean = false,
    val unreadNotifications: Int = 0,
    val unreadConsults: Int = 0,
    val unreadReferrals: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PatientRepository,
    private val notifications: NotificationRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        repository.records,
        repository.pendingChanges,
        repository.failedChanges,
        repository.syncStatus,
        notifications.notifications,
    ) { records, pending, failed, sync, alerts ->
        val unread = alerts.filterNot { it.isRead }
        val today = LocalDate.now()
        val live = records.filterNot { it.patient.isDeleted }
        HomeUiState(
            loading = false,
            todaysPatients = Dashboard.todaysPatients(live.map { PatientSummaries.from(it, today) }, today).size,
            totalPatients = live.size,
            upcomingOperations = Dashboard.upcomingOperations(live, today),
            investigations = InvestigationDashboard.counts(live),
            resultsToReview = InvestigationDashboard.toReview(live),
            pendingChanges = pending,
            failedChanges = failed,
            offline = sync.offline,
            unreadNotifications = unread.size,
            unreadConsults = unread.count { NotificationKind.isConsult(it.kind) },
            unreadReferrals = unread.count { NotificationKind.isReferral(it.kind) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Called when Home is shown: new notifications may have arrived. */
    fun refreshNotifications() {
        viewModelScope.launch { notifications.refresh() }
    }

    fun discardFailedChanges() {
        viewModelScope.launch { repository.discardFailedChanges() }
    }
}
