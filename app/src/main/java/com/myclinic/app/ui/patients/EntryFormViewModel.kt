package com.myclinic.app.ui.patients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.ui.navigation.EntryFormRoute
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.FormSpec
import com.myclinic.domain.forms.FormSpecs
import com.myclinic.domain.record.Allergy
import com.myclinic.domain.record.RecordTable
import com.myclinic.domain.record.SurgicalCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryFormUiState(
    val loading: Boolean = true,
    val values: Map<String, String> = emptyMap(),
    val initialValues: Map<String, String> = emptyMap(),
    val errors: Map<String, FieldError> = emptyMap(),
    val saving: Boolean = false,
    /** Set when saved or deleted: the screen closes. */
    val finished: Boolean = false,
    val allergies: List<Allergy> = emptyList(),
    val surgicalCases: List<SurgicalCase> = emptyList(),
) {
    val hasChanges: Boolean get() = !loading && values != initialValues
}

/** Add or edit one entry of any record section; the fields come from [FormSpecs]. */
@HiltViewModel
class EntryFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PatientRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntryFormRoute>()
    val patientId: String = route.patientId
    val table: RecordTable = requireNotNull(RecordTable.fromTableName(route.table))
    val entryId: String? = route.entryId
    val spec: FormSpec = FormSpecs.forTable(table)
    val isNew: Boolean get() = entryId == null

    private val _state = MutableStateFlow(EntryFormUiState())
    val state: StateFlow<EntryFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val values = entryId?.let { repository.row(table, it) }?.let { FormCodec.fromJson(spec, it) }
                ?: FormCodec.newValues(spec)
            _state.update { it.copy(loading = false, values = values, initialValues = values) }
        }
        viewModelScope.launch {
            // Keep the allergy banner and the operation picker current.
            repository.record(patientId).collect { record ->
                _state.update { it.copy(allergies = record?.allergies.orEmpty(), surgicalCases = record?.surgicalCases.orEmpty()) }
            }
        }
    }

    fun onValue(key: String, value: String) = _state.update {
        it.copy(values = it.values + (key to value), errors = it.errors - key)
    }

    /** For the condition picker, which sets the name and the code together. */
    fun onValues(changes: Map<String, String>) = _state.update {
        it.copy(values = it.values + changes, errors = it.errors - changes.keys)
    }

    fun save() {
        val s = _state.value
        val errors = FormCodec.validate(spec, s.values)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.save(table, patientId, entryId, s.values)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun markDeleted() {
        val id = entryId ?: return
        viewModelScope.launch {
            repository.markDeleted(table, id)
            _state.update { it.copy(finished = true) }
        }
    }
}
