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
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.files.AttachFileButtons
import com.myclinic.app.ui.files.FileItem
import com.myclinic.app.ui.files.FileList
import androidx.compose.material3.LinearProgressIndicator
import com.myclinic.domain.forms.Vocabulary
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.key
import com.myclinic.domain.forms.FieldType
import com.myclinic.domain.forms.FormSpec
import com.myclinic.domain.record.Attachment
import com.myclinic.domain.record.RecordTable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(
    onClose: () -> Unit,
    onOpenFile: (Attachment) -> Unit,
    viewModel: EntryFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val lang = currentAppLanguage()

    val scroll = rememberScrollState()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    // A new blank form after "Save and add another": back to the first field.
    LaunchedEffect(state.formRound) { if (state.formRound > 0) scroll.animateScrollTo(0) }
    val saveLabel = stringResource(if (state.addedInSession.isNotEmpty() && !state.hasChanges) R.string.done else R.string.save)
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
                        if (state.departmentResult == null) {
                            TextButton(onClick = viewModel::save, enabled = !state.saving && !state.loading && !state.readingFile) {
                                Text(saveLabel)
                            }
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
                        .verticalScroll(scroll)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val departmentResult = state.departmentResult
                    if (departmentResult != null) {
                        // Uploaded by the lab/radiology department: shown as it was sent, never edited.
                        MessageCard(
                            title = stringResource(R.string.result_from_department),
                            body = stringResource(R.string.result_from_department_body),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        ResultDetails(departmentResult, state.attachments, onOpenFile)
                    } else {
                        val context = FieldContext(
                            surgicalCases = state.surgicalCases,
                            openRequests = state.openRequests,
                            facilities = state.facilities,
                            onAddFacility = viewModel::addFacility,
                            onRequestPicked = viewModel::onRequestPicked,
                        )
                        if (state.addedInSession.isNotEmpty()) {
                            AddedEntriesCard(state.addedInSession.map { entrySummary(viewModel.spec, it) })
                        }
                        // Keyed by round so pickers and search boxes start empty for the next entry.
                        key(state.formRound) {
                            viewModel.spec.fields.forEach { field ->
                                FieldEditor(
                                    field = field,
                                    values = state.values,
                                    error = state.errors[field.key],
                                    context = context,
                                    onValue = viewModel::onValue,
                                    onValues = viewModel::onValues,
                                )
                            }
                        }
                        if (viewModel.canAttachFiles) {
                            Text(stringResource(R.string.files_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.files_help), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FileList(
                                items = state.attachments.map { it.toFileItem() },
                                onOpen = { item -> state.attachments.firstOrNull { it.id == item.key }?.let(onOpenFile) },
                                onRemove = { item -> viewModel.removeAttachment(item.key) },
                            )
                            val waiting = stringResource(R.string.file_will_attach)
                            val photo = stringResource(R.string.file_photo)
                            FileList(
                                items = state.newFiles.mapIndexed { i, f ->
                                    FileItem(i.toString(), f.fileName ?: photo, f.isPdf, note = waiting)
                                },
                                onOpen = null,
                                onRemove = { item -> viewModel.removeNewFile(item.key.toInt()) },
                            )
                            if (state.readingFile) LinearProgressIndicator(Modifier.fillMaxWidth())
                            AttachFileButtons(onImage = viewModel::onImage, onPdf = viewModel::onPdf, enabled = !state.readingFile)
                        }
                        state.error?.let { ErrorMessage(it.message()) }
                        if (state.errors.isNotEmpty()) ErrorMessage(stringResource(R.string.fix_errors))
                        PrimaryButton(saveLabel, onClick = viewModel::save, loading = state.saving,
                            enabled = !state.readingFile)
                        if (viewModel.canAddAnother) {
                            SecondaryButton(stringResource(R.string.save_and_add_another), onClick = viewModel::saveAndAddAnother,
                                enabled = !state.saving && !state.readingFile)
                        }
                    }
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

/** The entries saved so far with "Save and add another", so the doctor sees what is already in. */
@Composable
private fun AddedEntriesCard(summaries: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.added_just_now), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            summaries.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(stringResource(R.string.entry_added_next), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** "Metformin · 500 mg", "Father · Diabetes": the required fields, plus the next short text when there is only one. */
@Composable
private fun entrySummary(spec: FormSpec, values: Map<String, String>): String {
    val shown = setOf(FieldType.TEXT, FieldType.CONDITION, FieldType.CHOICE)
    val required = spec.fields.filter { it.required && it.type in shown }
    val fields = if (required.size > 1) required else required + spec.fields.filter { !it.required && it.type == FieldType.TEXT }.take(1)
    return fields.mapNotNull { f ->
        val v = values[f.key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (f.type == FieldType.CHOICE) optionLabel(v) ?: v else v
    }.joinToString(" · ")
}
