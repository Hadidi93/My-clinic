package com.myclinic.app.ui.consults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.consults.ConsultRepository
import com.myclinic.app.data.consults.NewConsult
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.navigation.NewConsultRoute
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.domain.consult.ConsultRules
import com.myclinic.domain.consult.DoctorCard
import com.myclinic.domain.model.AppRole
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.PatientRef
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.model.VerificationStatus
import com.myclinic.domain.permissions.ConsultRequest
import com.myclinic.domain.permissions.ConsultRequestError
import com.myclinic.domain.permissions.ConsultRequestValidator
import com.myclinic.domain.record.Patient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class NewConsultUiState(
    val patient: Patient? = null,
    val query: String = "",
    val results: List<DoctorCard> = emptyList(),
    val searching: Boolean = false,
    val consultant: DoctorCard? = null,
    val question: String = "",
    val urgency: String = "routine",
    val sections: Set<RecordSection> = ConsultRules.DEFAULT_SECTIONS,
    val anonymize: Boolean = true,
    val durationDays: Int = 7,
    val consent: Boolean = false,
    val errors: List<ConsultRequestError> = emptyList(),
    val showErrors: Boolean = false,
    val sending: Boolean = false,
    val error: DataError? = null,
    /** The new consult's id: the screen opens its conversation. */
    val createdId: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class NewConsultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val consults: ConsultRepository,
    private val doctors: DoctorRepository,
    patients: PatientRepository,
) : ViewModel() {

    val patientId: String = checkNotNull(savedStateHandle[NewConsultRoute::patientId.name])
    private val _state = MutableStateFlow(NewConsultUiState())
    val state: StateFlow<NewConsultUiState> = _state.asStateFlow()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val patient = patients.record(patientId).first()?.patient
            _state.update { it.copy(patient = patient) }
        }
        viewModelScope.launch {
            query.debounce(300).collect { q -> search(q) }
        }
    }

    private suspend fun search(q: String) {
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        _state.update { it.copy(searching = true) }
        consults.searchDoctors(q)
            .onSuccess { list -> _state.update { it.copy(results = list, searching = false) } }
            .onFailure { e -> _state.update { it.copy(searching = false, error = e.toDataError()) } }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q, error = null) }
        query.value = q
    }

    fun onSelect(d: DoctorCard) = update { it.copy(consultant = d, query = "", results = emptyList()) }
    fun onQuestion(v: String) = update { it.copy(question = v) }
    fun onUrgency(v: String) = update { it.copy(urgency = v) }
    fun onDuration(v: Int) = update { it.copy(durationDays = v) }
    fun onConsent(v: Boolean) = update { it.copy(consent = v) }
    fun onAnonymize(v: Boolean) = update {
        // Anonymous means no personal identifiers can be included.
        it.copy(anonymize = v, sections = if (v) it.sections - RecordSection.IDENTIFIERS else it.sections)
    }
    fun toggleSection(s: RecordSection) = update {
        val on = s in it.sections
        val sections = if (on) it.sections - s else it.sections + s
        it.copy(sections = sections, anonymize = if (s == RecordSection.IDENTIFIERS && !on) false else it.anonymize)
    }

    private fun update(change: (NewConsultUiState) -> NewConsultUiState) = _state.update { s ->
        change(s).let { n -> n.copy(errors = validate(n), error = null) }
    }

    private fun validate(s: NewConsultUiState): List<ConsultRequestError> {
        val me = doctors.myProfile.value ?: return listOf(ConsultRequestError.REQUESTER_NOT_VERIFIED)
        val patient = s.patient ?: return listOf(ConsultRequestError.NOT_PATIENT_OWNER)
        val consultant = s.consultant ?: return listOf(ConsultRequestError.CANNOT_CONSULT_SELF)
        return ConsultRequestValidator.validate(
            requester = me,
            request = ConsultRequest(
                patient = PatientRef(patient.id, patient.ownerId, patient.isDeleted),
                consultant = consultant.asVerifiedDoctor(),
                question = s.question,
                sections = s.sections,
                anonymize = s.anonymize,
                durationDays = s.durationDays,
                consentConfirmed = s.consent,
                consentDate = if (s.consent) LocalDate.now() else null,
            ),
            today = LocalDate.now(),
        )
    }

    fun send() {
        val s = _state.value
        val errors = validate(s)
        if (errors.isNotEmpty() || s.consultant == null) {
            _state.update { it.copy(errors = errors, showErrors = true) }
            return
        }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            consults.createConsult(
                NewConsult(
                    patientId = patientId, consultantId = s.consultant.id, question = s.question,
                    sections = s.sections, anonymize = s.anonymize, durationDays = s.durationDays,
                    urgency = s.urgency, consentDate = LocalDate.now(),
                ),
            )
                .onSuccess { id -> _state.update { it.copy(sending = false, createdId = id) } }
                .onFailure { e -> _state.update { it.copy(sending = false, error = e.toDataError()) } }
        }
    }
}

/** Only verified doctors appear in the directory, so a picked colleague is verified. */
private fun DoctorCard.asVerifiedDoctor() = Doctor(
    id = id, email = "", fullName = fullName, specialty = specialty, hospital = hospital, licenseNumber = null,
    phone = null, photoPath = photoPath, licenseDocumentPath = null, preferredLanguage = "en",
    role = AppRole.DOCTOR, verificationStatus = VerificationStatus.VERIFIED, verificationNote = null,
)

/** "Ask a colleague": share chosen parts of a record with one doctor, for a limited time. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewConsultScreen(onClose: () -> Unit, onCreated: (String) -> Unit, viewModel: NewConsultViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    fun shown(e: ConsultRequestError) = state.showErrors && e in state.errors

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.new_consult_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 600.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    state.patient?.let { Text(it.fullName, style = MaterialTheme.typography.titleLarge) }
                    if (shown(ConsultRequestError.REQUESTER_NOT_VERIFIED)) {
                        MessageCard(title = stringResource(R.string.consult_need_verified))
                    }
                    DoctorPicker(
                        query = state.query, onQuery = viewModel::onQuery, results = state.results, searching = state.searching,
                        selected = state.consultant, onSelect = viewModel::onSelect,
                        error = if (state.showErrors && state.consultant == null) stringResource(R.string.choose_colleague_error) else null,
                    )
                    OutlinedTextField(
                        value = state.question, onValueChange = viewModel::onQuestion, minLines = 3,
                        label = { Text(stringResource(R.string.consult_question)) },
                        isError = shown(ConsultRequestError.QUESTION_EMPTY) || shown(ConsultRequestError.QUESTION_TOO_LONG),
                        supportingText = if (shown(ConsultRequestError.QUESTION_EMPTY)) ({ Text(stringResource(R.string.field_required)) }) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.consult_urgency), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("routine", "urgent", "emergency").forEach { u ->
                            FilterChip(selected = state.urgency == u, onClick = { viewModel.onUrgency(u) },
                                label = { Text(optionLabel(u).orEmpty()) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }

                    Text(stringResource(R.string.consult_sections), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.consult_sections_help), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecordSection.entries.forEach { s ->
                            FilterChip(selected = s in state.sections, onClick = { viewModel.toggleSection(s) },
                                label = { Text(s.label()) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    if (shown(ConsultRequestError.NO_SECTIONS)) {
                        Text(stringResource(R.string.consult_no_sections), color = MaterialTheme.colorScheme.error)
                    }

                    Row(
                        Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                            .toggleable(value = state.anonymize, role = Role.Switch, onValueChange = viewModel::onAnonymize),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.consult_anonymize), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.consult_anonymize_help), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.anonymize, onCheckedChange = null)
                    }

                    Text(stringResource(R.string.consult_duration), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConsultRules.DURATIONS.forEach { d ->
                            FilterChip(selected = state.durationDays == d, onClick = { viewModel.onDuration(d) },
                                label = { Text(pluralStringResource(R.plurals.days, d, d)) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }

                    ConsentCheckbox(
                        checked = state.consent, onChange = viewModel::onConsent,
                        text = stringResource(R.string.consult_consent),
                        error = if (shown(ConsultRequestError.CONSENT_MISSING)) stringResource(R.string.consent_required) else null,
                    )
                    state.error?.let { ErrorMessage(it.message()) }
                    PrimaryButton(stringResource(R.string.send_consult), onClick = viewModel::send, loading = state.sending)
                }
            }
        }
    }
}
