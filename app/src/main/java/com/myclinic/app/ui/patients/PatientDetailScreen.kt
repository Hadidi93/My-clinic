package com.myclinic.app.ui.patients

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myclinic.app.R
import com.myclinic.app.ui.components.FullScreenLoading
import com.myclinic.app.ui.components.message
import androidx.compose.runtime.LaunchedEffect
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.domain.forms.ChronicDiseases
import com.myclinic.domain.forms.PreopChecklist
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.record.PatientRecord
import com.myclinic.domain.record.RecordTable
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.InvestigationRules
import com.myclinic.domain.record.LabValueFormatter
import com.myclinic.domain.record.VitalsFormatter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight

/**
 * The patient's record. [onEdit] opens the form for (table, entry id or null for a new entry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    onBack: () -> Unit,
    onEdit: (patientId: String, table: RecordTable, entryId: String?) -> Unit,
    onOpenInvestigation: (patientId: String, requestId: String) -> Unit,
    onConsult: (patientId: String) -> Unit,
    onRefer: (patientId: String) -> Unit,
    onOpenReferrals: () -> Unit,
    onOpenAccessLog: (patientId: String) -> Unit,
    viewModel: PatientDetailViewModel = hiltViewModel(),
) {
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    var confirmExport by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = currentAppLanguage()
    LaunchedEffect(exportState.share) {
        exportState.share?.let { context.startActivity(it) }
        if (exportState.share != null) viewModel.onExportHandled()
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val endState by viewModel.endState.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }
    // Co-management ended: this record is no longer ours, go back.
    LaunchedEffect(endState.ended) { if (endState.ended) onBack() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val record = state.record

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(record?.patient?.fullName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (record != null) {
                            IconButton(onClick = { onEdit(viewModel.patientId, RecordTable.PATIENTS, viewModel.patientId) }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_personal_data))
                            }
                            if (state.isOwner) {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (record.patient.isDeleted) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.restore_patient)) },
                                            onClick = { menuOpen = false; viewModel.setDeleted(false) },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete_patient)) },
                                            onClick = { menuOpen = false; confirmDelete = true },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            when {
                state.loading -> FullScreenLoading()
                record == null -> Text(
                    stringResource(R.string.patient_not_found),
                    modifier = Modifier.padding(padding).padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> Column(Modifier.fillMaxSize().padding(padding)) {
                    AllergyBanner(record.allergies, onClick = {
                        onEdit(record.patient.id, RecordTable.ALLERGIES, null)
                    })
                    PatientHeader(state)
                    if (!record.patient.isDeleted) {
                        PatientActions(
                            isOwner = state.isOwner,
                            onConsult = { onConsult(record.patient.id) },
                            onRefer = { onRefer(record.patient.id) },
                            onOpenReferrals = onOpenReferrals,
                            onEndComanagement = { confirmEnd = true },
                            onAccessLog = { onOpenAccessLog(record.patient.id) },
                            onExport = { confirmExport = true },
                            working = endState.working || exportState.working,
                        )
                    }
                    PrimaryTabRow(selectedTabIndex = tab) {
                        listOf(R.string.tab_record, R.string.tab_timeline, R.string.tab_vitals).forEachIndexed { i, label ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) },
                                modifier = Modifier.heightIn(min = TouchTarget))
                        }
                    }
                    val editable = !record.patient.isDeleted
                    val edit: (RecordTable, String?) -> Unit = { t, id -> if (editable) onEdit(record.patient.id, t, id) }
                    when (tab) {
                        0 -> RecordTab(record, editable, edit, onOpenInvestigation = { onOpenInvestigation(record.patient.id, it) })
                        1 -> TimelineTab(state.timeline, onOpen = { t, id ->
                            when (t) {
                                RecordTable.INVESTIGATION_REQUESTS -> onOpenInvestigation(record.patient.id, id)
                                RecordTable.PATIENTS -> edit(t, record.patient.id)
                                else -> edit(t, id)
                            }
                        })
                        else -> VitalsTab(record.examinations, record.investigationResults, state.trends, state.selectedTrend,
                            viewModel::selectTrend)
                    }
                }
            }
        }

        if (confirmExport) {
            AlertDialog(
                onDismissRequest = { confirmExport = false },
                title = { Text(stringResource(R.string.export_pdf)) },
                text = { Text(stringResource(R.string.export_warning)) },
                confirmButton = {
                    TextButton(onClick = { confirmExport = false; viewModel.export(language) }) { Text(stringResource(R.string.export_confirm)) }
                },
                dismissButton = { TextButton(onClick = { confirmExport = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        exportState.error?.let { e ->
            AlertDialog(
                onDismissRequest = viewModel::onExportHandled,
                title = { Text(stringResource(R.string.export_pdf)) },
                text = { Text(stringResource(R.string.export_failed, e.message())) },
                confirmButton = { TextButton(onClick = viewModel::onExportHandled) { Text(stringResource(R.string.ok)) } },
            )
        }
        if (confirmEnd) {
            AlertDialog(
                onDismissRequest = { confirmEnd = false },
                title = { Text(stringResource(R.string.referral_end)) },
                text = { Text(stringResource(R.string.referral_end_self_confirm)) },
                confirmButton = {
                    TextButton(onClick = { confirmEnd = false; viewModel.endComanagement() }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        endState.error?.let { e ->
            AlertDialog(
                onDismissRequest = { viewModel.clearEndError() },
                title = { Text(stringResource(R.string.referral_end)) },
                text = { Text(e.message()) },
                confirmButton = { TextButton(onClick = { viewModel.clearEndError() }) { Text(stringResource(R.string.ok)) } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.delete_patient)) },
                text = { Text(stringResource(R.string.delete_patient_confirm)) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; viewModel.setDeleted(true) }) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun PatientHeader(state: PatientDetailUiState) {
    val p = state.record?.patient ?: return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val line = sexAgeLine(p.sex, state.ageYears, p.fileNumber)
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge)
        p.primaryDiagnosis?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (p.tags.isNotEmpty()) Text(p.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodyMedium)
        if (!state.isOwner) Text(stringResource(R.string.comanaged_badge), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary)
        if (p.isDeleted) MessageCard(title = stringResource(R.string.patient_deleted_banner))
    }
}

/**
 * The main actions as buttons on the screen: consult, refer, referrals for the
 * main doctor; "End co-management" for a co-manager. Delete stays in ⋮.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatientActions(
    isOwner: Boolean,
    onConsult: () -> Unit,
    onRefer: () -> Unit,
    onOpenReferrals: () -> Unit,
    onEndComanagement: () -> Unit,
    onAccessLog: () -> Unit,
    onExport: () -> Unit,
    working: Boolean,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isOwner) {
            AssistChip(onClick = onConsult, label = { Text(stringResource(R.string.ask_colleague)) },
                leadingIcon = { Icon(Icons.Filled.Forum, contentDescription = null) }, modifier = Modifier.heightIn(min = 48.dp))
            AssistChip(onClick = onRefer, label = { Text(stringResource(R.string.refer_patient)) },
                leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) }, modifier = Modifier.heightIn(min = 48.dp))
            AssistChip(onClick = onOpenReferrals, label = { Text(stringResource(R.string.referrals_title)) },
                modifier = Modifier.heightIn(min = 48.dp))
            AssistChip(onClick = onAccessLog, label = { Text(stringResource(R.string.access_log_title)) },
                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) }, modifier = Modifier.heightIn(min = 48.dp))
            AssistChip(onClick = onExport, enabled = !working, label = { Text(stringResource(R.string.export_pdf)) },
                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) }, modifier = Modifier.heightIn(min = 48.dp))
        } else {
            AssistChip(onClick = onEndComanagement, enabled = !working, label = { Text(stringResource(R.string.referral_end)) },
                leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

/** One line in a section card. */
private data class EntryLine(val id: String, val title: String, val detail: String?)

/**
 * The record, section by section. [visibleSections] limits it to what a
 * consult shared (null = everything), so a section that wasn't shared is
 * left out rather than shown as empty.
 */
@Composable
internal fun RecordTab(
    record: PatientRecord,
    editable: Boolean,
    onEdit: (RecordTable, String?) -> Unit,
    onOpenInvestigation: (String) -> Unit,
    visibleSections: Set<RecordSection>? = null,
) {
    fun visible(section: RecordSection) = visibleSections == null || section in visibleSections
    val lang = currentAppLanguage()
    fun opt(v: String?) = v?.let { Vocabulary.option(it).get(lang) }
    val current = stringResource(R.string.current)
    val stopped = stringResource(R.string.stopped)
    val chronic = stringResource(R.string.chronic)

    val sections: List<Pair<RecordTable, List<EntryLine>>> = listOf(
        RecordTable.PRESENTING_COMPLAINTS to record.complaints.map {
            EntryLine(it.id, it.complaint, listOfNotNull(formatDateTime(it.recordedAt), it.hpi).joinToString(" — "))
        },
        RecordTable.MEDICAL_CONDITIONS to record.conditions.map {
            val name = ChronicDiseases.byCode(it.conditionCode)?.name?.get(lang) ?: it.name
            EntryLine(it.id, name, listOfNotNull(chronic.takeIf { _ -> it.isChronic }, formatDate(it.diagnosedOn), it.notes)
                .joinToString(" · "))
        },
        RecordTable.SURGICAL_HISTORY to record.surgicalHistory.map {
            EntryLine(it.id, it.procedure, listOfNotNull(formatDate(it.performedOn), it.hospital, it.complications).joinToString(" · "))
        },
        RecordTable.MEDICATIONS to record.medications.map {
            EntryLine(it.id, listOfNotNull(it.name, it.dose).joinToString(" "),
                listOfNotNull(opt(it.route), it.frequency, if (it.isCurrent) current else stopped).joinToString(" · "))
        },
        RecordTable.ALLERGIES to record.allergies.map {
            EntryLine(it.id, it.allergen, listOfNotNull(opt(it.severity), it.reaction).joinToString(" · "))
        },
        RecordTable.FAMILY_HISTORY to record.familyHistory.map {
            EntryLine(it.id, "${opt(it.relation)}: ${it.condition}", it.notes)
        },
        RecordTable.SOCIAL_HISTORY to listOfNotNull(record.socialHistory).map {
            EntryLine(it.id, listOfNotNull(opt(it.smoking), it.packYears?.let { py -> "$py pack-years" }).joinToString(" · "),
                listOfNotNull(it.occupation, opt(it.maritalStatus), it.alcohol).joinToString(" · "))
        },
        RecordTable.EXAMINATIONS to record.examinations.map {
            EntryLine(it.id, formatDateTime(it.examinedAt).orEmpty(), listOfNotNull(VitalsFormatter.summary(it), it.findings).joinToString("\n"))
        },
        RecordTable.SURGICAL_CASES to record.surgicalCases.map {
            val done = PreopChecklist.ITEMS.count { key -> it.preopChecklist[key] == true }
            EntryLine(
                it.id,
                listOfNotNull(it.plannedOperation, it.diagnosis.takeIf { _ -> it.plannedOperation == null }).joinToString(),
                listOfNotNull(
                    opt(it.status), formatDate(it.operationDate ?: it.plannedDate), it.diagnosis.takeIf { _ -> it.plannedOperation != null },
                    stringResource(R.string.checklist_progress, done, PreopChecklist.ITEMS.size),
                ).joinToString(" · "),
            )
        },
        RecordTable.POSTOP_FOLLOWUPS to record.followups.map { f ->
            val case = record.surgicalCases.firstOrNull { it.id == f.surgicalCaseId }
            val files = record.attachmentsForFollowup(f.id).size
            EntryLine(f.id, listOfNotNull(formatDate(f.visitDate), opt(f.woundStatus)).joinToString(" · "),
                listOfNotNull(
                    case?.plannedOperation ?: case?.diagnosis, f.notes,
                    if (files > 0) pluralStringResource(R.plurals.file_count, files, files) else null,
                ).joinToString(" — "))
        },
    )
    // Investigations go after the examination, before surgery.
    val beforeAll = sections.takeWhile { it.first != RecordTable.SURGICAL_CASES }
    val beforeInvestigations = beforeAll.filter { visible(it.first.section) }
    val afterInvestigations = sections.drop(beforeAll.size).filter { visible(it.first.section) }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (visible(RecordSection.IDENTIFIERS)) item { PersonalDataCard(record, editable, onEdit) }
        items(beforeInvestigations, key = { it.first.tableName }) { (table, lines) ->
            RecordSectionCard(record, table, lines, editable, onEdit)
        }
        if (visible(RecordSection.INVESTIGATIONS)) {
            item(key = "investigations") { InvestigationsCard(record, editable, onEdit, onOpenInvestigation) }
        }
        items(afterInvestigations, key = { it.first.tableName }) { (table, lines) ->
            RecordSectionCard(record, table, lines, editable, onEdit)
        }
    }
}

@Composable
private fun RecordSectionCard(
    record: PatientRecord,
    table: RecordTable,
    lines: List<EntryLine>,
    editable: Boolean,
    onEdit: (RecordTable, String?) -> Unit,
) {
    val lang = currentAppLanguage()
    // Social history is a single entry: "Add" becomes "Edit" once it exists.
    val single = table == RecordTable.SOCIAL_HISTORY
    val noOperations = table == RecordTable.POSTOP_FOLLOWUPS && record.surgicalCases.isEmpty()
    val canAdd = editable && !(single && lines.isNotEmpty()) && !noOperations
    SectionCard(
        title = Vocabulary.section(table).get(lang),
        lines = lines,
        onAdd = if (canAdd) ({ onEdit(table, null) }) else null,
        onOpen = { id -> onEdit(table, id) },
        emptyHint = if (noOperations) stringResource(R.string.no_surgical_cases) else null,
    )
}

/**
 * Requests (with their status) and results typed without a request.
 * A request opens its own screen with the results and the review button.
 */
@Composable
private fun InvestigationsCard(
    record: PatientRecord,
    editable: Boolean,
    onEdit: (RecordTable, String?) -> Unit,
    onOpenInvestigation: (String) -> Unit,
) {
    val lang = currentAppLanguage()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.section_investigations), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
            }
            if (editable) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { onEdit(RecordTable.INVESTIGATION_REQUESTS, null) }, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.new_request))
                    }
                    TextButton(onClick = { onEdit(RecordTable.INVESTIGATION_RESULTS, null) }, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_result))
                    }
                }
            }
            val standalone = record.investigationResults.filter { r -> r.requestId == null || record.investigationRequests.none { it.id == r.requestId } }
            if (record.investigationRequests.isEmpty() && standalone.isEmpty()) {
                Text(stringResource(R.string.no_entries), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            record.investigationRequests.forEach { r ->
                val ready = InvestigationRules.isAwaitingReview(r)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = TouchTarget).clickable { onOpenInvestigation(r.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(r.tests.joinToString(", "), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (ready) FontWeight.Bold else FontWeight.Normal)
                    Text(
                        listOfNotNull(Vocabulary.option(r.status).get(lang), Vocabulary.option(r.urgency).get(lang).takeIf { r.urgency != "routine" },
                            formatDate(r.requestedAt)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            standalone.forEach { res ->
                val files = record.attachmentsForResult(res.id).size
                Column(
                    Modifier.fillMaxWidth().heightIn(min = TouchTarget).clickable { onEdit(RecordTable.INVESTIGATION_RESULTS, res.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(res.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(formatDate(res.resultDate), LabValueFormatter.summary(res.labValues),
                            if (files > 0) pluralStringResource(R.plurals.file_count, files, files) else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalDataCard(record: PatientRecord, editable: Boolean, onEdit: (RecordTable, String?) -> Unit) {
    val lang = currentAppLanguage()
    val p = record.patient
    val fields = listOf(
        "date_of_birth" to formatDate(p.dateOfBirth),
        "national_id" to p.nationalId,
        "phone" to p.phone,
        "address" to p.address,
        "emergency_contact_name" to listOfNotNull(p.emergencyContactName, p.emergencyContactPhone).joinToString(" · ").ifBlank { null },
    ).filter { !it.second.isNullOrBlank() }
    SectionCard(
        title = Vocabulary.section(RecordTable.PATIENTS).get(lang),
        lines = if (fields.isEmpty()) emptyList() else listOf(
            EntryLine(p.id, fields.joinToString("\n") { (k, v) -> "${Vocabulary.field(k).get(lang)}: $v" }, null),
        ),
        onAdd = null,
        onOpen = { if (editable) onEdit(RecordTable.PATIENTS, p.id) },
        emptyHint = null,
        trailingEdit = if (editable) ({ onEdit(RecordTable.PATIENTS, p.id) }) else null,
    )
}

@Composable
private fun SectionCard(
    title: String,
    lines: List<EntryLine>,
    onAdd: (() -> Unit)?,
    onOpen: (String) -> Unit,
    emptyHint: String?,
    trailingEdit: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                onAdd?.let {
                    TextButton(onClick = it, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_entry))
                    }
                }
                trailingEdit?.let {
                    TextButton(onClick = it, modifier = Modifier.heightIn(min = TouchTarget)) { Text(stringResource(R.string.edit)) }
                }
            }
            if (lines.isEmpty()) {
                Text(
                    emptyHint ?: stringResource(R.string.no_entries),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            lines.forEach { line ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = TouchTarget).clickable { onOpen(line.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(line.title, style = MaterialTheme.typography.bodyLarge)
                    line.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
