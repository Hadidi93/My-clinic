package com.myclinic.app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Android's uptime clock (counts deep sleep too). */
val AndroidUptimeClock = UptimeClock { SystemClock.elapsedRealtime() }

/** Tells the app lock when the whole app comes to the front or leaves it. */
fun AppLockManager.watchAndroidLifecycle() {
    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onForeground()
        override fun onStop(owner: LifecycleOwner) = onBackground()
    })
}
