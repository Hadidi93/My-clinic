package com.myclinic.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit

/** Runs a sync in the background, even if the app was closed; Android retries it when the network returns. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val engine: SyncEngine,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        supabase.auth.awaitInitialization() // load the saved login first
        return when (engine.syncNow()) {
            SyncOutcome.SUCCESS, SyncOutcome.NOT_SIGNED_IN -> Result.success()
            SyncOutcome.NETWORK_ERROR -> Result.retry()
            SyncOutcome.ERROR -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

class SyncScheduler(
    private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sync as soon as there is a connection (after every local change, and on app start). */
    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Background refresh every 15 minutes (the shortest Android allows). */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(NOW)
        workManager.cancelUniqueWork(PERIODIC)
    }

    private companion object {
        const val NOW = "sync-now"
        const val PERIODIC = "sync-periodic"
    }
}
