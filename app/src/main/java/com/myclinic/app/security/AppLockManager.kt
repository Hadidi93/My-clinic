package com.myclinic.app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.myclinic.domain.security.LockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locks the app (fingerprint / face / phone PIN to continue) at every start
 * and after 5 minutes without use, whether the app was in the background or
 * left open on the table. Uses the phone's uptime clock, which can't be moved
 * by changing the date.
 */
@Singleton
class AppLockManager @Inject constructor() {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile private var lastActiveAt: Long? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                checkNow()
                startTicker()
            }

            override fun onStop(owner: LifecycleOwner) {
                ticker?.cancel()
                // The time away counts from now; touches no longer update it.
                if (!_locked.value) lastActiveAt = now()
            }
        })
    }

    /** Any touch in the app. */
    fun onUserInteraction() {
        if (!_locked.value) lastActiveAt = now()
    }

    /** Fingerprint/face/PIN accepted, or the user just signed in with their password. */
    fun unlock() {
        lastActiveAt = now()
        _locked.value = false
    }

    /** On sign-out: the next session starts locked. */
    fun lock() {
        lastActiveAt = null
        _locked.value = true
    }

    private fun checkNow() {
        if (!_locked.value && LockPolicy.shouldLock(lastActiveAt, now())) _locked.value = true
    }

    /** While the app is open and untouched, lock after the timeout too. */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(15_000)
                checkNow()
            }
        }
    }

    private fun now() = SystemClock.elapsedRealtime()
}
