package com.myclinic.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myclinic.app.R
import com.myclinic.app.ui.components.TouchTarget
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.app.ui.patients.optionLabel
import com.myclinic.domain.model.DoctorGrade
import com.myclinic.domain.model.Specialties
import com.myclinic.domain.record.PatientSearch

/** Resident / Specialist / Consultant, as a drop-down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeDropdown(selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = optionLabel(selected).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.grade)) },
            placeholder = { Text(stringResource(R.string.grade_choose)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget).menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DoctorGrade.ALL.forEach { g ->
                DropdownMenuItem(
                    text = { Text(optionLabel(g).orEmpty()) },
                    onClick = { onSelect(g); open = false },
                    modifier = Modifier.heightIn(min = TouchTarget),
                )
            }
        }
    }
}

/**
 * Quick picks under the specialty field, filtered by what is typed. The
 * English name is saved, so colleagues find each other with the same words.
 */
@Composable
fun SpecialtySuggestions(value: String, onPick: (String) -> Unit) {
    val lang = currentAppLanguage()
    val query = PatientSearch.normalize(value)
    val matches = Specialties.ALL.filter {
        query.isEmpty() || PatientSearch.normalize(it.en).contains(query) || PatientSearch.normalize(it.ar).contains(query)
    }.filterNot { it.en.equals(value.trim(), ignoreCase = true) }
    if (matches.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(matches) { s ->
            FilterChip(selected = false, onClick = { onPick(s.en) }, label = { Text(s.get(lang)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
