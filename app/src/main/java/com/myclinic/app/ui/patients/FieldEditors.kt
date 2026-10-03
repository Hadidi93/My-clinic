package com.myclinic.app.ui.patients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myclinic.app.R
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.domain.forms.ChronicDiseases
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.FieldSpec
import com.myclinic.domain.forms.FieldType
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.record.Dates
import com.myclinic.domain.record.PatientSearch
import com.myclinic.domain.record.SUGGESTED_TAGS
import com.myclinic.domain.record.Facility
import com.myclinic.domain.record.FacilityValidator
import com.myclinic.domain.record.InvestigationRequest
import com.myclinic.domain.record.LabCatalog
import com.myclinic.domain.record.SurgicalCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** What some editors need besides the field itself: the patient's operations and requests, departments. */
data class FieldContext(
    val surgicalCases: List<SurgicalCase> = emptyList(),
    /** Requests a result can be filed under. */
    val openRequests: List<InvestigationRequest> = emptyList(),
    val facilities: List<Facility> = emptyList(),
    val onAddFacility: ((name: String, kind: String, hospital: String) -> Unit)? = null,
    /** Called when a request is picked for a result, so the form can fill in its tests. */
    val onRequestPicked: (InvestigationRequest?) -> Unit = {},
)

/** Draws the right editor for one form field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldEditor(
    field: FieldSpec,
    values: Map<String, String>,
    error: FieldError?,
    context: FieldContext,
    onValue: (String, String) -> Unit,
    onValues: (Map<String, String>) -> Unit,
) {
    val surgicalCases = context.surgicalCases
    val lang = currentAppLanguage()
    val label = Vocabulary.field(field.key).get(lang) + if (field.required) " *" else ""
    val value = values[field.key].orEmpty()
    val errorText = error?.message()
    val set: (String) -> Unit = { onValue(field.key, it) }

    when (field.type) {
        FieldType.TEXT -> AppTextField(value, set, label, error = errorText, capitalization = KeyboardCapitalization.Sentences)
        FieldType.PHONE -> AppTextField(value, set, label, error = errorText, keyboardType = KeyboardType.Phone)
        FieldType.INT -> AppTextField(value, set, label, error = errorText, keyboardType = KeyboardType.Number,
            supportingText = rangeHint(field))
        FieldType.DECIMAL -> AppTextField(value, set, label, error = errorText, keyboardType = KeyboardType.Decimal,
            supportingText = rangeHint(field))
        FieldType.MULTILINE -> OutlinedTextField(
            value = value, onValueChange = set, label = { Text(label) }, minLines = 3,
            isError = errorText != null, supportingText = errorText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        FieldType.DATE -> DateField(label, value, errorText, withTime = false, onChange = set)
        FieldType.DATETIME -> DateField(label, value, errorText, withTime = true, onChange = set)
        FieldType.BOOLEAN -> Row(
            Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                .toggleable(value = value == "true", role = Role.Switch, onValueChange = { set(it.toString()) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = value == "true", onCheckedChange = null)
        }
        FieldType.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    FilterChip(
                        selected = value == option,
                        // Tapping the selected chip clears an optional choice.
                        onClick = { set(if (value == option && !field.required) "" else option) },
                        label = { Text(Vocabulary.option(option).get(lang)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        FieldType.CHECKLIST -> Column {
            val checked = value.split(',').filter { it.isNotBlank() }.toSet()
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.checklist_progress, checked.size, field.options.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            field.options.forEach { item ->
                val on = item in checked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = TouchTarget).toggleable(
                        value = on, role = Role.Checkbox,
                        onValueChange = { set((if (on) checked - item else checked + item).joinToString(",")) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Text(Vocabulary.option(item).get(lang), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        FieldType.TAGS -> if (field.key == "tests") {
            TagsField(label, value, set, suggestions = LabCatalog.REQUEST_SUGGESTIONS[values["kind"]].orEmpty(), hashPrefix = false,
                error = errorText)
        } else {
            TagsField(label, value, set)
        }
        FieldType.LAB_VALUES -> LabValuesEditor(label, value, errorText, set)
        FieldType.FACILITY -> FacilityPicker(
            label = label,
            selectedId = value.ifBlank { null },
            facilities = context.facilities.filter { FacilityValidator.accepts(it.kind, values["kind"].orEmpty()) },
            requestKind = values["kind"].orEmpty(),
            noneLabel = stringResource(R.string.facility_none),
            onSelect = { set(it.orEmpty()) },
            onAdd = context.onAddFacility,
        )
        FieldType.INVESTIGATION_REQUEST -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (context.openRequests.isEmpty()) {
                Text(stringResource(R.string.no_open_requests), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                context.openRequests.forEach { r ->
                    FilterChip(
                        selected = value == r.id,
                        onClick = {
                            val picked = if (value == r.id) null else r
                            set(picked?.id.orEmpty())
                            context.onRequestPicked(picked)
                        },
                        label = { Text(listOfNotNull(r.tests.joinToString(", "), formatDate(r.requestedAt)).joinToString(" · ")) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        FieldType.CONDITION -> ConditionField(label, value, values[FormCodec.CONDITION_CODE_KEY].orEmpty(), errorText, onValues)
        FieldType.SURGICAL_CASE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (surgicalCases.isEmpty()) Text(stringResource(R.string.no_surgical_cases))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                surgicalCases.forEach { c ->
                    FilterChip(
                        selected = value == c.id, onClick = { set(c.id) },
                        label = { Text(listOfNotNull(c.plannedOperation ?: c.diagnosis, formatDate(c.operationDate ?: c.plannedDate)).joinToString(" · ")) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun rangeHint(field: FieldSpec): String? {
    val min = field.min ?: return null
    val max = field.max ?: return null
    fun f(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    return "${f(min)}–${f(max)}"
}

/** A read-only field that opens a calendar (and a clock when [withTime]). Value: "yyyy-MM-dd" or an ISO instant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, error: String?, withTime: Boolean, onChange: (String) -> Unit) {
    var showDate by rememberSaveable { mutableStateOf(false) }
    var pendingDate by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (withTime) formatDateTime(value) else formatDate(value)

    Box {
        OutlinedTextField(
            value = shown.orEmpty(), onValueChange = {}, readOnly = true, label = { Text(label) },
            isError = error != null, supportingText = error?.let { { Text(it) } },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear)) }
                } else {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        )
        // Transparent layer so a tap anywhere on the field (except the clear button) opens the calendar.
        Box(Modifier.matchParentSize().padding(end = 48.dp).clickable { showDate = true })
    }

    if (showDate) {
        val initialDay = Dates.localDate(value) ?: LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (withTime) {
                            pendingDate = day
                        } else {
                            onChange(day)
                        }
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }

    pendingDate?.let { day ->
        val initialTime = Dates.instant(value)?.atZone(ZoneId.systemDefault())?.toLocalTime() ?: LocalTime.now()
        val timeState = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute)
        AlertDialog(
            onDismissRequest = { pendingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    val instant = LocalDate.parse(day).atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant()
                    onChange(instant.toString())
                    pendingDate = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pendingDate = null }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = timeState) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suggestions: List<String> = SUGGESTED_TAGS,
    hashPrefix: Boolean = true,
    error: String? = null,
) {
    val prefix = if (hashPrefix) "#" else ""
    val tags = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    var input by remember { mutableStateOf("") }
    fun add(tag: String) {
        val t = tag.trim().removePrefix("#")
        if (t.isNotEmpty() && t !in tags) onChange((tags + t).joinToString(","))
        input = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = true, onClick = { onChange((tags - tag).joinToString(",")) },
                    label = { Text("$prefix$tag") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(input, { text -> if (text.contains(',')) { add(text.substringBefore(',')) } else { input = text } },
                stringResource(if (hashPrefix) R.string.add_tag else R.string.add_test), modifier = Modifier.weight(1f), onImeAction = { add(input) })
            TextButton(onClick = { add(input) }, enabled = input.isNotBlank()) { Text(stringResource(R.string.add_entry)) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val remaining = suggestions.filter { it !in tags }
        if (remaining.isNotEmpty()) {
            Text(stringResource(R.string.suggested_tags), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                remaining.forEach { tag ->
                    FilterChip(selected = false, onClick = { add(tag) }, label = { Text("$prefix$tag") }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
}

/** Free text with quick picks from the chronic-disease list (matches English or Arabic names). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionField(label: String, name: String, code: String, error: String?, onValues: (Map<String, String>) -> Unit) {
    val lang = currentAppLanguage()
    val picked = ChronicDiseases.byCode(code)
    val shownName = picked?.name?.get(lang) ?: name
    val query = PatientSearch.normalize(if (picked != null) "" else name)
    val suggestions = ChronicDiseases.ALL.filter {
        query.isEmpty() || PatientSearch.normalize(it.name.en).contains(query) || PatientSearch.normalize(it.name.ar).contains(query)
    }.take(10)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppTextField(
            value = shownName,
            // Typing makes it free text (clears the list code).
            onValueChange = { onValues(mapOf("name" to it, FormCodec.CONDITION_CODE_KEY to "")) },
            label = label, error = error, supportingText = stringResource(R.string.pick_condition_hint),
            capitalization = KeyboardCapitalization.Sentences,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { d ->
                FilterChip(
                    selected = d.code == code,
                    // The English name is stored (consistent search); the app shows it in the user's language.
                    onClick = { onValues(mapOf("name" to d.name.en, FormCodec.CONDITION_CODE_KEY to d.code)) },
                    label = { Text(d.name.get(lang)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
