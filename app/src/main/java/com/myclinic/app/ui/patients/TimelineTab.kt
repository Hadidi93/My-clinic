package com.myclinic.app.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myclinic.app.R
import com.myclinic.domain.record.Dates
import com.myclinic.domain.record.RecordTable
import com.myclinic.domain.record.TimelineEvent
import com.myclinic.domain.record.TimelineKind

/** Every event for the patient, newest first, grouped by day. Tap an event to open it. */
@Composable
fun TimelineTab(events: List<TimelineEvent>, onOpen: (RecordTable, String) -> Unit) {
    if (events.isEmpty()) {
        Text(stringResource(R.string.timeline_empty), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        return
    }
    val byDay = events.groupBy { Dates.localDate(it.at) }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        byDay.forEach { (day, dayEvents) ->
            item(key = "day-$day") {
                Text(
                    formatDate(day?.toString()).orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            items(dayEvents, key = { "${it.table}-${it.recordId}-${it.kind}" }) { event ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(event.table, event.recordId) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(event.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(event.kind.label(), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (event.title.isNotBlank()) Text(event.title, style = MaterialTheme.typography.bodyLarge)
                        event.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun TimelineKind.icon(): ImageVector = when (this) {
    TimelineKind.PATIENT_ADDED -> Icons.Filled.PersonAdd
    TimelineKind.COMPLAINT -> Icons.Filled.RecordVoiceOver
    TimelineKind.CONDITION_DIAGNOSED -> Icons.AutoMirrored.Filled.EventNote
    TimelineKind.PAST_SURGERY, TimelineKind.OPERATION_PLANNED, TimelineKind.OPERATION_DONE -> Icons.Filled.LocalHospital
    TimelineKind.MEDICATION_STARTED, TimelineKind.MEDICATION_STOPPED -> Icons.Filled.Medication
    TimelineKind.ALLERGY_RECORDED -> Icons.Filled.Warning
    TimelineKind.EXAMINATION -> Icons.Filled.MonitorHeart
    TimelineKind.FOLLOW_UP -> Icons.Filled.Healing
}
