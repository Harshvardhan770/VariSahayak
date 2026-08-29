package com.varisahayak.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests that the pending queue be drained.
 *
 * An interface so repositories do not depend on WorkManager directly and can be unit
 * tested without it.
 */
interface SyncScheduler {
    /** Asks for a sync as soon as there is a network. Safe to call repeatedly. */
    fun requestSync()

    /** Re-arms sync after a reboot or an app update. */
    fun rescheduleAfterBoot()

    /**
     * Registers the background pull.
     *
     * Without it, sync only ever ran as a side effect of *writing* something. A responder
     * or organiser who files nothing therefore never pulled either, and incidents
     * reported by volunteers never reached their dashboard. Idempotent — safe to call on
     * every app start.
     */
    fun ensurePeriodicSync()
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun requestSync() = enqueue(ExistingWorkPolicy.KEEP)

    /**
     * REPLACE on boot, not KEEP: a worker enqueued before the reboot may be recorded as
     * still pending but will never actually run, and KEEP would defer to that ghost.
     */
    override fun rescheduleAfterBoot() = enqueue(ExistingWorkPolicy.REPLACE)

    override fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncIncidentsWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // KEEP, not UPDATE: replacing the request on every cold start resets the interval
        // timer, so a user who reopens the app often would never reach a periodic run.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SyncIncidentsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        // Unique work: without it, every incident created offline would enqueue its own
        // worker and a reconnect would fire a dozen concurrent syncs.
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncIncidentsWorker.WORK_NAME,
            policy,
            request,
        )
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L

        /** WorkManager's floor is 15 minutes; anything shorter is silently clamped. */
        const val PERIODIC_INTERVAL_MINUTES = 15L
        const val PERIODIC_WORK_NAME = "varisahayak-sync-periodic"
    }
}
