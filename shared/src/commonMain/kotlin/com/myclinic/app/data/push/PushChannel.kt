package com.myclinic.app.data.push

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's push service (Firebase on Android, Apple's on iPhone) as the
 * shared code sees it. Pushes carry only a kind, never patient data.
 */
interface PushChannel {
    /** Kinds of pushes received while the app is running (screens refresh their lists). */
    val received: SharedFlow<String>
    /** The kind of notification the user tapped to open the app, until handled. */
    val openRequest: StateFlow<String?>
    fun onOpenHandled()
    /** This phone's push address, or null when push isn't set up. */
    suspend fun currentToken(): String?
    suspend fun deleteToken()
}
