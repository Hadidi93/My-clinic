package com.myclinic.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.R
import com.myclinic.app.ui.components.SecondaryButton
import com.myclinic.app.ui.patients.SyncBanner
import com.myclinic.app.ui.patients.formatDate
import com.myclinic.app.ui.profile.VerificationStatusCard
import com.myclinic.app.ui.theme.DashboardNumberStyle
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.record.DateFilter

/**
 * Home dashboard: big cards reachable with one thumb. Consults (Phase 4) and
 * results (Phase 3) are still placeholders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    doctor: Doctor,
    onOpenProfile: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenPatients: (DateFilter) -> Unit,
    onQuickAdd: () -> Unit,
    onOpenPatient: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (doctor.isAdmin) {
                        IconButton(onClick = onOpenAdmin) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = stringResource(R.string.admin_approvals))
                        }
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.open_profile))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQuickAdd,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.add_patient)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SyncBanner(state.pendingChanges, state.failedChanges, state.offline, viewModel::discardFailedChanges)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.home_greeting, doctor.fullName),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (!doctor.isVerified) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        VerificationStatusCard(doctor.verificationStatus, doctor.verificationNote)
                    }
                }
                item {
                    DashboardCard(Icons.Filled.People, stringResource(R.string.home_today_patients),
                        value = state.todaysPatients.toString(), onClick = { onOpenPatients(DateFilter.TODAY) })
                }
                item {
                    DashboardCard(Icons.AutoMirrored.Filled.EventNote, stringResource(R.string.home_upcoming_operations),
                        value = state.upcomingOperations.size.toString(), onClick = null)
                }
                item { DashboardCard(Icons.Filled.Forum, stringResource(R.string.home_pending_consults), phase = 4) }
                item { DashboardCard(Icons.Filled.Science, stringResource(R.string.home_pending_results), phase = 3) }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SecondaryButton(
                        text = "${stringResource(R.string.view_all_patients)} (${state.totalPatients})",
                        onClick = { onOpenPatients(DateFilter.ALL) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.home_upcoming_operations), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                }
                if (!state.loading && state.upcomingOperations.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.home_no_operations), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.upcomingOperations, key = { it.case.id }, span = { GridItemSpan(maxLineSpan) }) { op ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenPatient(op.patient.id) }) {
                        Column(Modifier.padding(16.dp).heightIn(min = 40.dp)) {
                            Text(formatDate(op.date.toString()).orEmpty(), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(op.case.plannedOperation ?: op.case.diagnosis, style = MaterialTheme.typography.titleMedium)
                            Text(op.patient.fullName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    icon: ImageVector,
    title: String,
    value: String? = null,
    phase: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value ?: "—", style = DashboardNumberStyle)
            Text(title, style = MaterialTheme.typography.titleSmall)
            phase?.let {
                Text(stringResource(R.string.coming_in_phase, it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
