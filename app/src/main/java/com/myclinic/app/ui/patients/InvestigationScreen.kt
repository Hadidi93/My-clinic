package com.myclinic.app.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.myclinic.app.R
import com.myclinic.app.data.facilities.FacilityRepository
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.files.FileItem
import com.myclinic.app.ui.files.FileList
import com.myclinic.app.ui.navigation.InvestigationRoute
import com.myclinic.domain.record.Allergy
import com.myclinic.domain.record.Attachment
import com.myclinic.domain.record.Facility
import com.myclinic.domain.record.InvestigationRequest
import com.myclinic.domain.record.InvestigationResult
import com.myclinic.domain.record.InvestigationRules
import com.myclinic.domain.record.InvestigationStatus
import com.myclinic.domain.record.RecordTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject

data class InvestigationUiState(
    val loading: Boolean = true,
    val request: InvestigationRequest? = null,
    val results: List<InvestigationResult> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val allergies: List<Allergy> = emptyList(),
    val facility: Facility? = null,
    val editable: Boolean = false,
)

@HiltViewModel
class InvestigationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PatientRepository,
    facilityRepository: FacilityRepository,
) : ViewModel() {

    // The InvestigationRoute arguments (read by name, so unit tests can supply them directly).
    val patientId: String = checkNotNull(savedStateHandle[InvestigationRoute::patientId.name])
    val requestId: String = checkNotNull(savedStateHandle[InvestigationRoute::requestId.name])

    val state: StateFlow<InvestigationUiState> = combine(
        repository.record(patientId),
        facilityRepository.facilities,
    ) { record, facilities ->
        if (record == null) return@combine InvestigationUiState(loading = false)
        val request = record.investigationRequests.firstOrNull { it.id == requestId }
            ?: return@combine InvestigationUiState(loading = false)
        InvestigationUiState(
            loading = false,
            request = request,
            results = record.resultsFor(request),
            attachments = record.attachments,
            allergies = record.allergies,
            facility = facilities.firstOrNull { it.id == request.facilityId },
            editable = !record.patient.isDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvestigationUiState())

    init {
        viewModelScope.launch { facilityRepository.refresh() }
    }

    fun markReviewed() = setStatus(InvestigationStatus.REVIEWED)
    fun cancel() = setStatus(InvestigationStatus.CANCELLED)

    private fun setStatus(status: String) {
        viewModelScope.launch {
            repository.saveChanges(
                RecordTable.INVESTIGATION_REQUESTS, patientId, requestId,
                buildJsonObject {
                    put("status", status)
                    // Shown straight away; the server records the real time and reviewer.
                    if (status == InvestigationStatus.REVIEWED) put("reviewed_at", Instant.now().toString())
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestigationScreen(
    onBack: () -> Unit,
    onEdit: (table: RecordTable, entryId: String?, requestId: String?) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    viewModel: InvestigationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    val request = state.request

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
            when {
                state.loading -> FullScreenLoading()
                request == null -> Text(stringResource(R.string.patient_not_found), Modifier.padding(padding).padding(24.dp))
                else -> Column(Modifier.fillMaxSize().padding(padding)) {
                    AllergyBanner(state.allergies)
                    Column(
                        Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RequestSummary(request, state.facility)
                        StatusSteps(request)

                        if (state.editable) {
                            if (InvestigationRules.canReview(request)) {
                                PrimaryButton(stringResource(R.string.mark_reviewed), onClick = viewModel::markReviewed)
                            }
                            if (InvestigationRules.canAddResult(request)) {
                                SecondaryButton(stringResource(R.string.add_result),
                                    onClick = { onEdit(RecordTable.INVESTIGATION_RESULTS, null, request.id) })
                            }
                            if (InvestigationRules.canEditRequest(request)) {
                                SecondaryButton(stringResource(R.string.edit_request),
                                    onClick = { onEdit(RecordTable.INVESTIGATION_REQUESTS, request.id, null) })
                            }
                            if (InvestigationRules.canCancel(request)) {
                                TextButton(onClick = { confirmCancel = true }) { Text(stringResource(R.string.cancel_request)) }
                            }
                        }

                        Text(stringResource(R.string.results_title), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        if (state.results.isEmpty()) {
                            Text(stringResource(R.string.no_results_yet), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.results.forEach { result ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ResultDetails(result, state.attachments.filter { it.resultId == result.id }, onOpenFile)
                                    if (state.editable) {
                                        TextButton(onClick = { onEdit(RecordTable.INVESTIGATION_RESULTS, result.id, null) }) {
                                            Text(stringResource(if (InvestigationRules.canEditResult(result)) R.string.edit else R.string.open))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (confirmCancel) {
            AlertDialog(
                onDismissRequest = { confirmCancel = false },
                title = { Text(stringResource(R.string.cancel_request)) },
                text = { Text(stringResource(R.string.cancel_request_confirm)) },
                confirmButton = { TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) { Text(stringResource(R.string.confirm)) } },
                dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.keep_request)) } },
            )
        }
    }
}

@Composable
private fun RequestSummary(request: InvestigationRequest, facility: Facility?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(request.tests.joinToString(", "), style = MaterialTheme.typography.titleLarge)
        Text(listOfNotNull(optionLabel(request.kind), optionLabel(request.urgency)).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = if (request.urgency != "routine") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        StatusChip(request.status)
        Text(
            facility?.let { stringResource(R.string.sent_to, it.label) }
                ?: if (request.facilityId == null) stringResource(R.string.handled_by_doctor) else stringResource(R.string.sent_to_department),
            style = MaterialTheme.typography.bodyMedium,
        )
        request.clinicalNotes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Requested → sample taken → result ready → reviewed, with the time of each step. */
@Composable
private fun StatusSteps(request: InvestigationRequest) {
    if (request.status == InvestigationStatus.CANCELLED) return
    val steps = listOf(
        InvestigationStatus.REQUESTED to request.requestedAt,
        InvestigationStatus.SAMPLE_TAKEN to request.sampleTakenAt,
        InvestigationStatus.RESULT_UPLOADED to request.resultedAt,
        InvestigationStatus.REVIEWED to request.reviewedAt,
    )
    val reached = InvestigationStatus.ALL.indexOf(request.status)
    Column {
        steps.forEachIndexed { i, (status, at) ->
            val done = i <= reached || at != null
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                Text(optionLabel(status).orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                formatDateTime(at)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    AssistChip(onClick = {}, label = { Text(optionLabel(status).orEmpty()) })
}

/** One result: title, date, who uploaded it, typed values, report and files. */
@Composable
fun ResultDetails(result: InvestigationResult, attachments: List<Attachment>, onOpenFile: (Attachment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(result.title, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(
                formatDate(result.resultDate),
                optionLabel(result.kind),
                if (result.isFromDepartment) stringResource(R.string.uploaded_by_department) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (result.labValues.isNotEmpty()) LabValuesTable(result.labValues)
        result.reportText?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (attachments.isNotEmpty()) {
            FileList(
                items = attachments.map { it.toFileItem() },
                onOpen = { item -> attachments.firstOrNull { it.id == item.key }?.let(onOpenFile) },
            )
        }
    }
}

@Composable
fun Attachment.toFileItem(): FileItem = FileItem(
    key = id,
    label = caption ?: fileName ?: stringResource(if (isPdf) R.string.file_pdf else R.string.file_photo),
    isPdf = isPdf,
    note = formatDateTime(takenAt ?: createdAt),
)
