package com.varisahayak.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.LostFoundRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains everything waiting to reach the server.
 *
 * Constrained to run only with a network and retried with exponential backoff by
 * [WorkManagerSyncScheduler]. It is safe to run repeatedly: every upload upserts on a
 * client-generated id, so a retry updates the same row rather than creating a second one.
 *
 * Nothing here ever deletes a local record. A record that cannot be sent stays on the
 * device, stays visible, and is retried — the product rule is that no locally captured
 * incident silently disappears.
 */
@HiltWorker
class SyncIncidentsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val incidentRepository: IncidentRepository,
    private val lostFoundRepository: LostFoundRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val incidents = incidentRepository.syncPending()
        val lostFound = lostFoundRepository.syncPending()

        // Pull server-side changes after pushing, so a status set by a dispatcher while
        // the device was offline lands locally in the same pass.
        if (incidents is Outcome.Success) {
            incidentRepository.refreshFromServer()
        }

        // Pull Lost & Found too, and unconditionally.
        //
        // Pushing alone is not enough: server-side face processing runs after a report
        // reaches the database, and the candidate matches it raises exist only there until
        // they are fetched. Without this, a volunteer who filed a Found Person report
        // offline would sync it successfully and never learn that it matched somebody.
        lostFoundRepository.refreshFromServer()

        val incidentsFailed = (incidents as? Outcome.Success)?.data?.hasFailures ?: true
        val lostFoundFailed = lostFound is Outcome.Failure

        return when {
            // retry() rather than failure(): failure is terminal and would strand the
            // queue until something else happened to enqueue another worker.
            incidentsFailed || lostFoundFailed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "varisahayak-sync"
    }
}
