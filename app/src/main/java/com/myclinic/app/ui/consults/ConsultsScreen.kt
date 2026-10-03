package com.myclinic.app.ui.consults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.consults.ConsultRepository
import com.myclinic.app.data.notifications.NotificationRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.patients.formatDateTime
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.domain.consult.ConsultSummary
import com.myclinic.domain.consult.NotificationKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsultsUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val consults: List<ConsultSummary> = emptyList(),
    val error: DataError? = null,
) {
    val received get() = consults.filter { it.iAmConsultant }
    val sent get() = consults.filterNot { it.iAmConsultant }
}

@HiltViewModel
class ConsultsViewModel @Inject constructor(
    private val repository: ConsultRepository,
    notifications: NotificationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ConsultsUiState())
    val state: StateFlow<ConsultsUiState> = _state.asStateFlow()

    init {
        // A consult push while this list is open: reload it.
        viewModelScope.launch { notifications.pushReceived.collect { if (NotificationKind.isConsult(it)) refresh() } }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.myConsults()
                .onSuccess { list -> _state.update { it.copy(loading = false, loaded = true, consults = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toDataError()) } }
        }
    }
}

/** Consults I was asked (received) and consults I asked (sent). Online only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: ConsultsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.consults_title)) },
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
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf(
                        stringResource(R.string.consults_received) to state.received.sumOf { it.unread },
                        stringResource(R.string.consults_sent) to state.sent.sumOf { it.unread },
                    ).forEachIndexed { i, (label, unread) ->
                        Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.heightIn(min = TouchTarget),
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(label)
                                    if (unread > 0) Badge { Text(unread.toString()) }
                                }
                            })
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val list = if (tab == 0) state.received else state.sent
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { e -> item { ErrorMessage(e.message()) } }
                    if (state.loaded && list.isEmpty()) {
                        item {
                            Text(stringResource(if (tab == 0) R.string.consults_none_received else R.string.consults_none_sent),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(list, key = { it.id }) { c -> ConsultCard(c, onClick = { onOpen(c.id) }) }
                }
            }
        }
    }
}

@Composable
private fun ConsultCard(c: ConsultSummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.otherDoctor.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Normal)
                if (c.unread > 0) Badge { Text(c.unread.toString()) }
            }
            doctorRoleLine(c.otherDoctor).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(patientLabel(c.patientName, c.patientAge, c.patientSex), style = MaterialTheme.typography.bodyMedium)
            Text(c.question, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                listOfNotNull(
                    consultStatusLabel(c),
                    optionLabel(c.urgency).takeIf { c.urgency != "routine" },
                    formatDateTime(c.lastActivityAt),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (c.urgency != "routine" && c.active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Waiting / Answered / Closed / Access withdrawn / Expired. */
@Composable
fun consultStatusLabel(c: ConsultSummary): String = when {
    c.revokedAt != null -> stringResource(R.string.consult_revoked)
    c.status == "closed" -> optionLabel("closed").orEmpty()
    !c.active -> stringResource(R.string.consult_expired)
    else -> optionLabel(c.status).orEmpty()
}
