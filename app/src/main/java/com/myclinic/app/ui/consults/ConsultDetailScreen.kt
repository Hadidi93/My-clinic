package com.myclinic.app.ui.consults

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.consults.ConsultRepository
import com.myclinic.app.data.files.PickedFile
import com.myclinic.app.data.files.PickedFileReader
import com.myclinic.app.data.notifications.NotificationRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.FullScreenError
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.files.AttachFileButtons
import com.myclinic.app.ui.files.FileItem
import com.myclinic.app.ui.files.FileList
import com.myclinic.app.ui.navigation.ConsultRoute
import com.myclinic.app.ui.patients.AllergyBanner
import com.myclinic.app.ui.patients.RecordTab
import com.myclinic.app.ui.patients.ResultDetails
import com.myclinic.app.ui.patients.formatDate
import com.myclinic.app.ui.patients.formatDateTime
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.domain.consult.ConsultMessage
import com.myclinic.domain.consult.ConsultPatient
import com.myclinic.domain.consult.ConsultRules
import com.myclinic.domain.consult.ConsultSummary
import com.myclinic.domain.consult.NotificationKind
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.Attachment
import com.myclinic.domain.record.PatientRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsultDetailUiState(
    val loading: Boolean = true,
    val consult: ConsultSummary? = null,
    val messages: List<ConsultMessage> = emptyList(),
    val loadError: DataError? = null,
    val draft: String = "",
    val files: List<PickedFile> = emptyList(),
    val readingFile: Boolean = false,
    val sending: Boolean = false,
    val error: DataError? = null,
    /** Consultant only, while the consult is live. */
    val patient: ConsultPatient? = null,
    val record: PatientRecord? = null,
    val recordError: DataError? = null,
)

@HiltViewModel
class ConsultDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ConsultRepository,
    private val notifications: NotificationRepository,
    private val fileReader: PickedFileReader,
) : ViewModel() {

    val consultId: String = checkNotNull(savedStateHandle[ConsultRoute::consultId.name])
    val myId: String? get() = repository.myUserId
    private val _state = MutableStateFlow(ConsultDetailUiState())
    val state: StateFlow<ConsultDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            notifications.pushReceived.collect { if (NotificationKind.isConsult(it)) loadMessages() }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val consult = repository.myConsults().getOrElse { e ->
                _state.update { it.copy(loading = false, loadError = e.toDataError()) }
                return@launch
            }.firstOrNull { it.id == consultId }
            if (consult == null) {
                _state.update { it.copy(loading = false, loadError = DataError.NOT_ALLOWED) }
                return@launch
            }
            _state.update { it.copy(loading = false, consult = consult) }
            loadMessages()
            markRead()
            if (ConsultRules.canViewRecord(consult)) loadRecord(consult)
        }
    }

    private suspend fun loadMessages() {
        repository.messages(consultId)
            .onSuccess { list -> _state.update { it.copy(messages = list) } }
            .onFailure { e -> _state.update { it.copy(error = e.toDataError()) } }
        markRead()
    }

    private suspend fun markRead() {
        notifications.refresh()
        notifications.markRead(
            notifications.notifications.value.filter { it.refId == consultId && NotificationKind.isConsult(it.kind) }.map { it.id },
        )
    }

    private suspend fun loadRecord(consult: ConsultSummary) {
        val patient = repository.consultPatient(consult.id).getOrElse { e ->
            _state.update { it.copy(recordError = e.toDataError()) }
            return
        }
        repository.sharedRecord(consult, patient)
            .onSuccess { record -> _state.update { it.copy(patient = patient, record = record) } }
            .onFailure { e -> _state.update { it.copy(patient = patient, recordError = e.toDataError()) } }
    }

    fun onDraft(v: String) = _state.update { it.copy(draft = v, error = null) }

    fun onImage(uri: Uri, fromCamera: Boolean) = readFile { fileReader.image(uri, deleteOriginal = fromCamera) }
    fun onPdf(uri: Uri) = readFile { fileReader.pdf(uri) }

    private fun readFile(read: suspend () -> PickedFile) {
        _state.update { it.copy(readingFile = true, error = null) }
        viewModelScope.launch {
            try {
                val f = read()
                _state.update { it.copy(readingFile = false, files = it.files + f) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(readingFile = false, error = e.toDataError()) }
            }
        }
    }

    fun removeFile(index: Int) = _state.update { it.copy(files = it.files.filterIndexed { i, _ -> i != index }) }

    fun send() {
        val s = _state.value
        val body = s.draft.trim().ifEmpty { if (s.files.isNotEmpty()) "📎" else return }
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            repository.sendMessage(consultId, body, s.files)
                .onSuccess {
                    _state.update { it.copy(sending = false, draft = "", files = emptyList()) }
                    loadMessages()
                    refreshSummary()
                }
                .onFailure { e -> _state.update { it.copy(sending = false, error = e.toDataError()) } }
        }
    }

    fun revoke() = action { repository.revoke(consultId) }
    fun close() = action { repository.close(consultId) }

    private fun action(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block().onFailure { e -> _state.update { it.copy(error = e.toDataError()) } }
            refreshSummary()
        }
    }

    private suspend fun refreshSummary() {
        repository.myConsults().onSuccess { list ->
            val c = list.firstOrNull { it.id == consultId }
            _state.update { s ->
                // Access ended: forget the shared record at once.
                if (c != null && !ConsultRules.canViewRecord(c)) s.copy(consult = c, record = null, patient = null) else s.copy(consult = c ?: s.consult)
            }
        }
    }
}

/** One consult: the question, the conversation and, for the consultant, the shared parts of the record. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultDetailScreen(
    onBack: () -> Unit,
    onOpenConsultFile: (path: String, mimeType: String) -> Unit,
    onOpenRecordFile: (Attachment) -> Unit,
    viewModel: ConsultDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val consult = state.consult

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(consult?.otherDoctor?.fullName ?: stringResource(R.string.consults_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            when {
                state.loading && consult == null -> FullScreenLoading()
                consult == null -> Box(Modifier.padding(padding)) {
                    FullScreenError(state.loadError?.message() ?: stringResource(R.string.error_unknown), onRetry = viewModel::load)
                }
                else -> Column(Modifier.fillMaxSize().padding(padding)) {
                    val showRecordTab = ConsultRules.canViewRecord(consult)
                    if (showRecordTab) {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            listOf(R.string.consult_tab_conversation, R.string.consult_tab_record).forEachIndexed { i, label ->
                                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) },
                                    modifier = Modifier.heightIn(min = TouchTarget))
                            }
                        }
                    }
                    if (showRecordTab && tab == 1) {
                        SharedRecord(state, consult, onOpenRecordFile)
                    } else {
                        Conversation(state, consult, viewModel, onOpenConsultFile,
                            onRevoke = { confirm = "revoke" }, onClose = { confirm = "close" })
                    }
                }
            }
        }

        confirm?.let { what ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(stringResource(if (what == "revoke") R.string.consult_revoke else R.string.consult_close)) },
                text = { Text(stringResource(if (what == "revoke") R.string.consult_revoke_confirm else R.string.consult_close_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = null
                        if (what == "revoke") viewModel.revoke() else viewModel.close()
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsultHeader(consult: ConsultSummary, onRevoke: () -> Unit, onClose: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(patientLabel(consult.patientName, consult.patientAge, consult.patientSex), style = MaterialTheme.typography.titleSmall)
            Text(consult.question, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(consultStatusLabel(consult), optionLabel(consult.urgency).takeIf { consult.urgency != "routine" })
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.consult_shared_sections, consult.sharedSections.sortedBy { it.ordinal }.map { it.label() }.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
            )
            if (consult.active) {
                formatDate(consult.expiresAt)?.let { Text(stringResource(R.string.consult_access_until, it), style = MaterialTheme.typography.bodySmall) }
            }
            // The main actions, in plain sight.
            if (ConsultRules.canRevoke(consult) || ConsultRules.canClose(consult)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (ConsultRules.canRevoke(consult)) {
                        OutlinedButton(onClick = onRevoke, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.consult_revoke))
                        }
                    }
                    if (ConsultRules.canClose(consult)) {
                        OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.consult_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Conversation(
    state: ConsultDetailUiState,
    consult: ConsultSummary,
    viewModel: ConsultDetailViewModel,
    onOpenFile: (path: String, mimeType: String) -> Unit,
    onRevoke: () -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size) }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ConsultHeader(consult, onRevoke, onClose) }
            items(state.messages, key = { it.id }) { m -> MessageBubble(m, mine = m.senderId == viewModel.myId, onOpenFile) }
        }
        HorizontalDivider()
        if (ConsultRules.canReply(consult)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.error?.let { ErrorMessage(it.message()) }
                if (state.files.isNotEmpty()) {
                    val photo = stringResource(R.string.file_photo)
                    FileList(
                        items = state.files.mapIndexed { i, f -> FileItem(i.toString(), f.fileName ?: photo, f.isPdf) },
                        onOpen = null, onRemove = { viewModel.removeFile(it.key.toInt()) },
                    )
                }
                if (state.readingFile || state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                AttachFileButtons(onImage = viewModel::onImage, onPdf = viewModel::onPdf, enabled = !state.readingFile && !state.sending)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.draft, onValueChange = viewModel::onDraft, modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.consult_message_hint)) }, maxLines = 5,
                    )
                    IconButton(
                        onClick = viewModel::send,
                        enabled = !state.sending && !state.readingFile && (state.draft.isNotBlank() || state.files.isNotEmpty()),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send)) }
                }
            }
        } else {
            Text(stringResource(R.string.consult_read_only), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun MessageBubble(m: ConsultMessage, mine: Boolean, onOpenFile: (path: String, mimeType: String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Card(
            Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.body != "📎") Text(m.body, style = MaterialTheme.typography.bodyLarge)
                if (m.attachmentPaths.isNotEmpty()) {
                    val photo = stringResource(R.string.file_photo)
                    val pdf = stringResource(R.string.file_pdf)
                    FileList(
                        items = m.attachmentPaths.map { p -> FileItem(p, if (p.endsWith(".pdf")) pdf else photo, p.endsWith(".pdf")) },
                        onOpen = { item -> onOpenFile(item.key, if (item.isPdf) PickedFile.MIME_PDF else PickedFile.MIME_JPEG) },
                    )
                }
                formatDateTime(m.createdAt)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** What the requesting doctor shared, read live from the server and never stored on this phone. */
@Composable
private fun SharedRecord(state: ConsultDetailUiState, consult: ConsultSummary, onOpenFile: (Attachment) -> Unit) {
    val record = state.record
    when {
        state.recordError != null -> Box(Modifier.padding(16.dp)) { ErrorMessage(state.recordError.message()) }
        record == null -> FullScreenLoading()
        else -> Column(Modifier.fillMaxSize()) {
            if (RecordSection.ALLERGIES in consult.sharedSections) AllergyBanner(record.allergies)
            MessageCard(
                title = patientLabel(state.patient?.fullName, state.patient?.ageYears, state.patient?.sex),
                body = stringResource(R.string.consult_record_note),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(12.dp),
            )
            if (RecordSection.INVESTIGATIONS in consult.sharedSections && record.investigationResults.isNotEmpty()) {
                // Results with their values and files, then the rest of the shared record.
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.results_title), style = MaterialTheme.typography.titleMedium)
                    record.investigationResults.forEach { r ->
                        Card(Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(12.dp)) { ResultDetails(r, record.attachmentsForResult(r.id), onOpenFile) }
                        }
                    }
                }
                HorizontalDivider()
            }
            Box(Modifier.weight(1f)) {
                RecordTab(record, editable = false, onEdit = { _, _ -> }, onOpenInvestigation = { },
                    visibleSections = consult.sharedSections)
            }
        }
    }
}
