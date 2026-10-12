package com.myclinic.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    private val t0 = 1_000_000L

    @Test
    fun `a fresh start always locks`() = assertTrue(LockPolicy.shouldLock(null, t0))

    @Test
    fun `stays unlocked while in use, locks after 5 minutes away`() {
        assertFalse(LockPolicy.shouldLock(t0, t0 + 4 * 60_000))
        assertTrue(LockPolicy.shouldLock(t0, t0 + 5 * 60_000))
    }

    @Test
    fun `a clock set backwards locks rather than extending the time`() = assertTrue(LockPolicy.shouldLock(t0, t0 - 1))
}
