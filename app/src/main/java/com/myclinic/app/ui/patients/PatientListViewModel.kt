package com.myclinic.app.ui.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.FormSpecs
import com.myclinic.domain.record.DateFilter
import com.myclinic.domain.record.PatientQuery
import com.myclinic.domain.record.PatientSearch
import com.myclinic.domain.record.PatientSummaries
import com.myclinic.domain.record.PatientSummary
import com.myclinic.domain.record.RecordTable
import com.myclinic.domain.record.StatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PatientListUiState(
    val loading: Boolean = true,
    val query: PatientQuery = PatientQuery(),
    val results: List<PatientSummary> = emptyList(),
    val totalPatients: Int = 0,
    val tagsInUse: List<String> = emptyList(),
    val pendingChanges: Int = 0,
    val failedChanges: Int = 0,
    val offline: Boolean = false,
)

data class QuickAddState(
    val open: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, FieldError> = emptyMap(),
    val saving: Boolean = false,
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val repository: PatientRepository,
) : ViewModel() {

    private val query = MutableStateFlow(PatientQuery())

    private val summaries = repository.records.map { records ->
        val today = LocalDate.now()
        records.map { PatientSummaries.from(it, today) }
    }

    private data class SyncInfo(val pending: Int, val failed: Int, val offline: Boolean)

    private val syncInfo = combine(repository.pendingChanges, repository.failedChanges, repository.syncStatus) { p, f, s ->
        SyncInfo(p, f, s.offline)
    }

    val state: StateFlow<PatientListUiState> = combine(summaries, query, syncInfo) { all, q, sync ->
        PatientListUiState(
            loading = false,
            query = q,
            results = PatientSearch.filter(all, q, LocalDate.now()),
            totalPatients = all.count { !it.patient.isDeleted },
            tagsInUse = PatientSearch.tagsInUse(all.filterNot { it.patient.isDeleted }),
            pendingChanges = sync.pending,
            failedChanges = sync.failed,
            offline = sync.offline,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientListUiState())

    fun onQueryText(text: String) = query.update { it.copy(text = text) }
    fun onDateFilter(filter: DateFilter) = query.update { it.copy(date = filter) }
    fun onToggleTag(tag: String) = query.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }
    fun onToggleDeleted() = query.update { it.copy(includeDeleted = !it.includeDeleted) }
    fun onStatus(status: StatusFilter) = query.update { it.copy(status = status) }

    private var initialized = false

    /** Applies the options the screen was opened with, once (not again when returning to it). */
    fun initialize(dateFilter: DateFilter, startWithQuickAdd: Boolean) {
        if (initialized) return
        initialized = true
        query.update { it.copy(date = dateFilter) }
        if (startWithQuickAdd) openQuickAdd()
    }

    fun discardFailedChanges() {
        viewModelScope.launch { repository.discardFailedChanges() }
    }

    // ---- Quick add ----

    private val _quickAdd = MutableStateFlow(QuickAddState())
    val quickAdd: StateFlow<QuickAddState> = _quickAdd.asStateFlow()

    /** Emits the new patient's id when "Save and open" succeeds. */
    private val _openPatient = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openPatient: SharedFlow<String> = _openPatient

    private val quickSpec = FormSpecs.PATIENT.copy(fields = FormSpecs.PATIENT.fields.filter { it.quick })

    fun openQuickAdd() = _quickAdd.update { QuickAddState(open = true, values = FormCodec.newValues(quickSpec)) }
    fun closeQuickAdd() = _quickAdd.update { QuickAddState() }
    fun onQuickValue(key: String, value: String) = _quickAdd.update {
        it.copy(values = it.values + (key to value), errors = it.errors - key)
    }

    fun saveQuickAdd(openAfter: Boolean) {
        val s = _quickAdd.value
        val errors = FormCodec.validate(quickSpec, s.values)
        if (errors.isNotEmpty()) {
            _quickAdd.update { it.copy(errors = errors) }
            return
        }
        _quickAdd.update { it.copy(saving = true) }
        viewModelScope.launch {
            // Save through the full form so fields not on quick-add are stored as empty.
            val id = repository.save(RecordTable.PATIENTS, null, null, FormCodec.newValues(FormSpecs.PATIENT) + s.values)
            _quickAdd.update { QuickAddState() }
            if (openAfter) _openPatient.tryEmit(id)
        }
    }
}
