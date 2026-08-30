package com.varisahayak.feature.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.varisahayak.R
import com.varisahayak.app.MainActivity
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.domain.repository.DeviceTokenRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import javax.inject.Inject

/**
 * Receives pushes and turns them into notifications.
 *
 * Two rules from plan 06 §6.5 shape everything here:
 *
 * **The payload carries an id and a type, nothing else.** No name, no medical detail, no
 * coordinates. A push traverses Google's infrastructure and lands on a lock screen in
 * public; anything in it is effectively published. The title and body rendered below are
 * built from local string resources keyed off the type — they are not taken from the
 * payload, so a malicious or malformed push cannot put arbitrary text on a lock screen.
 *
 * **Delivery is best-effort.** Everything that arrives by push also exists in the
 * `notifications` table and in the incident list. A device with no Play Services, a denied
 * POST_NOTIFICATIONS permission, or a dropped message loses the tap-through convenience
 * and nothing else.
 *
 * `onNewToken(String)` is the current callback. `onTokenRefresh` and `FirebaseInstanceId`
 * were removed from the SDK (contract §0.10) and must not reappear.
 */
@AndroidEntryPoint
class VariSahayakMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var deviceTokenRepository: DeviceTokenRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    // The service can be destroyed as soon as the callback returns, so registration runs
    // on a scope that outlives it rather than on a lifecycle that is about to end.
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        scope.launch { deviceTokenRepository.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data[KEY_TYPE].orEmpty()
        val incidentId = message.data[KEY_INCIDENT_ID]

        // Built from local resources, keyed by type. Never from the payload.
        val content = contentFor(type)
        val channelId = channelFor(type)

        if (!canPostNotifications()) {
            // Permission denied, or the user turned the channel off. The record is still
            // in the notification centre; there is nothing to recover here.
            Log.d(TAG, "Notification suppressed: posting not permitted")
            return
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(content.titleRes))
            .setContentText(getString(content.bodyRes))
            .setPriority(content.priority)
            .setCategory(content.category)
            // An SOS is sticky: ongoing blocks the swipe, and not auto-cancelling means
            // opening it does not clear it either. It comes down when the incident reaches
            // RESOLVED or CANCELLED and LocalAlertNotifier drops it, not when a responder
            // brushes the shade. Everything else stays dismissible — a queue a responder
            // cannot clear is a queue they stop reading.
            .setOngoing(content.sticky)
            .setAutoCancel(!content.sticky)
            .setContentIntent(openIntent(incidentId, type))
            .build()

        // canPostNotifications() already checked this, but lint cannot follow the check
        // across a function boundary — and the permission can be revoked between the two
        // calls anyway. A suppressed notification is a lost convenience, never lost data:
        // the notifications table holds the authoritative record.
        try {
            // Namespaced to match LocalAlertNotifier, which is what clears a sticky SOS
            // once the incident is resolved. A push and a local announcement for the same
            // emergency must land on one notification id, or the push copy is left on
            // screen with nothing able to take it down.
            NotificationManagerCompat.from(this)
                .notify(notificationId(type, incidentId), notification)
        } catch (denied: SecurityException) {
            Log.d(TAG, "Notification suppressed: permission revoked")
        }
    }

    /**
     * Opens the incident the notification is about, from a cold start as well as a warm
     * one.
     *
     * The extras are read by [MainActivity] into [NotificationDeepLinkBus]; navigation
     * resolves the server id against Room and then routes. Nothing about the destination
     * is decided here.
     */
    private fun openIntent(incidentServerId: String?, type: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            // singleTask + CLEAR_TOP: reuse the running task rather than stacking a second
            // copy of the app behind the one the user already has open.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            incidentServerId?.let {
                putExtra(NotificationDeepLinkBus.EXTRA_INCIDENT_SERVER_ID, it)
            }
            putExtra(NotificationDeepLinkBus.EXTRA_TYPE, type)
        }

        return PendingIntent.getActivity(
            this,
            incidentServerId?.hashCode() ?: 0,
            intent,
            // IMMUTABLE is required from Android 12; UPDATE_CURRENT so a second push about
            // the same incident refreshes the extras instead of reusing the first one's.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    /**
     * Matches `LocalAlertNotifier`'s scheme so the two paths address the same notification.
     *
     * The push carries a *server* id while the local path holds a client id, so these only
     * coincide once the deep-link bus has resolved one to the other. Until they do, the
     * worst case is two notifications for one incident — noisy, but never an undismissable
     * SOS, which is what a shared id with a mismatched namespace would produce.
     */
    private fun notificationId(type: String, incidentId: String?): Int {
        val key = incidentId ?: return type.hashCode()
        return if (type == TYPE_SOS) "SOS:$key".hashCode() else "INCIDENT:$key".hashCode()
    }

    private fun channelFor(type: String): String = when (type) {
        TYPE_SOS -> getString(R.string.notification_channel_id_sos)
        TYPE_ASSIGNMENT -> getString(R.string.notification_channel_id_assignment)
        TYPE_ESCALATION -> getString(R.string.notification_channel_id_escalation)
        TYPE_ANNOUNCEMENT -> getString(R.string.notification_channel_id_announcement)
        else -> getString(R.string.notification_channel_id_status)
    }

    private fun contentFor(type: String): NotificationContent = when (type) {
        TYPE_SOS -> NotificationContent(
            R.string.notification_sos_title,
            R.string.notification_sos_body,
            NotificationCompat.PRIORITY_MAX,
            NotificationCompat.CATEGORY_ALARM,
            sticky = true,
        )

        TYPE_ASSIGNMENT -> NotificationContent(
            R.string.notification_assignment_title,
            R.string.notification_assignment_body,
            NotificationCompat.PRIORITY_HIGH,
            NotificationCompat.CATEGORY_EVENT,
        )

        TYPE_ESCALATION -> NotificationContent(
            R.string.notification_escalation_title,
            R.string.notification_escalation_body,
            NotificationCompat.PRIORITY_HIGH,
            NotificationCompat.CATEGORY_EVENT,
        )

        TYPE_ANNOUNCEMENT -> NotificationContent(
            R.string.notification_announcement_title,
            R.string.notification_announcement_body,
            NotificationCompat.PRIORITY_LOW,
            NotificationCompat.CATEGORY_STATUS,
        )

        else -> NotificationContent(
            R.string.notification_status_title,
            R.string.notification_status_body,
            NotificationCompat.PRIORITY_DEFAULT,
            NotificationCompat.CATEGORY_STATUS,
        )
    }

    private data class NotificationContent(
        val titleRes: Int,
        val bodyRes: Int,
        val priority: Int,
        val category: String,
        /** Posted ongoing and never auto-cancelled. SOS only. */
        val sticky: Boolean = false,
    )

    private companion object {
        const val TAG = "VariSahayakMessaging"

        const val KEY_TYPE = "type"
        const val KEY_INCIDENT_ID = "incident_id"

        const val TYPE_SOS = "SOS"
        const val TYPE_ASSIGNMENT = "ASSIGNMENT"
        const val TYPE_ESCALATION = "ESCALATION"
        const val TYPE_ANNOUNCEMENT = "ANNOUNCEMENT"
    }
}
