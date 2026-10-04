package com.myclinic.app.ui.patients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.R
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.domain.forms.FormSpecs
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.record.DateFilter
import com.myclinic.domain.record.PatientSummary
import com.myclinic.domain.record.SUGGESTED_TAGS
import com.myclinic.domain.record.StatusFilter
import androidx.compose.material.icons.filled.TableView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    initialDateFilter: DateFilter,
    startWithQuickAdd: Boolean,
    onBack: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onResearchExport: () -> Unit,
    viewModel: PatientListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quick by viewModel.quickAdd.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.initialize(initialDateFilter, startWithQuickAdd) }
    LaunchedEffect(Unit) { viewModel.openPatient.collect { onOpenPatient(it) } }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.patients)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = onResearchExport) {
                            Icon(Icons.Filled.TableView, contentDescription = stringResource(R.string.research_export_title))
                        }
                        IconButton(onClick = viewModel::onToggleDeleted) {
                            Icon(
                                if (state.query.includeDeleted) Icons.Filled.Delete else Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(
                                    if (state.query.includeDeleted) R.string.hide_deleted else R.string.show_deleted,
                                ),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openQuickAdd,
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_patient)) },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                SyncBanner(state.pendingChanges, state.failedChanges, state.offline, viewModel::discardFailedChanges)
                SearchField(state.query.text, viewModel::onQueryText)
                StatusRow(state.query.status, viewModel::onStatus)
                FilterRow(state.query.date, viewModel::onDateFilter, state.tagsInUse, state.query.tags, viewModel::onToggleTag)
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp), // room for the add button
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!state.loading && state.results.isEmpty()) {
                        item {
                            Text(
                                stringResource(if (state.totalPatients == 0) R.string.no_patients else R.string.no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(state.results, key = { it.patient.id }) { summary ->
                        PatientRow(summary, onClick = { onOpenPatient(summary.patient.id) })
                        HorizontalDivider()
                    }
                }
            }
        }

        if (quick.open) {
            QuickAddSheet(
                state = quick,
                onValue = viewModel::onQuickValue,
                onSave = viewModel::saveQuickAdd,
                onDismiss = viewModel::closeQuickAdd,
            )
        }
    }
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_patients)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 56.dp),
    )
}

/** Current patients, discharged ones ("follow-up finished"), or all. */
@Composable
private fun StatusRow(status: StatusFilter, onStatus: (StatusFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val options = listOf(
            StatusFilter.ACTIVE to R.string.status_current,
            StatusFilter.DISCHARGED to R.string.status_discharged,
            StatusFilter.ALL to R.string.status_all,
        )
        items(options) { (filter, label) ->
            FilterChip(selected = status == filter, onClick = { onStatus(filter) }, label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun FilterRow(
    date: DateFilter,
    onDate: (DateFilter) -> Unit,
    tags: List<String>,
    selectedTags: Set<String>,
    onTag: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dates = listOf(
            DateFilter.ALL to R.string.filter_all_dates,
            DateFilter.TODAY to R.string.filter_today,
            DateFilter.LAST_7_DAYS to R.string.filter_7_days,
            DateFilter.LAST_30_DAYS to R.string.filter_30_days,
        )
        items(dates) { (filter, label) ->
            FilterChip(selected = date == filter, onClick = { onDate(filter) }, label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        items(tags) { tag ->
            FilterChip(selected = tag in selectedTags, onClick = { onTag(tag) }, label = { Text("#$tag") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun PatientRow(summary: PatientSummary, onClick: () -> Unit) {
    val p = summary.patient
    ListItem(
        headlineContent = { Text(p.fullName, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val line = sexAgeLine(p.sex, summary.ageYears, p.fileNumber)
                if (line.isNotEmpty()) Text(line)
                p.primaryDiagnosis?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (p.tags.isNotEmpty()) Text(p.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (summary.allergies.isNotEmpty()) {
                    Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.allergies_banner, summary.allergies.joinToString()),
                        tint = MaterialTheme.colorScheme.error)
                }
                if (p.isDeleted) Text(stringResource(R.string.deleted), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
                if (p.isDischarged) Text(stringResource(R.string.discharged), color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall)
                summary.lastActivity?.let { Text(formatDate(it.toString()).orEmpty(), style = MaterialTheme.typography.labelSmall) }
            }
        },
        modifier = Modifier.clickable(onClick = onClick).heightIn(min = 72.dp),
    )
}

/** Bottom sheet for adding a patient in a few seconds: name, sex, age, file no., diagnosis, tags. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickAddSheet(
    state: QuickAddState,
    onValue: (String, String) -> Unit,
    onSave: (openAfter: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val lang = currentAppLanguage()
    fun label(key: String) = Vocabulary.field(key).get(lang)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.quick_add_hint), style = MaterialTheme.typography.bodyMedium)
            val v = state.values
            AppTextField(
                value = v["full_name"].orEmpty(), onValueChange = { onValue("full_name", it) },
                label = label("full_name") + " *", capitalization = KeyboardCapitalization.Words,
                error = state.errors["full_name"]?.message(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormSpecs.PATIENT.field("sex")!!.options.forEach { option ->
                    FilterChip(
                        selected = v["sex"] == option, onClick = { onValue("sex", option) },
                        label = { Text(Vocabulary.option(option).get(lang)) }, modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = v["age_years"].orEmpty(), onValueChange = { onValue("age_years", it) },
                    label = label("age_years"), keyboardType = KeyboardType.Number,
                    error = state.errors["age_years"]?.message(), modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = v["file_number"].orEmpty(), onValueChange = { onValue("file_number", it) },
                    label = label("file_number"), error = state.errors["file_number"]?.message(),
                    modifier = Modifier.weight(1f),
                )
            }
            AppTextField(
                value = v["primary_diagnosis"].orEmpty(), onValueChange = { onValue("primary_diagnosis", it) },
                label = label("primary_diagnosis"), error = state.errors["primary_diagnosis"]?.message(),
                imeAction = ImeAction.Done,
            )
            val selected = v["tags"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTED_TAGS.forEach { tag ->
                    FilterChip(
                        selected = tag in selected,
                        onClick = {
                            val next = if (tag in selected) selected - tag else selected + tag
                            onValue("tags", next.joinToString(","))
                        },
                        label = { Text("#$tag") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            PrimaryButton(stringResource(R.string.save_and_open), onClick = { onSave(true) }, loading = state.saving)
            SecondaryButton(stringResource(R.string.save), onClick = { onSave(false) }, enabled = !state.saving)
        }
    }
}

