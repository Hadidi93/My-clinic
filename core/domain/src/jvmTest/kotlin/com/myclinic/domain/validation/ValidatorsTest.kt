package com.myclinic.domain.validation

import com.myclinic.domain.error.AuthError
import com.myclinic.domain.error.AuthErrorClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `arabic digits are converted`() {
        assertEquals("01012345678", "٠١٠١٢٣٤٥٦٧٨".normalizeDigits())
        assertEquals("123", "۱۲۳".normalizeDigits())
        assertEquals("abc 12", "abc 12".normalizeDigits())
    }

    @Test
    fun `email validation`() {
        assertTrue(EmailValidator.isValid("dr.demo@example.com"))
        assertTrue(EmailValidator.isValid("  dr.demo@example.com "))
        assertFalse(EmailValidator.isValid("dr.demo@"))
        assertFalse(EmailValidator.isValid("not an email"))
    }

    @Test
    fun `password policy`() {
        assertTrue(PasswordPolicy.validate("surgeon2026ok").isEmpty())
        assertEquals(listOf(PasswordError.TOO_SHORT), PasswordPolicy.validate("abc12345"))
        assertEquals(listOf(PasswordError.NEEDS_DIGIT), PasswordPolicy.validate("onlyletters"))
        assertEquals(listOf(PasswordError.NEEDS_LETTER), PasswordPolicy.validate("1234567890"))
        assertEquals(listOf(PasswordError.MISMATCH), PasswordPolicy.validate("surgeon2026ok", "surgeon2026no"))
        assertEquals(listOf(PasswordError.TOO_LONG), PasswordPolicy.validate("a1".repeat(40)))
    }

    private val validProfile = ProfileInput(
        fullName = "Dr Demo Surgeon",
        specialty = "General Surgery",
        hospital = "Demo Teaching Hospital",
        licenseNumber = "EMS-12345",
        phone = "+20 100 000 0000",
    )

    @Test
    fun `complete profile has no errors`() {
        assertTrue(ProfileValidator.validate(validProfile).isEmpty())
    }

    @Test
    fun `arabic phone digits are accepted`() {
        assertTrue(ProfileValidator.validate(validProfile.copy(phone = "٠١٠٠٠٠٠٠٠٠٠")).isEmpty())
    }

    @Test
    fun `empty profile reports every field`() {
        val invalid = ProfileValidator.validate(ProfileInput("", "", "", "", ""))
        assertEquals(ProfileField.entries.toSet(), invalid)
    }

    @Test
    fun `bad licence and phone are rejected`() {
        val invalid = ProfileValidator.validate(validProfile.copy(licenseNumber = "x!", phone = "123"))
        assertEquals(setOf(ProfileField.LICENSE_NUMBER, ProfileField.PHONE), invalid)
    }

    @Test
    fun `auth errors are classified`() {
        assertEquals(AuthError.INVALID_CREDENTIALS, AuthErrorClassifier.classify("invalid_credentials Invalid login credentials"))
        assertEquals(AuthError.EMAIL_NOT_CONFIRMED, AuthErrorClassifier.classify("Email not confirmed"))
        assertEquals(AuthError.EMAIL_ALREADY_REGISTERED, AuthErrorClassifier.classify("User already registered"))
        assertEquals(AuthError.RATE_LIMITED, AuthErrorClassifier.classify("over_email_send_rate_limit"))
        assertEquals(AuthError.UNKNOWN, AuthErrorClassifier.classify(null))
    }
}
