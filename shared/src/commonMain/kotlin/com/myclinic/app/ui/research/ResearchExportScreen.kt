package com.myclinic.app.ui.research

import com.myclinic.shared.resources.*
import org.koin.compose.viewmodel.koinViewModel
import com.myclinic.domain.record.Dates
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.myclinic.app.ui.components.rememberFileSharer
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.myclinic.app.data.DataError
import com.myclinic.app.data.audit.AuditRepository
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.export.ExportedFile
import com.myclinic.app.data.export.ResearchZipWriter
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.data.toDataError
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecureScreen
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.consults.ConsentCheckbox
import com.myclinic.domain.record.PatientQuery
import com.myclinic.domain.record.PatientRecord
import com.myclinic.domain.record.PatientSearch
import com.myclinic.domain.record.PatientSummaries
import com.myclinic.domain.record.ResearchExport
import com.myclinic.domain.record.ResearchExportOptions
import com.myclinic.domain.record.StatusFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResearchForm(
    val text: String = "",
    val status: StatusFilter = StatusFilter.ALL,
    val options: ResearchExportOptions = ResearchExportOptions(),
    val ethicsConfirmed: Boolean = false,
    val working: Boolean = false,
    val share: ExportedFile? = null,
    val error: DataError? = null,
)

data class ResearchUiState(val form: ResearchForm = ResearchForm(), val matching: List<PatientRecord> = emptyList())

class ResearchExportViewModel(
    patients: PatientRepository,
    doctors: DoctorRepository,
    private val audit: AuditRepository,
    private val exporter: ResearchZipWriter,
) : ViewModel() {
    private val form = MutableStateFlow(ResearchForm())

    val state: StateFlow<ResearchUiState> = combine(patients.records, doctors.myProfile, form) { records, me, f ->
        val today = Dates.today()
        // Only the doctor's own patients, never co-managed or deleted ones.
        val mine = records.filter { me != null && it.patient.ownerId == me.id && !it.patient.isDeleted }
        val summaries = mine.map { PatientSummaries.from(it, today) }
        val ids = PatientSearch.filter(summaries, PatientQuery(text = f.text, status = f.status), today).map { it.patient.id }.toSet()
        ResearchUiState(f, mine.filter { it.patient.id in ids })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResearchUiState())

    fun onText(text: String) = form.update { it.copy(text = text) }
    fun onStatus(status: StatusFilter) = form.update { it.copy(status = status) }
    fun onFullDates(on: Boolean) = form.update { it.copy(options = it.options.copy(fullDates = on)) }
    fun onFreeText(on: Boolean) = form.update { it.copy(options = it.options.copy(freeText = on)) }
    fun onEthics(on: Boolean) = form.update { it.copy(ethicsConfirmed = on) }

    /** Logs the export on the server first; offline (or refused) means no file is made. */
    fun export(title: String) {
        val s = state.value
        if (!s.form.ethicsConfirmed || s.matching.isEmpty() || s.form.working) return
        form.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            audit.logResearchExport(s.matching.map { it.patient.id })
                .onFailure { e -> form.update { it.copy(working = false, error = e.toDataError()) } }
                .onSuccess {
                    runCatching {
                        val today = Dates.today()
                        exporter.write(ResearchExport.build(s.matching, s.form.options, today), today, title)
                    }
                        .onSuccess { intent -> form.update { it.copy(working = false, share = intent) } }
                        .onFailure { form.update { it.copy(working = false, error = DataError.UNKNOWN) } }
                }
        }
    }

    fun onShared() = form.update { it.copy(share = null) }
}

/**
 * De-identified spreadsheet of the doctor's own patients for research or a
 * service review: choose who (search + status), what (dates, free text),
 * confirm approval, then share the ZIP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchExportScreen(onBack: () -> Unit, viewModel: ResearchExportViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val f = state.form
    val share = rememberFileSharer()
    val title = stringResource(Res.string.research_export_title)
    LaunchedEffect(f.share) {
        f.share?.let { share(it); viewModel.onShared() }
    }

    SecureScreen {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp).widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MessageCard(
                    title = stringResource(Res.string.research_intro_title),
                    body = stringResource(Res.string.research_intro_body),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Text(stringResource(Res.string.research_which_patients), style = MaterialTheme.typography.titleMedium)
                AppTextField(f.text, viewModel::onText, stringResource(Res.string.research_search_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        StatusFilter.ALL to Res.string.status_all,
                        StatusFilter.ACTIVE to Res.string.status_current,
                        StatusFilter.DISCHARGED to Res.string.status_discharged,
                    ).forEach { (s, label) ->
                        FilterChip(selected = f.status == s, onClick = { viewModel.onStatus(s) },
                            label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text(
                    pluralStringResource(Res.plurals.research_patient_count, state.matching.size, state.matching.size),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                )

                Text(stringResource(Res.string.research_what), style = MaterialTheme.typography.titleMedium)
                OptionSwitch(stringResource(Res.string.research_full_dates), stringResource(Res.string.research_full_dates_help),
                    f.options.fullDates, viewModel::onFullDates)
                OptionSwitch(stringResource(Res.string.research_free_text), stringResource(Res.string.research_free_text_help),
                    f.options.freeText, viewModel::onFreeText)

                ConsentCheckbox(f.ethicsConfirmed, viewModel::onEthics, stringResource(Res.string.research_ethics))
                Text(stringResource(Res.string.research_logged), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                f.error?.let { ErrorMessage(it.message()) }
                PrimaryButton(
                    stringResource(Res.string.research_export_button),
                    onClick = { viewModel.export(title) },
                    loading = f.working,
                    enabled = f.ethicsConfirmed && state.matching.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun OptionSwitch(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
