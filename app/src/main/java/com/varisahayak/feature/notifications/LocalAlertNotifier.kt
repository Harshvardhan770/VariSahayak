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
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.usecase.AlertTemplateGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.SupervisorJob
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
 * of the sync worker and realtime, so an incident that reaches this device can be announced
 * by this device — no server, no Firebase project, no push credential.
 *
 * **How "all relevant authorities" is actually decided — and it is not decided here.**
 * Every incident on this device arrived through `RealtimeCoordinator`'s `incidents` channel
 * and `refreshFromServer()`, both of which run under the signed-in user's row-level
 * security. The `"Responders read actionable incidents"` policy admits a row when it is
 * assigned to them, scoped to their organisation or their area, or sitting unclaimed in the
 * open pool; a volunteer sees only what they reported themselves. So the set of incidents
 * this class can see *is* the set this user is an authority for. Announcing all of them is
 * the fan-out, and Postgres — not a client-side role check — is what bounds it. A tampered
 * client gains a notification about a row it was already permitted to read, and nothing else.
 *
 * **What this is not.** It only fires while the app process is alive. A responder whose
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

    private val authRepository: AuthRepository,

    private val templateGenerator: AlertTemplateGenerator,

) {

    private val scope = CoroutineScope(SupervisorJob())
    private var jobs: List<Job> = emptyList()

    /**
     * SOS incidents already announced, as client id to server id.
     *
     * Membership is what stops a re-announcement: Room re-emits the whole list on every
     * write, and an SOS that stays open would otherwise buzz on every sync — which trains a
     * responder to ignore the one alert in this app that must never be ignored.
     *
     * The server id is carried alongside because clearing a sticky SOS needs both.
     * [VariSahayakMessagingService] posts a push-delivered SOS keyed by the *server* id —
     * that is the only id a push carries — while this class posts and clears by the
     * *client* id. An SOS notification is ongoing and cannot be swiped away, so an id
     * nothing knows to cancel is a notification that stays on screen forever.
     */
    private val announcedSos = mutableMapOf<String, String?>()

    /** The same guard for ordinary incidents, which need no second id: they are dismissible. */
    private val announcedIncidents = mutableSetOf<String>()

    /** Idempotent: called from a Compose effect that re-runs on configuration change. */
    fun start() {
        if (jobs.any { it.isActive }) return

        jobs = listOf(
            scope.launch { watchSos() },
            scope.launch { watchIncidents() },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    /**
     * The SOS stream, which is sticky.
     *
     * An SOS notification is posted ongoing: the system will not let it be swiped away and
     * tapping it does not dismiss it. That is deliberate and is the one place in this app
     * where taking the choice away from the user is right — an SOS that a responder brushed
     * off the shade while walking is an SOS nobody is going to.
     *
     * It is removed exactly once, from here, when the incident leaves the active set —
     * meaning somebody moved it to RESOLVED or CANCELLED. Until then it stays on screen.
     */
    private suspend fun watchSos() {
        incidentRepository.observeActiveSos().collectLatest { incidents ->
            incidents.forEach { incident ->
                if (!announcedSos.containsKey(incident.clientId)) notifySos(incident)
                // Refreshed every pass, not only on first sight: an incident is announced
                // from the local record and only later learns its server id from sync. A
                // value captured once would be null forever and the push copy unclearable.
                announcedSos[incident.clientId] = incident.serverId
            }

            val active = incidents.map { it.clientId }.toSet()

            // Resolved or cancelled. Clear the sticky notification, or it outlives the
            // emergency and there is no way for anyone to get rid of it. Both ids are
            // cancelled because either path may have posted it.
            announcedSos.filterKeys { it !in active }.forEach { (clientId, serverId) ->
                cancel(sosNotificationId(clientId))
                serverId?.let { cancel(sosNotificationId(it)) }
            }

            announcedSos.keys.retainAll(active)
        }
    }

    /**
     * Every other incident this user is an authority for.
     *
     * SOS is skipped because [watchSos] owns it and would otherwise post a second, weaker
     * notification for the same emergency.
     *
     * An incident this user reported themselves is skipped too. They filed it seconds ago
     * and are looking at the confirmation; telling them about it is the kind of noise that
     * teaches people to turn notifications off, which costs them the alerts that matter.
     */
    private suspend fun watchIncidents() {
        val selfId = authRepository.currentUserId()

        incidentRepository.observeOpen().collectLatest { incidents ->
            incidents.forEach { incident ->
                if (incident.isSos) return@forEach
                if (selfId != null && incident.reporterId == selfId) return@forEach

                if (announcedIncidents.add(incident.clientId)) notifyIncident(incident)
            }

            val open = incidents.map { it.clientId }.toSet()
            announcedIncidents.retainAll(open)
        }
    }

    private fun notifySos(incident: Incident) {
        val notification = builder(
            channelId = context.getString(R.string.notification_channel_id_sos),
            title = context.getString(R.string.notification_sos_title),
            body = context.getString(R.string.notification_sos_body),
            incident = incident,
            type = TYPE_SOS,
        )

        val alert = templateGenerator.generate(incident, System.currentTimeMillis())

            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // The sticky part. Ongoing blocks the swipe; autoCancel(false) means opening the
            // incident does not clear it either. Only a resolve or cancel takes it down.
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        post(sosNotificationId(incident.clientId), notification)
    }

    /**
     * A new incident for the authorities who can act on it.
     *
     * Dismissible, unlike an SOS: this is work to pick up, not an emergency in progress, and
     * a responder triaging a queue must be able to clear what they have read.
     */
    private fun notifyIncident(incident: Incident) {
        val notification = builder(
            channelId = context.getString(R.string.notification_channel_id_escalation),
            title = context.getString(R.string.notification_incident_title),
            body = context.getString(R.string.notification_incident_body),
            incident = incident,
            type = TYPE_INCIDENT,
        )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)

            .setContentIntent(openIntent(incident))
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .build()

        post(incidentNotificationId(incident.clientId), notification)
    }

    private fun builder(
        channelId: String,
        title: String,
        body: String,
        incident: Incident,
        type: String,
    ) = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setContentIntent(openIntent(incident, type))

    private fun post(id: Int, notification: android.app.Notification) {
        if (!canPost()) {
            Log.d(TAG, "Notification suppressed: posting not permitted")
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (denied: SecurityException) {
            // Revoked between the check and the post. A lost notification is a lost
            // convenience; the incident list holds the authoritative record.
            Log.d(TAG, "Notification suppressed: permission revoked")
        }
    }

    private fun cancel(id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
            .onFailure { Log.d(TAG, "Could not clear notification") }
    }

    /**
     * Opens the incident.
     *
     * The deep-link bus is keyed by *server* id because that is what a push carries. This
     * path has the local record in hand, but it reuses the same extra rather than adding a
     * second route — one resolution path is easier to keep correct than two.
     */
    private fun openIntent(incident: Incident, type: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            incident.serverId?.let {
                putExtra(NotificationDeepLinkBus.EXTRA_INCIDENT_SERVER_ID, it)
            }
            putExtra(NotificationDeepLinkBus.EXTRA_TYPE, type)
        }

        return PendingIntent.getActivity(
            context,
            // Distinct per notification, or the two streams would share one PendingIntent
            // and the second would silently reuse the first one's extras.
            if (type == TYPE_SOS) {
                sosNotificationId(incident.clientId)
            } else {
                incidentNotificationId(incident.clientId)
            },
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

    // Namespaced so the same incident cannot collide between the two streams. Nothing today
    // posts both for one row — watchIncidents skips SOS — but an id collision would silently
    // replace an ongoing emergency notification with a dismissible one, which is the exact
    // failure this class exists to prevent.
    private fun sosNotificationId(clientId: String): Int = "SOS:$clientId".hashCode()

    private fun incidentNotificationId(clientId: String): Int = "INCIDENT:$clientId".hashCode()

    private companion object {
        const val TAG = "LocalAlertNotifier"
        const val TYPE_SOS = "SOS"
        const val TYPE_INCIDENT = "ESCALATION"
    }
}
