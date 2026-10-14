package com.myclinic.app.ui.patients

import com.myclinic.shared.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.record.Allergy
import com.myclinic.domain.record.Dates
import com.myclinic.domain.record.TimelineKind
import com.myclinic.domain.record.VitalSign

/** Allergy alert shown at the top of every patient screen. Red when any allergy is recorded. */
@Composable
fun AllergyBanner(allergies: List<Allergy>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val hasAllergies = allergies.isNotEmpty()
    val language = currentAppLanguage()
    val text = if (hasAllergies) {
        stringResource(
            Res.string.allergies_banner,
            allergies.joinToString(" · ") { a ->
                val severity = Vocabulary.option(a.severity).get(language)
                "${a.allergen} ($severity)"
            },
        )
    } else {
        stringResource(Res.string.no_allergies_recorded)
    }
    Surface(
        color = if (hasAllergies) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (hasAllergies) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasAllergies) Icon(Icons.Filled.Warning, contentDescription = null)
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (hasAllergies) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** Shows offline state, changes waiting to upload, and changes the server refused. */
@Composable
fun SyncBanner(pending: Int, failed: Int, offline: Boolean, onDiscardFailed: () -> Unit) {
    if (failed > 0) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pluralStringResource(Res.plurals.sync_failed, failed, failed), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.sync_failed_body), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDiscardFailed) { Text(stringResource(Res.string.discard_failed)) }
            }
        }
    }
    if (offline || pending > 0) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    if (offline) stringResource(Res.string.offline) else pluralStringResource(Res.plurals.sync_pending, pending, pending),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ---- Formatting helpers ----

fun formatDate(iso: String?): String? = Dates.localDate(iso)?.let(::formatLocalDate)

fun formatDateTime(iso: String?): String? = Dates.instant(iso)?.let(::formatLocalDateTime)

@Composable
fun FieldError.message(): String = stringResource(
    when (this) {
        FieldError.REQUIRED -> Res.string.field_required
        FieldError.TOO_LONG -> Res.string.field_too_long
        FieldError.NOT_A_NUMBER -> Res.string.field_not_number
        FieldError.OUT_OF_RANGE -> Res.string.field_out_of_range
        FieldError.INVALID_DATE -> Res.string.field_invalid_date
        FieldError.DATE_IN_FUTURE -> Res.string.field_future_date
    },
)

@Composable
fun TimelineKind.label(): String = stringResource(
    when (this) {
        TimelineKind.PATIENT_ADDED -> Res.string.tl_patient_added
        TimelineKind.COMPLAINT -> Res.string.tl_complaint
        TimelineKind.CONDITION_DIAGNOSED -> Res.string.tl_condition
        TimelineKind.PAST_SURGERY -> Res.string.tl_past_surgery
        TimelineKind.MEDICATION_STARTED -> Res.string.tl_med_started
        TimelineKind.MEDICATION_STOPPED -> Res.string.tl_med_stopped
        TimelineKind.ALLERGY_RECORDED -> Res.string.tl_allergy
        TimelineKind.EXAMINATION -> Res.string.tl_examination
        TimelineKind.OPERATION_PLANNED -> Res.string.tl_operation_planned
        TimelineKind.OPERATION_DONE -> Res.string.tl_operation_done
        TimelineKind.FOLLOW_UP -> Res.string.tl_follow_up
        TimelineKind.INVESTIGATION_REQUESTED -> Res.string.tl_investigation_requested
        TimelineKind.INVESTIGATION_RESULT -> Res.string.tl_investigation_result
    },
)

@Composable
fun VitalSign.label(): String = stringResource(
    when (this) {
        VitalSign.PULSE -> Res.string.vital_pulse
        VitalSign.SYSTOLIC -> Res.string.vital_systolic
        VitalSign.DIASTOLIC -> Res.string.vital_diastolic
        VitalSign.RESP_RATE -> Res.string.vital_resp_rate
        VitalSign.TEMPERATURE -> Res.string.vital_temperature
        VitalSign.SPO2 -> Res.string.vital_spo2
        VitalSign.WEIGHT -> Res.string.vital_weight
        VitalSign.PAIN -> Res.string.vital_pain
    },
)

/** Localized option label (sex, severity, status...). */
@Composable
fun optionLabel(value: String?): String? = value?.let { Vocabulary.option(it).get(currentAppLanguage()) }

@Composable
fun sexAgeLine(sex: String, age: Int?, fileNumber: String?): String = listOfNotNull(
    age?.let { stringResource(Res.string.age_years_short, it) },
    optionLabel(sex)?.takeIf { sex != "unknown" },
    fileNumber?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.file_number_short, it) },
).joinToString(" · ")
