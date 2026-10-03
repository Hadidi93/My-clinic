package com.myclinic.app.ui.staff

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.data.files.PickedFileReader
import com.myclinic.app.data.staff.StaffRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.FullScreenError
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.files.AttachFileButtons
import com.myclinic.app.ui.files.FileItem
import com.myclinic.app.ui.files.FileList
import com.myclinic.app.ui.navigation.StaffRequestRoute
import com.myclinic.app.ui.patients.AllergyBanner
import com.myclinic.app.ui.patients.FieldContext
import com.myclinic.app.ui.patients.FieldEditor
import com.myclinic.app.ui.patients.LabValuesTable
import com.myclinic.app.ui.patients.formatDate
import com.myclinic.app.ui.patients.formatDateTime
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.app.ui.patients.sexAgeLine
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.FormSpec
import com.myclinic.domain.forms.FormSpecs
import com.myclinic.domain.record.Allergy
import com.myclinic.domain.record.InvestigationStatus
import com.myclinic.domain.record.LabCatalog
import com.myclinic.domain.record.StaffRequestDetail
import com.myclinic.domain.record.StaffResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The result form staff fill in: the same fields as the doctor's result form, minus the request picker and type. */
private val STAFF_RESULT_FORM = FormSpec(
    FormSpecs.INVESTIGATION_RESULT.table,
    FormSpecs.INVESTIGATION_RESULT.fields.filter { it.key in setOf("title", "result_date", "lab_values", "report_text") },
)

data class StaffRequestUiState(
    val loading: Boolean = true,
    val detail: StaffRequestDetail? = null,
    val loadError: DataError? = null,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, FieldError> = emptyMap(),
    val files: List<PickedFile> = emptyList(),
    val readingFile: Boolean = false,
    val working: Boolean = false,
    val nothingEntered: Boolean = false,
    val error: DataError? = null,
    /** Result sent: go back to the inbox. */
    val finished: Boolean = false,
)

@HiltViewModel
class StaffRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StaffRepository,
    private val fileReader: PickedFileReader,
) : ViewModel() {

    private val requestId = savedStateHandle.toRoute<StaffRequestRoute>().requestId
    private val _state = MutableStateFlow(StaffRequestUiState())
    val state: StateFlow<StaffRequestUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            repository.detail(requestId)
                .onSuccess { d ->
                    _state.update { s ->
                        s.copy(
                            loading = false,
                            detail = d,
                            // Start with the requested tests (only the first time).
                            values = s.values.ifEmpty {
                                FormCodec.newValues(STAFF_RESULT_FORM) + mapOf(
                                    "title" to d.tests.joinToString(", ").take(200),
                                    "lab_values" to if (d.kind == "lab") FormCodec.labValuesText(LabCatalog.startingValues(d.tests)) else "",
                                )
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.toDataError()) } }
        }
    }

    fun onValue(key: String, value: String) = _state.update {
        it.copy(values = it.values + (key to value), errors = it.errors - key, nothingEntered = false)
    }

    fun onImage(uri: Uri, fromCamera: Boolean) = readFile { fileReader.image(uri, deleteOriginal = fromCamera) }
    fun onPdf(uri: Uri) = readFile { fileReader.pdf(uri) }

    private fun readFile(read: suspend () -> PickedFile) {
        _state.update { it.copy(readingFile = true, error = null) }
        viewModelScope.launch {
            try {
                val f = read()
                _state.update { it.copy(readingFile = false, files = it.files + f, nothingEntered = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(readingFile = false, error = e.toDataError()) }
            }
        }
    }

    fun removeFile(index: Int) = _state.update { it.copy(files = it.files.filterIndexed { i, _ -> i != index }) }

    fun markSampleTaken() {
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            repository.markSampleTaken(requestId)
                .onSuccess { _state.update { it.copy(working = false) }; load() }
                .onFailure { e -> _state.update { it.copy(working = false, error = e.toDataError()) } }
        }
    }

    fun submit() {
        val s = _state.value
        val detail = s.detail ?: return
        val errors = FormCodec.validate(STAFF_RESULT_FORM, s.values, LocalDate.now())
        val values = FormCodec.parseLabValues(s.values["lab_values"].orEmpty()).orEmpty()
        val nothing = s.values["report_text"].isNullOrBlank() && values.isEmpty() && s.files.isEmpty()
        if (errors.isNotEmpty() || nothing) {
            _state.update { it.copy(errors = errors, nothingEntered = nothing) }
            return
        }
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            repository.submitResult(
                detail,
                title = s.values["title"].orEmpty(),
                resultDate = s.values["result_date"].orEmpty(),
                reportText = s.values["report_text"].orEmpty(),
                values = values,
                files = s.files,
            )
                .onSuccess { _state.update { it.copy(working = false, finished = true) } }
                .onFailure { e -> _state.update { it.copy(working = false, error = e.toDataError()) } }
        }
    }
}

/**
 * One request for lab/radiology staff: who it is for, allergies, earlier
 * results of the same kind, and the form to send the result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffRequestScreen(onBack: () -> Unit, viewModel: StaffRequestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.investigation_request_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            val detail = state.detail
            when {
                state.loading && detail == null -> FullScreenLoading()
                detail == null -> Column(Modifier.padding(padding)) {
                    // Also shown when the doctor has already reviewed or cancelled the request.
                    FullScreenError(state.loadError?.message() ?: stringResource(R.string.error_unknown), onRetry = viewModel::load)
                }
                else -> Column(Modifier.fillMaxSize().padding(padding)) {
                    AllergyBanner(detail.allergies.mapIndexed { i, a ->
                        Allergy(id = i.toString(), patientId = detail.patientId, allergen = a.allergen, reaction = a.reaction, severity = a.severity)
                    })
                    if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Column(
                        Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().imePadding()
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RequestHeader(detail)
                        if (detail.status == InvestigationStatus.REQUESTED) {
                            SecondaryButton(stringResource(R.string.mark_sample_taken), onClick = viewModel::markSampleTaken,
                                enabled = !state.working)
                        }

                        if (detail.thisRequestResults.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.staff_already_sent))
                            detail.thisRequestResults.forEach { ResultCard(it) }
                        }

                        SectionTitle(stringResource(R.string.staff_send_result))
                        STAFF_RESULT_FORM.fields.forEach { field ->
                            FieldEditor(
                                field = field, values = state.values, error = state.errors[field.key], context = FieldContext(),
                                onValue = viewModel::onValue, onValues = { it.forEach { (k, v) -> viewModel.onValue(k, v) } },
                            )
                        }
                        Text(stringResource(R.string.files_title), style = MaterialTheme.typography.titleMedium)
                        val photo = stringResource(R.string.file_photo)
                        FileList(
                            items = state.files.mapIndexed { i, f -> FileItem(i.toString(), f.fileName ?: photo, f.isPdf) },
                            onOpen = null,
                            onRemove = { viewModel.removeFile(it.key.toInt()) },
                        )
                        if (state.readingFile) LinearProgressIndicator(Modifier.fillMaxWidth())
                        AttachFileButtons(onImage = viewModel::onImage, onPdf = viewModel::onPdf, enabled = !state.readingFile)
                        if (state.nothingEntered) ErrorMessage(stringResource(R.string.staff_nothing_entered))
                        state.error?.let { ErrorMessage(it.message()) }
                        PrimaryButton(stringResource(R.string.staff_submit), onClick = viewModel::submit, loading = state.working,
                            enabled = !state.readingFile)

                        if (detail.previousResults.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.staff_previous_results))
                            detail.previousResults.forEach { ResultCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun RequestHeader(d: StaffRequestDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(d.patient.fullName, style = MaterialTheme.typography.titleLarge)
        sexAgeLine(d.patient.sex ?: "unknown", d.patient.ageYears, d.patient.fileNumber).takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(d.tests.joinToString(", "), style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(optionLabel(d.kind), optionLabel(d.urgency), optionLabel(d.status)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = if (d.urgency != "routine") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(listOfNotNull(d.requestedBy, formatDateTime(d.requestedAt)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        d.clinicalNotes?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.clinical_notes_label, it), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultCard(r: StaffResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleSmall)
            formatDate(r.resultDate)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (r.labValues.isNotEmpty()) LabValuesTable(r.labValues)
            r.reportText?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
