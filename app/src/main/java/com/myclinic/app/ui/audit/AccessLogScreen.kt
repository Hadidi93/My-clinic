package com.myclinic.app.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.audit.AuditRepository
import com.myclinic.app.data.audit.SupabaseAuditRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.consults.label
import com.myclinic.app.ui.navigation.AccessLogRoute
import com.myclinic.app.ui.patients.formatDateTime
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.AuditEntry
import com.myclinic.domain.record.RecordTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessLogUiState(
    val loading: Boolean = false,
    val entries: List<AuditEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val error: DataError? = null,
)

@HiltViewModel
class AccessLogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AuditRepository,
) : ViewModel() {
    /** Null: the admin's app-wide activity log. */
    val patientId: String? = savedStateHandle[AccessLogRoute::patientId.name]
    private val _state = MutableStateFlow(AccessLogUiState())
    val state: StateFlow<AccessLogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = load(before = null)

    fun loadMore() = load(before = _state.value.entries.lastOrNull()?.occurredAt)

    private fun load(before: String?) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = patientId?.let { repository.patientLog(it) } ?: repository.activityLog(before)
            result
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            loading = false,
                            entries = if (before == null) list else it.entries + list,
                            canLoadMore = patientId == null && list.size >= SupabaseAuditRepository.PAGE,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toDataError()) } }
        }
    }
}

/**
 * "Who opened or changed this record, and when" (for the patient's main
 * doctor), or the app-wide activity log (for admins). Never shows clinical values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessLogScreen(onBack: () -> Unit, viewModel: AccessLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (viewModel.patientId != null) R.string.access_log_title else R.string.activity_log_title)) },
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
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    item {
                        Text(
                            stringResource(if (viewModel.patientId != null) R.string.access_log_help else R.string.activity_log_help),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    state.error?.let { e -> item { Column(Modifier.padding(16.dp)) { ErrorMessage(e.message()) } } }
                    items(state.entries, key = { it.id }) { e ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entryText(e, showPatient = viewModel.patientId == null), style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(formatDateTime(e.occurredAt).orEmpty(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                when (e.via) {
                                    "consult" -> Text(stringResource(R.string.audit_via_consult), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary)
                                    "staff" -> Text(stringResource(R.string.audit_via_department), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    if (state.canLoadMore) {
                        item {
                            TextButton(onClick = viewModel::loadMore, enabled = !state.loading, modifier = Modifier.padding(8.dp)) {
                                Text(stringResource(R.string.audit_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Dr Demo (Consultant) opened Allergies", "You changed Examination & vital signs", ... */
@Composable
private fun entryText(e: AuditEntry, showPatient: Boolean): String {
    val lang = currentAppLanguage()
    val who = when {
        e.actorIsMe -> stringResource(R.string.audit_you)
        e.actorName != null -> listOfNotNull(e.actorName, optionLabel(e.actorGrade)?.let { "($it)" }).joinToString(" ")
        else -> stringResource(R.string.audit_system)
    }
    val what = e.section?.let { s -> RecordSection.fromDb(s)?.label() }
        ?: RecordTable.fromTableName(e.tableName)?.let { Vocabulary.section(it).get(lang) }
        ?: when (e.tableName) {
            "consults", "consult_messages" -> stringResource(R.string.consults_title)
            "referrals" -> stringResource(R.string.referrals_title)
            "doctors" -> stringResource(R.string.audit_account)
            else -> e.tableName
        }
    val action = stringResource(
        when (e.action) {
            "view" -> R.string.audit_view
            "create" -> R.string.audit_create
            "update" -> R.string.audit_update
            "delete" -> R.string.audit_delete
            "share" -> R.string.audit_share
            "revoke" -> R.string.audit_revoke
            "close" -> R.string.audit_close
            "export" -> R.string.audit_export
            "verify" -> R.string.audit_verify
            "refer" -> R.string.audit_refer
            "transfer" -> R.string.audit_transfer
            "submit_result" -> R.string.audit_submit_result
            else -> R.string.audit_other
        },
        who, what,
    )
    val patient = e.patientId
    return if (showPatient && patient != null) "$action · ${stringResource(R.string.audit_patient_ref, patient.take(8))}" else action
}
