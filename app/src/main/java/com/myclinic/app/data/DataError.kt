package com.myclinic.app.data

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException

/** Reasons a database or storage call failed, in terms the UI can explain. */
enum class DataError { NETWORK, LICENSE_TAKEN, NOT_ALLOWED, FILE_TOO_LARGE, DUPLICATE, UNKNOWN }

fun Throwable.toDataError(): DataError {
    if (this is com.myclinic.app.data.files.FileTooLargeException) return DataError.FILE_TOO_LARGE
    if (this is HttpRequestException || this is IOException) return DataError.NETWORK
    val rest = this as? RestException
    val text = listOfNotNull(message, rest?.error, rest?.description).joinToString(" ")
    return when {
        "doctors_license_number_key" in text -> DataError.LICENSE_TAKEN
        "42501" in text || "permission denied" in text.lowercase() -> DataError.NOT_ALLOWED
        "23505" in text || "duplicate key" in text -> DataError.DUPLICATE
        else -> DataError.UNKNOWN
    }
}
