package com.myclinic.app.ui.patients

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myclinic.app.R
import com.myclinic.app.ui.components.currentAppLanguage
import com.myclinic.domain.forms.FieldError
import com.myclinic.domain.forms.Vocabulary
import com.myclinic.domain.record.Allergy
import com.myclinic.domain.record.Dates
import com.myclinic.domain.record.TimelineKind
import com.myclinic.domain.record.VitalSign
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Allergy alert shown at the top of every patient screen. Red when any allergy is recorded. */
@Composable
fun AllergyBanner(allergies: List<Allergy>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val hasAllergies = allergies.isNotEmpty()
    val language = currentAppLanguage()
    val text = if (hasAllergies) {
        stringResource(
            R.string.allergies_banner,
            allergies.joinToString(" · ") { a ->
                val severity = Vocabulary.option(a.severity).get(language)
                "${a.allergen} ($severity)"
            },
        )
    } else {
        stringResource(R.string.no_allergies_recorded)
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
                    Text(pluralStringResource(R.plurals.sync_failed, failed, failed), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.sync_failed_body), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDiscardFailed) { Text(stringResource(R.string.discard_failed)) }
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
                    if (offline) stringResource(R.string.offline) else pluralStringResource(R.plurals.sync_pending, pending, pending),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ---- Formatting helpers ----

private fun locale(): Locale = Locale.getDefault()

fun formatDate(iso: String?): String? = Dates.localDate(iso)?.format(
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()),
)

fun formatDateTime(iso: String?): String? = Dates.instant(iso)?.atZone(ZoneId.systemDefault())?.format(
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale()),
)

@Composable
fun FieldError.message(): String = stringResource(
    when (this) {
        FieldError.REQUIRED -> R.string.field_required
        FieldError.TOO_LONG -> R.string.field_too_long
        FieldError.NOT_A_NUMBER -> R.string.field_not_number
        FieldError.OUT_OF_RANGE -> R.string.field_out_of_range
        FieldError.INVALID_DATE -> R.string.field_invalid_date
        FieldError.DATE_IN_FUTURE -> R.string.field_future_date
    },
)

@Composable
fun TimelineKind.label(): String = stringResource(
    when (this) {
        TimelineKind.PATIENT_ADDED -> R.string.tl_patient_added
        TimelineKind.COMPLAINT -> R.string.tl_complaint
        TimelineKind.CONDITION_DIAGNOSED -> R.string.tl_condition
        TimelineKind.PAST_SURGERY -> R.string.tl_past_surgery
        TimelineKind.MEDICATION_STARTED -> R.string.tl_med_started
        TimelineKind.MEDICATION_STOPPED -> R.string.tl_med_stopped
        TimelineKind.ALLERGY_RECORDED -> R.string.tl_allergy
        TimelineKind.EXAMINATION -> R.string.tl_examination
        TimelineKind.OPERATION_PLANNED -> R.string.tl_operation_planned
        TimelineKind.OPERATION_DONE -> R.string.tl_operation_done
        TimelineKind.FOLLOW_UP -> R.string.tl_follow_up
        TimelineKind.INVESTIGATION_REQUESTED -> R.string.tl_investigation_requested
        TimelineKind.INVESTIGATION_RESULT -> R.string.tl_investigation_result
    },
)

@Composable
fun VitalSign.label(): String = stringResource(
    when (this) {
        VitalSign.PULSE -> R.string.vital_pulse
        VitalSign.SYSTOLIC -> R.string.vital_systolic
        VitalSign.DIASTOLIC -> R.string.vital_diastolic
        VitalSign.RESP_RATE -> R.string.vital_resp_rate
        VitalSign.TEMPERATURE -> R.string.vital_temperature
        VitalSign.SPO2 -> R.string.vital_spo2
        VitalSign.WEIGHT -> R.string.vital_weight
        VitalSign.PAIN -> R.string.vital_pain
    },
)

/** Localized option label (sex, severity, status...). */
@Composable
fun optionLabel(value: String?): String? = value?.let { Vocabulary.option(it).get(currentAppLanguage()) }

@Composable
fun sexAgeLine(sex: String, age: Int?, fileNumber: String?): String = listOfNotNull(
    age?.let { stringResource(R.string.age_years_short, it) },
    optionLabel(sex)?.takeIf { sex != "unknown" },
    fileNumber?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.file_number_short, it) },
).joinToString(" · ")
