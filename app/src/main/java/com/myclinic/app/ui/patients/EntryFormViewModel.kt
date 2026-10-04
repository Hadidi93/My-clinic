package com.myclinic.app.ui.patients

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myclinic.app.data.DataError
import com.myclinic.app.data.facilities.FacilityRepository
import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.data.files.PickedFileReader
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.navigation.EntryFormRoute
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.FormSpec
import com.myclinic.domain.forms.FormSpecs
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.Allergy
import com.myclinic.domain.record.Attachment
import com.myclinic.domain.record.Facility
import com.myclinic.domain.record.InvestigationRequest
import com.myclinic.domain.record.InvestigationResult
import com.myclinic.domain.record.InvestigationRules
import com.myclinic.domain.record.LabCatalog
import com.myclinic.domain.record.RecordTable
import com.myclinic.domain.record.SurgicalCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    /** Requests a result can be filed under. */
    val openRequests: List<InvestigationRequest> = emptyList(),
    val facilities: List<Facility> = emptyList(),
    /** Files already attached to this entry. */
    val attachments: List<Attachment> = emptyList(),
    /** Files picked in this form, attached when it is saved. */
    val newFiles: List<PickedFile> = emptyList(),
    val readingFile: Boolean = false,
    /** A result uploaded by the lab: shown read-only (it can only be marked "entered in error"). */
    val departmentResult: InvestigationResult? = null,
    val error: DataError? = null,
    /** Entries saved with "Save and add another" while this form was open. */
    val addedInSession: List<Map<String, String>> = emptyList(),
    /** Changes each time the form is cleared for the next entry (resets the field editors). */
    val formRound: Int = 0,
) {
    val hasChanges: Boolean get() = !loading && (values != initialValues || newFiles.isNotEmpty())
}

/** Add or edit one entry of any record section; the fields come from [FormSpecs]. */
@HiltViewModel
class EntryFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PatientRepository,
    private val facilityRepository: FacilityRepository,
    private val fileReader: PickedFileReader,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntryFormRoute>()
    val patientId: String = route.patientId
    val table: RecordTable = requireNotNull(RecordTable.fromTableName(route.table))
    val entryId: String? = route.entryId
    val spec: FormSpec = FormSpecs.forTable(table)
    val isNew: Boolean get() = entryId == null

    /** History lists where several entries are usually added in a row (conditions, operations, drugs...). */
    val canAddAnother: Boolean = isNew && table in MULTI_ENTRY_TABLES

    /** Results and post-op follow-ups can have photos/PDFs attached. */
    val canAttachFiles: Boolean = table == RecordTable.INVESTIGATION_RESULTS || table == RecordTable.POSTOP_FOLLOWUPS
    private val fileSection = if (table == RecordTable.POSTOP_FOLLOWUPS) RecordSection.SURGICAL_CARE else RecordSection.INVESTIGATIONS

    private val _state = MutableStateFlow(EntryFormUiState())
    val state: StateFlow<EntryFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val values = entryId?.let { repository.row(table, it) }?.let { FormCodec.fromJson(spec, it) }
                ?: FormCodec.newValues(spec)
            _state.update { it.copy(loading = false, values = values, initialValues = values) }
            // "Add result" from a request: start with that request's tests.
            val request = route.requestId?.takeIf { isNew }?.let { id ->
                repository.record(patientId).first()?.investigationRequests?.firstOrNull { it.id == id }
            }
            if (request != null) {
                onRequestPicked(request)
                _state.update { it.copy(initialValues = it.values) }
            }
        }
        viewModelScope.launch {
            // Keep the allergy banner, pickers and attachments current.
            repository.record(patientId).collect { record ->
                _state.update { s ->
                    s.copy(
                        allergies = record?.allergies.orEmpty(),
                        surgicalCases = record?.surgicalCases.orEmpty(),
                        openRequests = record?.investigationRequests.orEmpty().filter(InvestigationRules::canAddResult),
                        attachments = entryId?.let { id ->
                            if (table == RecordTable.POSTOP_FOLLOWUPS) record?.attachmentsForFollowup(id) else record?.attachmentsForResult(id)
                        }.orEmpty(),
                        departmentResult = record?.investigationResults
                            ?.firstOrNull { it.id == entryId && !InvestigationRules.canEditResult(it) },
                    )
                }
            }
        }
        if (table == RecordTable.INVESTIGATION_REQUESTS) {
            viewModelScope.launch { facilityRepository.facilities.collect { list -> _state.update { it.copy(facilities = list) } } }
            viewModelScope.launch { facilityRepository.refresh() } // offline: the last loaded list is used
        }
    }

    fun onValue(key: String, value: String) = _state.update {
        it.copy(values = it.values + (key to value), errors = it.errors - key)
    }

    /** For the condition picker, which sets the name and the code together. */
    fun onValues(changes: Map<String, String>) = _state.update {
        it.copy(values = it.values + changes, errors = it.errors - changes.keys)
    }

    /** Filing a result under a request fills in what is still empty: type, title and the requested tests. */
    fun onRequestPicked(request: InvestigationRequest?) {
        if (request == null) return
        val v = _state.value.values
        val changes = buildMap {
            put("request_id", request.id)
            put("kind", request.kind)
            if (v["title"].isNullOrBlank()) put("title", request.tests.joinToString(", ").take(200))
            if (v["lab_values"].isNullOrBlank() && request.kind == "lab") {
                put("lab_values", FormCodec.labValuesText(LabCatalog.startingValues(request.tests)))
            }
        }
        onValues(changes)
    }

    fun addFacility(name: String, kind: String, hospital: String) {
        viewModelScope.launch {
            facilityRepository.add(name, kind, hospital)
                .onSuccess { onValue("facility_id", it.id) }
                .onFailure { e -> _state.update { it.copy(error = e.toDataError()) } }
        }
    }

    fun onImage(uri: Uri, fromCamera: Boolean) = readFile { fileReader.image(uri, deleteOriginal = fromCamera) }
    fun onPdf(uri: Uri) = readFile { fileReader.pdf(uri) }

    private fun readFile(read: suspend () -> PickedFile) {
        _state.update { it.copy(readingFile = true, error = null) }
        viewModelScope.launch {
            try {
                val file = read()
                _state.update { it.copy(readingFile = false, newFiles = it.newFiles + file) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(readingFile = false, error = e.toDataError()) }
            }
        }
    }

    fun removeNewFile(index: Int) = _state.update { it.copy(newFiles = it.newFiles.filterIndexed { i, _ -> i != index }) }

    /** Removing an attached file marks it "entered in error" (it stays in the record history). */
    fun removeAttachment(id: String) {
        viewModelScope.launch { repository.markDeleted(RecordTable.ATTACHMENTS, id) }
    }

    fun save() = saveEntry(addAnother = false)

    /** Saves this entry and clears the form for the next one, without leaving the screen. */
    fun saveAndAddAnother() = saveEntry(addAnother = true)

    private fun saveEntry(addAnother: Boolean) {
        val s = _state.value
        // "Done" after adding several: nothing new was typed, so just close.
        if (!addAnother && s.addedInSession.isNotEmpty() && !s.hasChanges) {
            _state.update { it.copy(finished = true) }
            return
        }
        val errors = FormCodec.validate(spec, s.values)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = repository.save(table, patientId, entryId, s.values)
            s.newFiles.forEach { file ->
                repository.addAttachment(
                    patientId, fileSection,
                    resultId = id.takeIf { table == RecordTable.INVESTIGATION_RESULTS },
                    followupId = id.takeIf { table == RecordTable.POSTOP_FOLLOWUPS },
                    file = file,
                )
            }
            if (addAnother) {
                val fresh = FormCodec.newValues(spec)
                _state.update {
                    it.copy(
                        saving = false, values = fresh, initialValues = fresh, errors = emptyMap(), newFiles = emptyList(),
                        addedInSession = it.addedInSession + listOf(s.values), formRound = it.formRound + 1,
                    )
                }
            } else {
                _state.update { it.copy(saving = false, finished = true) }
            }
        }
    }

    fun markDeleted() {
        val id = entryId ?: return
        viewModelScope.launch {
            repository.markDeleted(table, id)
            _state.update { it.copy(finished = true) }
        }
    }

    private companion object {
        val MULTI_ENTRY_TABLES = setOf(
            RecordTable.PRESENTING_COMPLAINTS, RecordTable.MEDICAL_CONDITIONS, RecordTable.SURGICAL_HISTORY,
            RecordTable.MEDICATIONS, RecordTable.ALLERGIES, RecordTable.FAMILY_HISTORY,
        )
    }
}
