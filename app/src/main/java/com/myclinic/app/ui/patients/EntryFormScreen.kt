package com.myclinic.app.ui.patients

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.R
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.record.RecordTable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(onClose: () -> Unit, viewModel: EntryFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val lang = currentAppLanguage()

    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    val requestClose: () -> Unit = {
        if (state.hasChanges) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = requestClose)

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(Vocabulary.section(viewModel.table).get(lang)) },
                    navigationIcon = {
                        IconButton(onClick = requestClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::save, enabled = !state.saving && !state.loading) {
                            Text(stringResource(R.string.save))
                        }
                    },
                )
            },
        ) { padding ->
            if (state.loading) {
                FullScreenLoading()
            } else Column(Modifier.fillMaxSize().padding(padding)) {
                AllergyBanner(state.allergies)
                Column(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    viewModel.spec.fields.forEach { field ->
                        FieldEditor(
                            field = field,
                            values = state.values,
                            error = state.errors[field.key],
                            surgicalCases = state.surgicalCases,
                            onValue = viewModel::onValue,
                            onValues = viewModel::onValues,
                        )
                    }
                    if (state.errors.isNotEmpty()) ErrorMessage(stringResource(R.string.fix_errors))
                    PrimaryButton(stringResource(R.string.save), onClick = viewModel::save, loading = state.saving)
                    if (!viewModel.isNew && viewModel.table != RecordTable.PATIENTS) {
                        SecondaryButton(stringResource(R.string.delete_entry), onClick = { confirmDelete = true })
                    }
                }
            }
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.unsaved_changes_title)) },
                confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.delete_entry)) },
                text = { Text(stringResource(R.string.delete_entry_confirm), style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.markDeleted() }) { Text(stringResource(R.string.confirm)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
