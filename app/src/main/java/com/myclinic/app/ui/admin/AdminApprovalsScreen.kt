package com.myclinic.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.myclinic.app.R
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.message
import com.myclinic.domain.model.Doctor
import com.myclinic.domain.model.VerificationStatus

/** Admin-only list of doctors waiting for licence verification. Screenshots are blocked here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApprovalsScreen(onBack: () -> Unit, viewModel: AdminApprovalsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var rejecting by remember { mutableStateOf<Doctor?>(null) }

    val decision = state.lastDecision
    val decisionText = decision?.let { (name, status) ->
        stringResource(if (status == VerificationStatus.VERIFIED) R.string.admin_approved else R.string.admin_rejected, name)
    }
    LaunchedEffect(decisionText) {
        if (decisionText != null) {
            viewModel.onDecisionShown()
            snackbar.showSnackbar(decisionText)
        }
    }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.admin_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.error?.let { error -> item { ErrorMessage(error.message()) } }
                    if (!state.loading && state.doctors.isEmpty()) {
                        item { Text(stringResource(R.string.admin_empty), style = MaterialTheme.typography.bodyLarge) }
                    }
                    items(state.doctors, key = { it.id }) { doctor ->
                        PendingDoctorCard(
                            doctor = doctor,
                            department = state.facilities.firstOrNull { it.id == doctor.facilityId }?.label,
                            busy = state.busyDoctorId == doctor.id,
                            onApprove = { viewModel.approve(doctor) },
                            onReject = { rejecting = doctor },
                            onViewLicense = { viewModel.openLicense(doctor) },
                        )
                    }
                }
            }
        }

        rejecting?.let { doctor ->
            RejectDialog(
                onDismiss = { rejecting = null },
                onConfirm = { reason ->
                    viewModel.reject(doctor, reason)
                    rejecting = null
                },
            )
        }

        state.licenseViewerUrl?.let { url ->
            AlertDialog(
                onDismissRequest = viewModel::closeLicense,
                confirmButton = { TextButton(onClick = viewModel::closeLicense) { Text(stringResource(R.string.close)) } },
                text = {
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.license_document),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun PendingDoctorCard(
    doctor: Doctor,
    department: String?,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onViewLicense: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(doctor.fullName.ifBlank { doctor.email }, style = MaterialTheme.typography.titleMedium)
            Text(doctor.email, style = MaterialTheme.typography.bodyMedium)
            if (doctor.isStaff) {
                // Staff never see patient records, only their department's requests.
                Text(
                    stringResource(R.string.admin_staff_account, department ?: stringResource(R.string.department_unknown)),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary,
                )
            }
            listOfNotNull(doctor.specialty, doctor.hospital).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                doctor.licenseNumber?.let { stringResource(R.string.license_label, it) }
                    ?: stringResource(R.string.profile_incomplete),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (doctor.licenseDocumentPath != null) {
                TextButton(onClick = onViewLicense, modifier = Modifier.heightIn(min = TouchTarget)) {
                    Text(stringResource(R.string.view_license))
                }
            } else {
                Text(
                    stringResource(R.string.no_license_document),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f).heightIn(min = TouchTarget)) {
                        Text(stringResource(R.string.reject))
                    }
                    Button(
                        onClick = onApprove,
                        enabled = doctor.isProfileComplete,
                        modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                    ) {
                        Text(stringResource(R.string.approve))
                    }
                }
            }
        }
    }
}

@Composable
private fun RejectDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_reject_title)) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= 500) reason = it },
                label = { Text(stringResource(R.string.admin_reject_reason)) },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text(stringResource(R.string.reject)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
