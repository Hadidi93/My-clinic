package com.myclinic.domain.validation

/**
 * Converts Arabic-Indic (٠١٢…) and Eastern Arabic/Persian (۰۱۲…) digits to
 * 0-9, so a phone or licence number typed on an Arabic keyboard is stored
 * the same way as one typed in English.
 */
fun String.normalizeDigits(): String = buildString(length) {
    for (ch in this@normalizeDigits) {
        append(
            when (ch) {
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> ch
            },
        )
    }
}

object EmailValidator {
    private val pattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    fun isValid(email: String): Boolean = email.length <= 254 && pattern.matches(email.trim())
}

enum class PasswordError { TOO_SHORT, TOO_LONG, NEEDS_LETTER, NEEDS_DIGIT, MISMATCH }

/**
 * Password rules. Configure the same minimum length in
 * Supabase Dashboard -> Authentication -> Providers -> Email (see docs/SETUP.md).
 */
object PasswordPolicy {
    const val MIN_LENGTH = 10
    /** bcrypt, which Supabase uses, ignores anything after 72 bytes. */
    const val MAX_LENGTH = 72

    fun validate(password: String, confirmation: String? = null): List<PasswordError> {
        val errors = mutableListOf<PasswordError>()
        if (password.length < MIN_LENGTH) errors += PasswordError.TOO_SHORT
        if (password.toByteArray(Charsets.UTF_8).size > MAX_LENGTH) errors += PasswordError.TOO_LONG
        if (password.none { it.isLetter() }) errors += PasswordError.NEEDS_LETTER
        if (password.none { it.isDigit() }) errors += PasswordError.NEEDS_DIGIT
        if (confirmation != null && confirmation != password) errors += PasswordError.MISMATCH
        return errors
    }
}

data class ProfileInput(
    val fullName: String,
    val specialty: String,
    val hospital: String,
    val licenseNumber: String,
    val phone: String,
)

enum class ProfileField { FULL_NAME, SPECIALTY, HOSPITAL, LICENSE_NUMBER, PHONE }

/** Required fields for licence verification, with the same length limits as the database. */
object ProfileValidator {
    private val licensePattern = Regex("^[A-Za-z0-9/\\-. ]{3,40}$")
    private val phonePattern = Regex("^\\+?[0-9]{7,15}$")

    /** Removes spaces, dashes and brackets from a phone number and normalises digits. */
    fun cleanPhone(phone: String): String = phone.normalizeDigits().filter { it.isDigit() || it == '+' }

    fun cleanLicense(license: String): String = license.normalizeDigits().trim()

    /** Returns the fields that are invalid; an empty set means the profile is complete. */
    fun validate(input: ProfileInput): Set<ProfileField> {
        val invalid = mutableSetOf<ProfileField>()
        if (input.fullName.trim().length !in 3..120) invalid += ProfileField.FULL_NAME
        if (input.specialty.trim().length !in 2..80) invalid += ProfileField.SPECIALTY
        if (input.hospital.trim().length !in 2..120) invalid += ProfileField.HOSPITAL
        if (!licensePattern.matches(cleanLicense(input.licenseNumber))) invalid += ProfileField.LICENSE_NUMBER
        if (!phonePattern.matches(cleanPhone(input.phone))) invalid += ProfileField.PHONE
        return invalid
    }
}
