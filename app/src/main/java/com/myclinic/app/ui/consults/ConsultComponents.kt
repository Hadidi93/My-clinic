package com.myclinic.app.ui.consults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myclinic.app.R
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.domain.consult.DoctorCard
import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.model.Specialties
import com.myclinic.app.ui.components.currentAppLanguage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip

/**
 * Find a colleague by name, specialty or hospital. Only verified doctors are
 * listed (lab staff and unverified accounts never appear).
 */
@Composable
fun DoctorPicker(
    query: String,
    onQuery: (String) -> Unit,
    results: List<DoctorCard>,
    searching: Boolean,
    selected: DoctorCard?,
    onSelect: (DoctorCard) -> Unit,
    error: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.choose_colleague), style = MaterialTheme.typography.titleMedium)
        if (selected != null) {
            DoctorRow(selected, selected = true, onClick = null)
        }
        AppTextField(query, onQuery, stringResource(R.string.search_doctors_hint), error = error)
        // One tap searches a specialty.
        if (selected == null || query.isNotEmpty()) {
            val lang = currentAppLanguage()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Specialties.ALL) { s ->
                    FilterChip(
                        selected = query.trim().equals(s.en, ignoreCase = true),
                        onClick = { onQuery(s.en) },
                        label = { Text(s.get(lang)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!searching && query.isNotBlank() && results.isEmpty()) {
            Text(stringResource(R.string.no_doctors_found), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results.filter { it.id != selected?.id }.take(20).forEach { d ->
            DoctorRow(d, selected = false, onClick = { onSelect(d) })
        }
    }
}

@Composable
fun DoctorRow(doctor: DoctorCard, selected: Boolean, onClick: (() -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.padding(12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.Person, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(doctor.fullName, style = MaterialTheme.typography.titleSmall)
                doctorRoleLine(doctor).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                doctor.hospital?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Consultant · Cardiology" (grade and specialty, whichever are filled in). */
@Composable
fun doctorRoleLine(doctor: DoctorCard): String = listOfNotNull(
    optionLabel(doctor.grade),
    doctor.specialty?.takeIf { it.isNotBlank() }?.let { specialtyLabel(it) },
).joinToString(" · ")

/** A specialty in the app's language when it is one of the common ones, else as typed. */
@Composable
fun specialtyLabel(specialty: String): String {
    val lang = currentAppLanguage()
    return Specialties.ALL.firstOrNull { it.en.equals(specialty.trim(), ignoreCase = true) }?.get(lang) ?: specialty
}

/** "Patient consent obtained" checkbox: required before any sharing. */
@Composable
fun ConsentCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, text: String, error: String? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Label of a shareable section, e.g. "Allergies". */
@Composable
fun RecordSection.label(): String = stringResource(
    when (this) {
        RecordSection.IDENTIFIERS -> R.string.section_identifiers
        RecordSection.PRESENTING_COMPLAINT -> R.string.section_presenting_complaint
        RecordSection.PAST_MEDICAL -> R.string.section_past_medical
        RecordSection.PAST_SURGICAL -> R.string.section_past_surgical
        RecordSection.MEDICATIONS -> R.string.section_medications
        RecordSection.ALLERGIES -> R.string.section_allergies
        RecordSection.FAMILY_HISTORY -> R.string.section_family_history
        RecordSection.SOCIAL_HISTORY -> R.string.section_social_history
        RecordSection.EXAMINATION -> R.string.section_examination
        RecordSection.INVESTIGATIONS -> R.string.section_investigations
        RecordSection.SURGICAL_CARE -> R.string.section_surgical_care
    },
)

/** "Anonymous patient · 54 y · Male", or the name when it may be shown. */
@Composable
fun patientLabel(name: String?, age: Int?, sex: String?): String = listOfNotNull(
    name ?: stringResource(R.string.anonymous_patient),
    age?.let { stringResource(R.string.age_years_short, it) },
    optionLabel(sex)?.takeIf { sex != "unknown" },
).joinToString(" · ")
