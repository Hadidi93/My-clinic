package com.myclinic.domain.forms

import com.myclinic.domain.record.Dates
import com.myclinic.domain.validation.normalizeDigits
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate

enum class FieldError { REQUIRED, TOO_LONG, NOT_A_NUMBER, OUT_OF_RANGE, INVALID_DATE, DATE_IN_FUTURE }

/**
 * Converts between a database row (JSON) and what the form shows (plain
 * text per field), and validates the text.
 *
 * Raw text conventions:
 *   BOOLEAN   "true" / "false"
 *   CHECKLIST checked item keys joined by ","
 *   TAGS      tags joined by ","
 *   DATE      "yyyy-MM-dd"
 *   DATETIME  ISO instant, e.g. "2026-09-30T08:15:00Z"
 *   CONDITION the condition name; the code goes in the extra key "condition_code"
 */
object FormCodec {

    const val CONDITION_CODE_KEY = "condition_code"

    /** Values for a brand-new entry, with defaults filled in. */
    fun newValues(spec: FormSpec, now: Instant = Instant.now(), today: LocalDate = LocalDate.now()): Map<String, String> =
        spec.fields.associate { f ->
            f.key to when (f.default) {
                null -> ""
                "now" -> now.toString()
                "today" -> today.toString()
                else -> f.default
            }
        }

    /** Form text from an existing row. */
    fun fromJson(spec: FormSpec, row: JsonObject): Map<String, String> {
        val values = mutableMapOf<String, String>()
        for (f in spec.fields) {
            val element = row[f.key]
            values[f.key] = when (f.type) {
                FieldType.CHECKLIST -> (element as? JsonObject)
                    ?.filter { (_, v) -> (v as? JsonPrimitive)?.booleanOrNull == true }
                    ?.keys?.joinToString(",").orEmpty()
                FieldType.TAGS -> (element as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(",").orEmpty()
                else -> (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull.orEmpty()
            }
        }
        if (spec.fields.any { it.type == FieldType.CONDITION }) {
            values[CONDITION_CODE_KEY] = (row[CONDITION_CODE_KEY] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }
        return values
    }

    /** Checks every field. An empty map means the form can be saved. */
    fun validate(spec: FormSpec, values: Map<String, String>, today: LocalDate = LocalDate.now()): Map<String, FieldError> {
        val errors = mutableMapOf<String, FieldError>()
        for (f in spec.fields) {
            val raw = values[f.key].orEmpty().trim()
            if (raw.isEmpty()) {
                if (f.required) errors[f.key] = FieldError.REQUIRED
                continue
            }
            val error: FieldError? = when (f.type) {
                FieldType.TEXT, FieldType.MULTILINE, FieldType.PHONE, FieldType.CONDITION, FieldType.CHOICE ->
                    if (f.maxLength != null && raw.length > f.maxLength) FieldError.TOO_LONG else null
                FieldType.INT -> checkNumber(raw.normalizeDigits().toIntOrNull()?.toDouble(), f)
                FieldType.DECIMAL -> checkNumber(parseDecimal(raw), f)
                FieldType.DATE -> {
                    val d = runCatching { LocalDate.parse(raw) }.getOrNull()
                    when {
                        d == null -> FieldError.INVALID_DATE
                        f.key in PAST_ONLY_DATES && d.isAfter(today) -> FieldError.DATE_IN_FUTURE
                        else -> null
                    }
                }
                FieldType.DATETIME -> if (Dates.instant(raw) == null) FieldError.INVALID_DATE else null
                FieldType.BOOLEAN, FieldType.CHECKLIST, FieldType.TAGS, FieldType.SURGICAL_CASE -> null
            }
            if (error != null) errors[f.key] = error
        }
        return errors
    }

    /**
     * Builds the JSON row to save. [base] holds the columns the form doesn't
     * show (id, patient_id, created_by...), which are kept unchanged.
     * Empty optional fields become null so clearing a field clears it on the server.
     */
    fun toJson(spec: FormSpec, values: Map<String, String>, base: JsonObject): JsonObject {
        val out = base.toMutableMap()
        for (f in spec.fields) {
            val raw = values[f.key].orEmpty().trim()
            out[f.key] = encode(f, raw)
        }
        if (spec.fields.any { it.type == FieldType.CONDITION }) {
            out[CONDITION_CODE_KEY] = values[CONDITION_CODE_KEY].orEmpty().ifBlank { null }
                ?.let { JsonPrimitive(it) } ?: JsonNull
        }
        return JsonObject(out)
    }

    private fun encode(f: FieldSpec, raw: String): JsonElement = when (f.type) {
        FieldType.CHECKLIST -> JsonObject(
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.associateWith { JsonPrimitive(true) },
        )
        FieldType.TAGS -> JsonArray(
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { JsonPrimitive(it) },
        )
        else -> if (raw.isEmpty()) {
            JsonNull
        } else {
            when (f.type) {
                FieldType.INT -> raw.normalizeDigits().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
                FieldType.DECIMAL -> parseDecimal(raw)?.let { JsonPrimitive(it) } ?: JsonNull
                FieldType.BOOLEAN -> JsonPrimitive(raw == "true")
                FieldType.PHONE -> JsonPrimitive(raw.normalizeDigits())
                else -> JsonPrimitive(raw)
            }
        }
    }

    /** Accepts "37.5", "37,5" and Arabic digits/decimal separator ("٣٧٫٥"). */
    fun parseDecimal(raw: String): Double? =
        raw.normalizeDigits().replace('٫', '.').replace(',', '.').toDoubleOrNull()

    private fun checkNumber(value: Double?, f: FieldSpec): FieldError? = when {
        value == null -> FieldError.NOT_A_NUMBER
        (f.min != null && value < f.min) || (f.max != null && value > f.max) -> FieldError.OUT_OF_RANGE
        else -> null
    }

    /** Dates that describe the past and therefore can't be in the future. */
    private val PAST_ONLY_DATES = setOf(
        "date_of_birth", "onset_date", "diagnosed_on", "performed_on", "operation_date",
    )

    /** Current value of a field in a row, as text (for display). */
    fun text(row: JsonObject, key: String): String? =
        (row[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}
