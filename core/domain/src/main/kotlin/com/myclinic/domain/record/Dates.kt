package com.myclinic.domain.record

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Parsing of the ISO date/time text the server sends. Anything unparseable becomes null. */
object Dates {

    /** "2026-09-30" or a full timestamp -> the local calendar date. */
    fun localDate(value: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            if (value.length == 10) LocalDate.parse(value) else instant(value)?.atZone(zone)?.toLocalDate()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun instant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            if (value.length == 10) {
                LocalDate.parse(value).atStartOfDay(ZoneId.of("UTC")).toInstant()
            } else {
                OffsetDateTime.parse(value.replace(' ', 'T')).toInstant()
            }
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(value).atZone(ZoneId.of("UTC")).toInstant()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    /** A sortable number; missing dates sort as oldest. */
    fun sortKey(value: String?): Long = instant(value)?.toEpochMilli() ?: Long.MIN_VALUE

    /** Timestamp text for "now" in the format the server accepts. */
    fun nowIso(now: Instant = Instant.now()): String = now.toString()

    /** Age in whole years from date of birth, falling back to the recorded age. */
    fun age(dateOfBirth: String?, ageYears: Int?, today: LocalDate): Int? =
        localDate(dateOfBirth)?.let { Period.between(it, today).years.takeIf { y -> y >= 0 } } ?: ageYears
}
