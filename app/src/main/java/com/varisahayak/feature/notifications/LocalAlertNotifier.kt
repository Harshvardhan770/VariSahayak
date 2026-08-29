package com.varisahayak.feature.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.varisahayak.R
import com.varisahayak.app.MainActivity
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.repository.IncidentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raises system notifications for work this device can already see.
 *
 * Push is the intended delivery route and is not available: the build has no
 * `google-services.json`, so the google-services plugin is not applied, FCM has no project
 * to register against, and [VariSahayakMessagingService] is never invoked. Nothing reached
 * the notification tray at all.
 *
 * This closes the gap using what the app already has. Incidents arrive through Room by way
 * of the sync worker and realtime, so an SOS that reaches this device can be announced by
 * this device — no server, no Firebase project, no push credential.
 *
 * **What this is not.** It only fires while the app process is alive. A volunteer whose
 * phone is in their pocket with the app swiped away still gets nothing; that genuinely
 * requires FCM. This makes alerts visible outside the *screen*, not outside the *process*.
 * The distinction matters and should not be papered over.
 *
 * Content is built from local string resources exactly as the push path does, so a
 * notification never carries a name, a description, or coordinates onto a lock screen.
 */
@Singleton
class LocalAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val incidentRepository: IncidentRepository,
) {

    private val scope = CoroutineScope(SupervisorJob())
    private var job: Job? = null

    /**
     * Incidents already announced, so a re-emission of the same list does not re-notify.
     *
     * Room re-emits the whole list on every write, and an SOS that stays open would
     * otherwise buzz on every sync — which trains a volunteer to ignore the one alert in
     * this app that must never be ignored.
     */
    private val announced = mutableSetOf<String>()

    /** Idempotent: called from a Compose effect that re-runs on configuration change. */
    fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            incidentRepository.observeActiveSos().collectLatest { incidents ->
                incidents.forEach { incident ->
                    if (announced.add(incident.clientId)) notifySos(incident)
                }

                // Forget anything no longer active, so a genuinely new SOS at the same id
                // after a resolve-and-reopen is announced again.
                announced.retainAll(incidents.map { it.clientId }.toSet())
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun notifySos(incident: Incident) {
        if (!canPost()) {
            Log.d(TAG, "SOS notification suppressed: posting not permitted")
            return
        }

        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.notification_channel_id_sos),
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_sos_title))
            .setContentText(context.getString(R.string.notification_sos_body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent(incident))
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(incident.clientId.hashCode(), notification)
        } catch (denied: SecurityException) {
            // Revoked between the check and the post. A lost notification is a lost
            // convenience; the incident list holds the authoritative record.
            Log.d(TAG, "SOS notification suppressed: permission revoked")
        }
    }

    /**
     * Opens the incident.
     *
     * The deep-link bus is keyed by *server* id because that is what a push carries. This
     * path has the local record in hand, but it reuses the same extra rather than adding a
     * second route — one resolution path is easier to keep correct than two.
     */
    private fun openIntent(incident: Incident): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            incident.serverId?.let {
                putExtra(NotificationDeepLinkBus.EXTRA_INCIDENT_SERVER_ID, it)
            }
            putExtra(NotificationDeepLinkBus.EXTRA_TYPE, TYPE_SOS)
        }

        return PendingIntent.getActivity(
            context,
            incident.clientId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private companion object {
        const val TAG = "LocalAlertNotifier"
        const val TYPE_SOS = "SOS"
    }
}
