package com.myclinic.domain.forms

import com.myclinic.domain.record.Dates
import com.myclinic.domain.record.LabValue
import com.myclinic.domain.record.RecordJson
import kotlinx.serialization.builtins.ListSerializer
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
 *   LAB_VALUES the JSON list itself, e.g. [{"test":"Hb","value":11.2,"unit":"g/dL"}]
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
                FieldType.LAB_VALUES -> (element as? JsonArray)?.takeIf { it.isNotEmpty() }?.toString().orEmpty()
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
                FieldType.TAGS -> if (raw.split(',').count { it.isNotBlank() } > MAX_TAGS) FieldError.TOO_LONG else null
                FieldType.LAB_VALUES -> validateLabValues(raw)
                FieldType.BOOLEAN, FieldType.CHECKLIST, FieldType.SURGICAL_CASE,
                FieldType.FACILITY, FieldType.INVESTIGATION_REQUEST -> null
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
        FieldType.LAB_VALUES -> encodeLabValues(parseLabValues(raw).orEmpty())
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

    /** Reads the LAB_VALUES text; null if it isn't a valid list. Rows without a test name are dropped. */
    fun parseLabValues(raw: String): List<LabValue>? {
        if (raw.isBlank()) return emptyList()
        return runCatching { RecordJson.decodeFromString(ListSerializer(LabValue.serializer()), raw) }
            .getOrNull()
            ?.filter { it.test.isNotBlank() }
    }

    /** LAB_VALUES text for a list (the editor's output). */
    fun labValuesText(values: List<LabValue>): String =
        if (values.isEmpty()) "" else encodeLabValues(values).toString()

    private fun encodeLabValues(values: List<LabValue>): JsonArray = JsonArray(
        values.filter { it.test.isNotBlank() }.map { v ->
            // Only the keys that have a value, to keep rows small.
            JsonObject(buildMap {
                put("test", JsonPrimitive(v.test.trim()))
                v.value?.let { put("value", JsonPrimitive(it)) }
                v.text?.trim()?.takeIf { it.isNotEmpty() }?.let { put("text", JsonPrimitive(it)) }
                v.unit?.trim()?.takeIf { it.isNotEmpty() }?.let { put("unit", JsonPrimitive(it)) }
                v.low?.let { put("low", JsonPrimitive(it)) }
                v.high?.let { put("high", JsonPrimitive(it)) }
            })
        },
    )

    private fun validateLabValues(raw: String): FieldError? {
        val values = parseLabValues(raw) ?: return FieldError.NOT_A_NUMBER
        return when {
            values.size > MAX_LAB_VALUES -> FieldError.TOO_LONG
            values.any { it.test.length > 100 || it.unit.orEmpty().length > 30 || it.text.orEmpty().length > 200 } ->
                FieldError.TOO_LONG
            values.any { it.value == null && it.text.isNullOrBlank() } -> FieldError.REQUIRED
            values.any { it.low != null && it.high != null && it.low > it.high } -> FieldError.OUT_OF_RANGE
            else -> null
        }
    }

    private const val MAX_TAGS = 50
    private const val MAX_LAB_VALUES = 100

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
        "date_of_birth", "onset_date", "diagnosed_on", "performed_on", "operation_date", "result_date",
    )

    /** Current value of a field in a row, as text (for display). */
    fun text(row: JsonObject, key: String): String? =
        (row[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}
