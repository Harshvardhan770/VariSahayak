package com.varisahayak.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.varisahayak.R
import com.varisahayak.data.realtime.RealtimeCoordinator
import com.varisahayak.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Implements [Configuration.Provider] so WorkManager uses Hilt's worker factory — the
 * manifest removes WorkManager's default initializer to make room for it. Without this,
 * the sync worker cannot be constructed with its injected dependencies.
 */
@HiltAndroidApp
class VariSahayakApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var realtimeCoordinator: RealtimeCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Pull as well as push. Sync used to run only when this device wrote something,
        // which left every read-only role — responders, organisers — looking at a
        // permanently empty queue.
        syncScheduler.ensurePeriodicSync()
        // Follows the session on its own: channels open on sign-in and close on sign-out,
        // so starting it here costs nothing while signed out.
        realtimeCoordinator.start()
    }

    /**
     * Channels are created up front so an SOS notification arriving before the user has
     * opened any screen still lands at the right importance.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        val channels = listOf(
            channel(
                id = getString(R.string.notification_channel_id_sos),
                name = getString(R.string.notification_channel_sos),
                importance = NotificationManager.IMPORTANCE_HIGH,
                bypassDnd = true,
            ),
            channel(
                id = getString(R.string.notification_channel_id_assignment),
                name = getString(R.string.notification_channel_assignment),
                importance = NotificationManager.IMPORTANCE_HIGH,
            ),
            channel(
                id = getString(R.string.notification_channel_id_escalation),
                name = getString(R.string.notification_channel_escalation),
                importance = NotificationManager.IMPORTANCE_HIGH,
            ),
            channel(
                id = getString(R.string.notification_channel_id_status),
                name = getString(R.string.notification_channel_status),
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
            channel(
                id = getString(R.string.notification_channel_id_announcement),
                name = getString(R.string.notification_channel_announcement),
                importance = NotificationManager.IMPORTANCE_LOW,
            ),
        )

        manager.createNotificationChannels(channels)
    }

    // The caller returns early below API 26, but lint cannot follow that across a
    // function boundary. Annotated rather than re-guarded: a second runtime check here
    // would imply this is reachable on older devices, which it is not.
    @RequiresApi(Build.VERSION_CODES.O)
    private fun channel(
        id: String,
        name: String,
        importance: Int,
        bypassDnd: Boolean = false,
    ): NotificationChannel = NotificationChannel(id, name, importance).apply {
        // Requesting DND bypass is not the same as being granted it; the system decides.
        setBypassDnd(bypassDnd)
        enableVibration(true)
    }
}
