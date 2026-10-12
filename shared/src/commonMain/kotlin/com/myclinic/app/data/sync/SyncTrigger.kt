package com.myclinic.app.data.sync

/** Asks the phone to run a sync (WorkManager on Android; on iPhone, while the app is open). */
interface SyncTrigger {
    /** Sync as soon as there is a connection (after every local change, and on app start). */
    fun requestSync()
    /** Regular background refresh, where the phone allows it. */
    fun schedulePeriodic()
    fun cancelAll()
}
