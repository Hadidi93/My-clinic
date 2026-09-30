package com.myclinic.domain.record

import java.time.Instant

/**
 * Vital signs that can be plotted. [normalLow]..[normalHigh] is a typical
 * adult reference range used only to shade the chart and flag values; it is
 * a visual aid, not a clinical decision rule.
 */
enum class VitalSign(
    val unit: String,
    val normalLow: Double?,
    val normalHigh: Double?,
    val read: (Examination) -> Double?,
) {
    PULSE("bpm", 60.0, 100.0, { it.pulseBpm?.toDouble() }),
    SYSTOLIC("mmHg", 90.0, 140.0, { it.systolicMmhg?.toDouble() }),
    DIASTOLIC("mmHg", 60.0, 90.0, { it.diastolicMmhg?.toDouble() }),
    RESP_RATE("/min", 12.0, 20.0, { it.respRate?.toDouble() }),
    TEMPERATURE("°C", 36.1, 37.8, { it.temperatureC }),
    SPO2("%", 94.0, 100.0, { it.spo2Percent?.toDouble() }),
    WEIGHT("kg", null, null, { it.weightKg }),
    PAIN("/10", 0.0, 3.0, { it.painScore?.toDouble() });

    fun isAbnormal(value: Double): Boolean =
        (normalLow != null && value < normalLow) || (normalHigh != null && value > normalHigh)
}

data class VitalPoint(val at: Instant, val value: Double, val abnormal: Boolean)

object VitalsSeries {
    /** Points for one vital sign, oldest first, skipping examinations without that value. */
    fun series(examinations: List<Examination>, sign: VitalSign): List<VitalPoint> =
        examinations.mapNotNull { e ->
            val at = Dates.instant(e.examinedAt ?: e.createdAt) ?: return@mapNotNull null
            val v = sign.read(e) ?: return@mapNotNull null
            VitalPoint(at, v, sign.isAbnormal(v))
        }.sortedBy { it.at }

    /** Vital signs that have at least one value, so the chart only offers useful choices. */
    fun available(examinations: List<Examination>): List<VitalSign> =
        VitalSign.entries.filter { sign -> examinations.any { sign.read(it) != null } }
}
