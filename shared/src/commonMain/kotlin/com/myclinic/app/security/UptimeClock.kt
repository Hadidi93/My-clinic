package com.myclinic.app.security

/** Milliseconds since the phone started, including time asleep; the user can't change it. */
fun interface UptimeClock {
    fun millis(): Long
}
