package com.myclinic.app.ui.patients

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myclinic.app.R
import com.myclinic.domain.record.Examination
import com.myclinic.domain.record.InvestigationResult
import com.myclinic.domain.record.LabTrends
import java.time.Instant
import com.myclinic.domain.record.VitalSign
import com.myclinic.domain.record.VitalsSeries
import kotlin.math.abs

/** What the trends tab can show: a vital sign, or a lab test from typed results. */
sealed interface Trend {
    data class Vital(val sign: VitalSign) : Trend
    data class Lab(val test: String) : Trend
}

/** One point on the chart, whatever it measures. */
data class ChartPoint(val at: Instant, val value: Double, val abnormal: Boolean)

/** Trends: pick a vital sign or a lab test, see its chart, and a table of every reading. */
@Composable
fun VitalsTab(
    examinations: List<Examination>,
    results: List<InvestigationResult>,
    available: List<Trend>,
    selected: Trend?,
    onSelect: (Trend) -> Unit,
) {
    if (selected == null) {
        Text(stringResource(R.string.no_vitals), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        return
    }
    val series = when (selected) {
        is Trend.Vital -> TrendSeries(
            label = selected.sign.label(),
            unit = selected.sign.unit,
            low = selected.sign.normalLow,
            high = selected.sign.normalHigh,
            points = VitalsSeries.series(examinations, selected.sign).map { ChartPoint(it.at, it.value, it.abnormal) },
        )
        is Trend.Lab -> {
            val lab = LabTrends.series(results, selected.test)
            TrendSeries(
                label = selected.test,
                unit = lab.lastOrNull()?.unit.orEmpty(),
                // The lab's own reference range, from the latest result that gives one.
                low = lab.lastOrNull { it.low != null }?.low,
                high = lab.lastOrNull { it.high != null }?.high,
                points = lab.map { ChartPoint(it.at, it.value, it.abnormal) },
            )
        }
    }
    TrendContent(series, available, selected, onSelect)
}

private data class TrendSeries(val label: String, val unit: String, val low: Double?, val high: Double?, val points: List<ChartPoint>)

@Composable
private fun Trend.chipLabel(): String = when (this) {
    is Trend.Vital -> sign.label()
    is Trend.Lab -> test
}

@Composable
private fun TrendContent(selectedSeries: TrendSeries, available: List<Trend>, selected: Trend, onSelect: (Trend) -> Unit) {
    val points = selectedSeries.points
    val unitSuffix = selectedSeries.unit.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(available) { trend ->
                    FilterChip(selected = trend == selected, onClick = { onSelect(trend) }, label = { Text(trend.chipLabel()) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // A single series: the title names it, so no legend box is needed.
                Text(selectedSeries.label + unitSuffix, style = MaterialTheme.typography.titleMedium)
                VitalsChart(points, selectedSeries)
                val low = selectedSeries.low
                val high = selectedSeries.high
                if (low != null && high != null) {
                    Text(
                        stringResource(R.string.vital_normal_range, fmt(low), fmt(high), selectedSeries.unit),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val abnormal = points.count { it.abnormal }
                if (abnormal > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(pluralStringResource(R.plurals.vital_abnormal_count, abnormal, abnormal),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.vital_readings), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        }
        // The table view: every value in text, newest first (the chart is never the only way to read the data).
        items(points.reversed()) { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(p.at.toString()).orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (p.abnormal) {
                    Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.outside_range),
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 6.dp))
                }
                Text("${fmt(p.value)} ${selectedSeries.unit}".trim(), style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()
        }
    }
}

private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString()
    else java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/**
 * Line chart drawn by hand on a Canvas (no chart library needed):
 * recessive grid, shaded typical range, 2dp line, 8dp dots with a 2dp surface
 * ring, out-of-range dots in the error colour. Tap a dot to see its value.
 * Time always runs left to right, also in Arabic.
 */
@Composable
private fun VitalsChart(points: List<ChartPoint>, sign: TrendSeries) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)
    var selectedIndex by remember(points) { mutableStateOf(points.lastIndex) }
    val description = stringResource(R.string.chart_description, sign.label, points.size)

    val values = points.map { it.value } + listOfNotNull(sign.low, sign.high)
    val rawMin = values.minOrNull() ?: 0.0
    val rawMax = values.maxOrNull() ?: 1.0
    val pad = ((rawMax - rawMin).takeIf { it > 0 } ?: 1.0) * 0.1
    val yMin = rawMin - pad
    val yMax = rawMax + pad
    val tMin = points.firstOrNull()?.at?.toEpochMilli() ?: 0L
    val tMax = points.lastOrNull()?.at?.toEpochMilli() ?: 1L

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val left = 44.dp.toPx(); val right = size.width - 12.dp.toPx()
                    val xs = points.indices.map { i -> xFor(points[i].at.toEpochMilli(), tMin, tMax, left, right) }
                    xs.withIndex().minByOrNull { abs(it.value - tap.x) }?.let { selectedIndex = it.index }
                }
            },
    ) {
        val left = 44.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        fun y(v: Double) = (bottom - (v - yMin) / (yMax - yMin) * (bottom - top)).toFloat()

        // Typical range band (recessive).
        val low = sign.low
        val high = sign.high
        if (low != null && high != null) {
            val yTop = y(high.coerceAtMost(yMax))
            val yBottom = y(low.coerceAtLeast(yMin))
            drawRect(colors.primary.copy(alpha = 0.10f), topLeft = Offset(left, yTop), size = Size(right - left, yBottom - yTop))
        }
        // Horizontal grid + y labels.
        repeat(4) { i ->
            val v = yMin + (yMax - yMin) * i / 3.0
            val gy = y(v)
            drawLine(colors.outlineVariant, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(fmt(Math.round(v * 10) / 10.0), labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), gy - text.size.height / 2f))
        }
        if (points.isEmpty()) return@Canvas

        val xy = points.map { Offset(xFor(it.at.toEpochMilli(), tMin, tMax, left, right), y(it.value)) }
        // Line.
        if (xy.size > 1) {
            val path = Path().apply { moveTo(xy[0].x, xy[0].y); xy.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, colors.primary, style = Stroke(width = 2.dp.toPx()))
        }
        // Selected-point crosshair.
        val sel = points.getOrNull(selectedIndex)
        val selXY = xy.getOrNull(selectedIndex)
        if (sel != null && selXY != null) {
            drawLine(colors.outline, Offset(selXY.x, top), Offset(selXY.x, bottom), strokeWidth = 1.dp.toPx())
        }
        // Dots with a surface ring so they stay distinct where the line passes through.
        points.forEachIndexed { i, p ->
            val c = xy[i]
            val r = if (i == selectedIndex) 6.dp.toPx() else 4.dp.toPx()
            drawCircle(colors.surface, radius = r + 2.dp.toPx(), center = c)
            drawCircle(if (p.abnormal) colors.error else colors.primary, radius = r, center = c)
        }
        // x labels: first and last date.
        val first = measurer.measure(formatDate(points.first().at.toString()).orEmpty(), labelStyle)
        drawText(first, topLeft = Offset(left, bottom + 6.dp.toPx()))
        if (points.size > 1) {
            val last = measurer.measure(formatDate(points.last().at.toString()).orEmpty(), labelStyle)
            drawText(last, topLeft = Offset(right - last.size.width, bottom + 6.dp.toPx()))
        }
        // Tooltip for the selected point: value in text ink, never in the series colour.
        if (sel != null && selXY != null) {
            val label = measurer.measure(
                "${fmt(sel.value)} ${sign.unit} · ${formatDateTime(sel.at.toString()).orEmpty()}".replace("  ", " "),
                TextStyle(fontSize = 12.sp, color = colors.onSurface),
            )
            val w = label.size.width + 12.dp.toPx()
            val h = label.size.height + 8.dp.toPx()
            val bx = (selXY.x - w / 2).coerceIn(left, right - w)
            val by = (selXY.y - h - 10.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(colors.surfaceContainerHigh, topLeft = Offset(bx, by), size = Size(w, h), cornerRadius = CornerRadius(8.dp.toPx()))
            drawText(label, topLeft = Offset(bx + 6.dp.toPx(), by + 4.dp.toPx()))
        }
    }
}

private fun xFor(t: Long, tMin: Long, tMax: Long, left: Float, right: Float): Float =
    if (tMax == tMin) (left + right) / 2 else left + (t - tMin).toFloat() / (tMax - tMin) * (right - left)
