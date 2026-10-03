package com.myclinic.domain.security

/**
 * When the app asks for the fingerprint / face / phone PIN again: at every
 * start, and after [TIMEOUT_MS] without use (in the background or with the
 * phone left on the table).
 */
object LockPolicy {
    const val TIMEOUT_MS: Long = 5 * 60 * 1000

    /** [lastActiveAtMs] null = nothing recorded yet (fresh start): lock. */
    fun shouldLock(lastActiveAtMs: Long?, nowMs: Long, timeoutMs: Long = TIMEOUT_MS): Boolean =
        lastActiveAtMs == null || nowMs - lastActiveAtMs >= timeoutMs || nowMs < lastActiveAtMs // clock moved back: lock
}
