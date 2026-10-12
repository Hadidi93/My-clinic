package com.myclinic.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Email links from Supabase, as the phone hands them to the app. */
class AuthLinkTest {
    @Test
    fun `reset and verify links with a code`() {
        assertEquals(AuthLink(isReset = true, code = "abc", error = null), AuthLink.parse("myclinic://auth-callback/reset?code=abc"))
        assertEquals(AuthLink(isReset = false, code = "x1", error = null), AuthLink.parse("myclinic://auth-callback/verified?code=x1"))
    }

    @Test
    fun `expired links report the error from the query or after the hash`() {
        assertEquals("otp_expired", AuthLink.parse("myclinic://auth-callback/verified?error=otp_expired")?.error)
        assertEquals("error=access_denied", AuthLink.parse("myclinic://auth-callback/reset#error=access_denied&error_code=403")?.error)
    }

    @Test
    fun `other links are ignored`() {
        assertNull(AuthLink.parse(null))
        assertNull(AuthLink.parse("https://example.com/reset?code=abc"))
        assertNull(AuthLink.parse("myclinic://something-else/reset?code=abc"))
    }
}
