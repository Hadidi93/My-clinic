package com.myclinic.app.ui.consults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.consults.ConsultRepository
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.notifications.NotificationRepository
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.navigation.NewReferralRoute
import com.myclinic.app.ui.patients.formatDateTime
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.domain.consult.DoctorCard
import com.myclinic.domain.consult.NotificationKind
import com.myclinic.domain.consult.ReferralRules
import com.myclinic.domain.consult.ReferralSummary
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

// ---------------------------------------------------------------------------
// New referral
// ---------------------------------------------------------------------------

data class NewReferralUiState(
    val patient: Patient? = null,
    val query: String = "",
    val results: List<DoctorCard> = emptyList(),
    val searching: Boolean = false,
    val doctor: DoctorCard? = null,
    val kind: String = "comanagement",
    val note: String = "",
    val consent: Boolean = false,
    val showErrors: Boolean = false,
    val sending: Boolean = false,
    val error: DataError? = null,
    val done: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class NewReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val consults: ConsultRepository,
    private val doctors: DoctorRepository,
    patients: PatientRepository,
) : ViewModel() {
    val patientId: String = checkNotNull(savedStateHandle[NewReferralRoute::patientId.name])
    private val _state = MutableStateFlow(NewReferralUiState())
    val state: StateFlow<NewReferralUiState> = _state.asStateFlow()
    private val query = MutableStateFlow("")

    val canRefer: Boolean get() = doctors.myProfile.value?.isVerified == true

    init {
        viewModelScope.launch { _state.update { it.copy(patient = patients.record(patientId).first()?.patient) } }
        viewModelScope.launch {
            query.debounce(300).collect { q ->
                if (q.isBlank()) {
                    _state.update { it.copy(results = emptyList(), searching = false) }
                } else {
                    _state.update { it.copy(searching = true) }
                    consults.searchDoctors(q)
                        .onSuccess { list -> _state.update { it.copy(results = list, searching = false) } }
                        .onFailure { e -> _state.update { it.copy(searching = false, error = e.toDataError()) } }
                }
            }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q, error = null) }
        query.value = q
    }
    fun onSelect(d: DoctorCard) = _state.update { it.copy(doctor = d, query = "", results = emptyList()) }
    fun onKind(k: String) = _state.update { it.copy(kind = k) }
    fun onNote(v: String) = _state.update { it.copy(note = v.take(2000)) }
    fun onConsent(v: Boolean) = _state.update { it.copy(consent = v) }

    fun send() {
        val s = _state.value
        if (s.doctor == null || !s.consent) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            consults.createReferral(patientId, s.doctor.id, s.kind, s.note, LocalDate.now())
                .onSuccess { _state.update { it.copy(sending = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(sending = false, error = e.toDataError()) } }
        }
    }
}

/** Refer a patient: co-management (both doctors) or transfer of care (the colleague becomes the main doctor). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewReferralScreen(onClose: () -> Unit, onSent: () -> Unit, viewModel: NewReferralViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onSent() }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.new_referral_title)) },
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
                    if (!viewModel.canRefer) MessageCard(title = stringResource(R.string.consult_need_verified))
                    DoctorPicker(
                        query = state.query, onQuery = viewModel::onQuery, results = state.results, searching = state.searching,
                        selected = state.doctor, onSelect = viewModel::onSelect,
                        error = if (state.showErrors && state.doctor == null) stringResource(R.string.choose_colleague_error) else null,
                    )
                    Text(stringResource(R.string.referral_kind), style = MaterialTheme.typography.titleMedium)
                    KindOption("comanagement", R.string.referral_comanagement_help, state.kind, viewModel::onKind)
                    KindOption("transfer", R.string.referral_transfer_help, state.kind, viewModel::onKind)
                    OutlinedTextField(
                        value = state.note, onValueChange = viewModel::onNote, minLines = 3,
                        label = { Text(stringResource(R.string.referral_note)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    ConsentCheckbox(
                        checked = state.consent, onChange = viewModel::onConsent,
                        text = stringResource(R.string.referral_consent),
                        error = if (state.showErrors && !state.consent) stringResource(R.string.consent_required) else null,
                    )
                    state.error?.let { ErrorMessage(it.message()) }
                    PrimaryButton(stringResource(R.string.send_referral), onClick = viewModel::send, loading = state.sending,
                        enabled = viewModel.canRefer)
                }
            }
        }
    }
}

@Composable
private fun KindOption(kind: String, help: Int, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget)
            .selectable(selected = selected == kind, role = Role.RadioButton, onClick = { onSelect(kind) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == kind, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(optionLabel(kind).orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Referral list
// ---------------------------------------------------------------------------

data class ReferralsUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val referrals: List<ReferralSummary> = emptyList(),
    val busyId: String? = null,
    val error: DataError? = null,
)

@HiltViewModel
class ReferralsViewModel @Inject constructor(
    private val repository: ConsultRepository,
    private val notifications: NotificationRepository,
    private val patients: PatientRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReferralsUiState())
    val state: StateFlow<ReferralsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { notifications.pushReceived.collect { if (NotificationKind.isReferral(it)) refresh() } }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.myReferrals()
                .onSuccess { list -> _state.update { it.copy(loading = false, loaded = true, referrals = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toDataError()) } }
            notifications.refresh()
            notifications.markRead(notifications.notifications.value.filter { NotificationKind.isReferral(it.kind) }.map { it.id })
        }
    }

    fun accept(r: ReferralSummary) = act(r) { repository.respondReferral(r.id, true) }
    fun decline(r: ReferralSummary) = act(r) { repository.respondReferral(r.id, false) }
    fun cancelOrEnd(r: ReferralSummary) = act(r) { repository.endReferral(r.id) }

    private fun act(r: ReferralSummary, block: suspend () -> Result<Unit>) {
        _state.update { it.copy(busyId = r.id, error = null) }
        viewModelScope.launch {
            block()
                .onSuccess {
                    // Access changed: download a newly shared patient, or remove one that is no longer ours.
                    patients.startSync()
                }
                .onFailure { e -> _state.update { it.copy(error = e.toDataError()) } }
            _state.update { it.copy(busyId = null) }
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralsScreen(onBack: () -> Unit, onOpenPatient: (String) -> Unit, viewModel: ReferralsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Pair<String, ReferralSummary>?>(null) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.referrals_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { e -> item { ErrorMessage(e.message()) } }
                    if (state.loaded && state.referrals.isEmpty()) {
                        item {
                            Text(stringResource(R.string.referrals_none), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(state.referrals, key = { it.id }) { r ->
                        ReferralCard(
                            r, busy = state.busyId == r.id,
                            onAccept = { confirm = "accept" to r },
                            onDecline = { viewModel.decline(r) },
                            onCancelOrEnd = { confirm = "end" to r },
                            onOpenPatient = { onOpenPatient(r.patientId) },
                        )
                    }
                }
            }
        }
        confirm?.let { (what, r) ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = {
                    Text(stringResource(when {
                        what == "accept" -> R.string.referral_accept
                        r.status == "pending" -> R.string.referral_cancel
                        else -> R.string.referral_end
                    }))
                },
                text = {
                    Text(stringResource(when {
                        what == "accept" && r.kind == "transfer" -> R.string.referral_accept_transfer_confirm
                        what == "accept" -> R.string.referral_accept_comanagement_confirm
                        r.status == "pending" -> R.string.referral_cancel_confirm
                        else -> R.string.referral_end_confirm
                    }))
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = null
                        if (what == "accept") viewModel.accept(r) else viewModel.cancelOrEnd(r)
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferralCard(
    r: ReferralSummary,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancelOrEnd: () -> Unit,
    onOpenPatient: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (r.received) R.string.referral_from else R.string.referral_to, r.otherDoctor.fullName),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            listOf(doctorRoleLine(r.otherDoctor), r.otherDoctor.hospital.orEmpty()).filter { it.isNotBlank() }
                .joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            Text(patientLabel(r.patientName, r.patientAge, r.patientSex), style = MaterialTheme.typography.titleMedium)
            r.primaryDiagnosis?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(listOfNotNull(optionLabel(r.kind), optionLabel(r.status), formatDateTime(r.createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall)
            r.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (ReferralRules.canRespond(r)) {
                    Button(onClick = onAccept, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.referral_accept))
                    }
                    OutlinedButton(onClick = onDecline, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.referral_decline))
                    }
                }
                if (ReferralRules.canCancel(r)) {
                    OutlinedButton(onClick = onCancelOrEnd, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.referral_cancel))
                    }
                }
                if (ReferralRules.canEnd(r)) {
                    OutlinedButton(onClick = onCancelOrEnd, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.referral_end))
                    }
                }
                if (r.status == "accepted" || !r.received) {
                    TextButton(onClick = onOpenPatient, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.open_patient))
                    }
                }
            }
        }
    }
}
