package com.myclinic.app.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * iPhone sync: runs straight away after each change and every 15 minutes
 * while the app is open (iOS doesn't allow a regular background timer).
 * Changes made offline wait in the outbox and go up on the next try.
 */
class IosSyncTrigger(private val engine: Lazy<SyncEngine>) : SyncTrigger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodic: Job? = null

    override fun requestSync() {
        scope.launch { engine.value.syncNow() }
    }

    override fun schedulePeriodic() {
        if (periodic?.isActive == true) return
        periodic = scope.launch {
            while (isActive) {
                delay(15 * 60 * 1000L)
                engine.value.syncNow()
            }
        }
    }

    override fun cancelAll() {
        periodic?.cancel()
        periodic = null
    }
}
