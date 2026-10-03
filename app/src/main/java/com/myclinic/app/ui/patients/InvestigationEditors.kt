package com.myclinic.app.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myclinic.app.R
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.domain.forms.FormCodec
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.record.Facility
import com.myclinic.domain.record.FacilityValidator
import com.myclinic.domain.record.LabCatalog
import com.myclinic.domain.record.LabValue
import com.myclinic.domain.record.LabValueFormatter

/** One row of the values editor, as typed (numbers stay text until saved, so "11." can be typed). */
private data class LabRowText(
    val test: String = "",
    val value: String = "",
    val unit: String = "",
    val low: String = "",
    val high: String = "",
) {
    fun toLabValue(): LabValue {
        val number = FormCodec.parseDecimal(value.trim())
        return LabValue(
            test = test.trim(),
            value = number,
            text = value.trim().takeIf { number == null && it.isNotEmpty() },
            unit = unit.trim().ifEmpty { null },
            low = FormCodec.parseDecimal(low.trim()),
            high = FormCodec.parseDecimal(high.trim()),
        )
    }

    companion object {
        fun from(v: LabValue) = LabRowText(
            test = v.test,
            value = v.value?.let(::plain) ?: v.text.orEmpty(),
            unit = v.unit.orEmpty(),
            low = v.low?.let(::plain).orEmpty(),
            high = v.high?.let(::plain).orEmpty(),
        )

        private fun plain(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    }
}

/**
 * Typed results: one row per test (name, value, unit, reference range).
 * Quick chips add common tests with their usual unit and range.
 * The value text is the JSON list (see FormCodec LAB_VALUES).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabValuesEditor(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    val rows = remember { mutableStateListOf<LabRowText>() }
    var emitted by remember { mutableStateOf<String?>(null) }
    if (value != emitted) {
        // Set from outside (opening an entry, or picking a request): show those values.
        rows.clear()
        rows.addAll(FormCodec.parseLabValues(value).orEmpty().map(LabRowText::from))
        emitted = value
    }
    fun emit() {
        val text = FormCodec.labValuesText(rows.map { it.toLabValue() })
        emitted = text
        onChange(text)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        rows.forEachIndexed { i, row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppTextField(row.test, { rows[i] = row.copy(test = it); emit() }, stringResource(R.string.lab_test),
                            modifier = Modifier.weight(1.3f))
                        AppTextField(row.value, { rows[i] = row.copy(value = it); emit() }, stringResource(R.string.lab_value),
                            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                        IconButton(onClick = { rows.removeAt(i); emit() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.lab_remove_value))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppTextField(row.unit, { rows[i] = row.copy(unit = it); emit() }, stringResource(R.string.lab_unit),
                            modifier = Modifier.weight(1f))
                        AppTextField(row.low, { rows[i] = row.copy(low = it); emit() }, stringResource(R.string.lab_low),
                            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                        AppTextField(row.high, { rows[i] = row.copy(high = it); emit() }, stringResource(R.string.lab_high),
                            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    }
                    val v = row.toLabValue()
                    if (v.isAbnormal) {
                        Text(stringResource(R.string.outside_range) + " " + LabValueFormatter.one(v),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { rows.add(LabRowText()) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.lab_add_value))
        }
        val present = rows.map { it.test.trim().lowercase() }.toSet()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LabCatalog.TESTS.filter { it.name.lowercase() !in present }) { t ->
                AssistChip(
                    onClick = {
                        rows.add(LabRowText(t.name, unit = t.unit.orEmpty(), low = t.low?.let(::fmtNumber).orEmpty(),
                            high = t.high?.let(::fmtNumber).orEmpty()))
                        emit()
                    },
                    label = { Text(t.name) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

private fun fmtNumber(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/** Read-only table of typed values; abnormal values in bold with an arrow. */
@Composable
fun LabValuesTable(values: List<LabValue>) {
    Column {
        values.forEach { v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(v.test, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        LabValueFormatter.one(v).removePrefix(v.test).trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (v.isAbnormal) FontWeight.Bold else FontWeight.Normal,
                        color = if (v.isAbnormal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    LabValueFormatter.range(v)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** The department kind that handles a kind of request. */
fun facilityKindFor(investigationKind: String): String = when (investigationKind) {
    "lab" -> "lab"
    "imaging" -> "radiology"
    "pathology" -> "pathology"
    else -> "other"
}

/**
 * Pick a department, or none. [onAdd] (if given) offers "Add department";
 * [requestKind] fixes the new department's kind (blank = the user chooses).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FacilityPicker(
    label: String,
    selectedId: String?,
    facilities: List<Facility>,
    requestKind: String,
    noneLabel: String?,
    onSelect: (String?) -> Unit,
    onAdd: ((name: String, kind: String, hospital: String) -> Unit)?,
    error: String? = null,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            noneLabel?.let {
                FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text(it) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
            facilities.forEach { f ->
                FilterChip(selected = selectedId == f.id, onClick = { onSelect(f.id) }, label = { Text(f.label) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
            onAdd?.let {
                AssistChip(onClick = { adding = true }, label = { Text(stringResource(R.string.facility_add)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (adding && onAdd != null) {
        AddFacilityDialog(
            fixedKind = requestKind.takeIf { it.isNotBlank() }?.let(::facilityKindFor),
            existing = facilities,
            onDismiss = { adding = false },
            onAdd = { name, kind, hospital -> adding = false; onAdd(name, kind, hospital) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFacilityDialog(
    fixedKind: String?,
    existing: List<Facility>,
    onDismiss: () -> Unit,
    onAdd: (name: String, kind: String, hospital: String) -> Unit,
) {
    val lang = currentAppLanguage()
    var name by rememberSaveable { mutableStateOf("") }
    var hospital by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(fixedKind ?: "lab") }
    val duplicate = FacilityValidator.isDuplicate(existing, name, kind, hospital)
    val valid = FacilityValidator.isValidName(name) && !duplicate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.facility_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.facility_add_help), style = MaterialTheme.typography.bodyMedium)
                if (fixedKind == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FacilityValidator.KINDS.forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(Vocabulary.option(k).get(lang)) },
                                modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.facility_name)) }, singleLine = true,
                    isError = duplicate, supportingText = if (duplicate) ({ Text(stringResource(R.string.error_duplicate)) }) else null,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hospital, { hospital = it }, label = { Text(stringResource(R.string.hospital)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, kind, hospital) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
