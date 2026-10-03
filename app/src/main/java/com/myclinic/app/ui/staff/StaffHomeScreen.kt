package com.myclinic.app.ui.staff

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.R
import com.myclinic.app.data.DataError
import com.myclinic.app.data.staff.StaffRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.patients.formatDateTime
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.app.ui.patients.sexAgeLine
import com.myclinic.app.ui.profile.VerificationStatusCard
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.record.WorklistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffHomeUiState(
    val loading: Boolean = false,
    val items: List<WorklistItem> = emptyList(),
    val loaded: Boolean = false,
    val error: DataError? = null,
)

@HiltViewModel
class StaffHomeViewModel @Inject constructor(
    private val repository: StaffRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StaffHomeUiState())
    val state: StateFlow<StaffHomeUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.worklist()
                .onSuccess { list -> _state.update { it.copy(loading = false, loaded = true, items = list) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toDataError()) } }
        }
    }
}

/**
 * The department inbox for lab/radiology staff: requests sent to their
 * department, most urgent first. Online only; nothing is kept on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHomeScreen(
    staff: Doctor,
    onOpenProfile: () -> Unit,
    onOpenRequest: (String) -> Unit,
    viewModel: StaffHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload whenever the screen comes back (e.g. after submitting a result).
    LifecycleResumeEffect(staff.isVerified) {
        if (staff.isVerified) viewModel.refresh()
        onPauseOrDispose { }
    }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.staff_inbox_title)) },
                    actions = {
                        if (staff.isVerified) {
                            IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                            }
                        }
                        IconButton(onClick = onOpenProfile) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.open_profile))
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(stringResource(R.string.home_greeting, staff.fullName), style = MaterialTheme.typography.headlineSmall)
                    }
                    if (!staff.isVerified) {
                        item { VerificationStatusCard(staff.verificationStatus, staff.verificationNote) }
                        item { Text(stringResource(R.string.staff_pending_body), style = MaterialTheme.typography.bodyLarge) }
                    }
                    state.error?.let { e -> item { ErrorMessage(e.message()) } }
                    if (staff.isVerified && state.loaded && state.items.isEmpty()) {
                        item {
                            Text(stringResource(R.string.staff_inbox_empty), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(state.items, key = { it.id }) { item -> WorklistCard(item, onClick = { onOpenRequest(item.id) }) }
                }
            }
        }
    }
}

@Composable
private fun WorklistCard(item: WorklistItem, onClick: () -> Unit) {
    val urgent = item.urgency != "routine"
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (item.urgency == "stat") {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.tests.joinToString(", "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (urgent) {
                    Text(optionLabel(item.urgency).orEmpty(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Text(item.patientName, style = MaterialTheme.typography.bodyLarge)
            sexAgeLine(item.patientSex ?: "unknown", item.ageYears, item.fileNumber).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                listOfNotNull(optionLabel(item.status), item.requestedBy, formatDateTime(item.requestedAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
