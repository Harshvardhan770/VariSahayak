package com.varisahayak.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms sync after a reboot or an app update.
 *
 * Without this, an incident captured in a dead spot and still queued when the phone is
 * restarted would sit on the device indefinitely — WorkManager's own persistence covers
 * scheduled work, but a queue that never gets re-requested after a cold boot is exactly
 * the case that loses a report.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> syncScheduler.rescheduleAfterBoot()
        }
    }
}
