package com.myclinic.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myclinic.app.R
import com.myclinic.app.ui.profile.VerificationStatusCard
import com.myclinic.app.ui.theme.DashboardNumberStyle
import com.myclinic.domain.model.Doctor

/**
 * Home dashboard. In Phase 1 the four cards are placeholders showing which
 * phase fills them in; the layout (big cards, two columns, thumb-reachable)
 * is final.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(doctor: Doctor, onOpenProfile: () -> Unit, onOpenAdmin: () -> Unit) {
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
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
            item { DashboardCard(Icons.Filled.People, stringResource(R.string.home_today_patients), phase = 2) }
            item { DashboardCard(Icons.Filled.Forum, stringResource(R.string.home_pending_consults), phase = 4) }
            item { DashboardCard(Icons.Filled.Science, stringResource(R.string.home_pending_results), phase = 3) }
            item { DashboardCard(Icons.AutoMirrored.Filled.EventNote, stringResource(R.string.home_upcoming_operations), phase = 2) }
        }
    }
}

@Composable
private fun DashboardCard(icon: ImageVector, title: String, phase: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("—", style = DashboardNumberStyle)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.coming_in_phase, phase),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
