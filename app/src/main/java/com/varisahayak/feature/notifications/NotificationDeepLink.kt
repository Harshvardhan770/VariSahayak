package com.varisahayak.feature.notifications

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a tapped notification asked the app to open.
 *
 * The payload carries a server id and a type and nothing else — see
 * [VariSahayakMessagingService]. The id is resolved to a local record and the authoritative
 * data is read from Room, so a stale or spoofed payload cannot put wrong information on
 * screen.
 */
data class NotificationTarget(
    /** `public.incidents.id`. Server-side, because the sender has no local client id. */
    val incidentServerId: String,
    val type: String,
)

/**
 * Carries a notification tap from the Activity to the navigation graph.
 *
 * A singleton rather than an intent-handling composable because of the cold-start case:
 * the tap arrives in `onCreate` long before the NavHost exists, and again in `onNewIntent`
 * when the app was already running. Both write here; navigation reads when it is ready.
 *
 * [consume] is what makes the target one-shot. Without it, a configuration change would
 * replay the last tap and yank the user back to an incident they had navigated away from.
 */
@Singleton
class NotificationDeepLinkBus @Inject constructor() {

    private val _pending = MutableStateFlow<NotificationTarget?>(null)
    val pending: StateFlow<NotificationTarget?> = _pending.asStateFlow()

    /** Reads a target out of a launch intent, if it carries one. */
    fun offer(intent: Intent?) {
        val incidentId = intent?.getStringExtra(EXTRA_INCIDENT_SERVER_ID) ?: return
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()

        _pending.value = NotificationTarget(incidentServerId = incidentId, type = type)

        // Cleared from the intent as well as consumed from the bus: a singleTask activity
        // keeps the same Intent instance across resumes, so leaving the extra in place
        // makes every return to the app re-trigger the navigation.
        intent.removeExtra(EXTRA_INCIDENT_SERVER_ID)
        intent.removeExtra(EXTRA_TYPE)
    }

    fun consume() {
        _pending.value = null
    }

    companion object {
        const val EXTRA_INCIDENT_SERVER_ID = "com.varisahayak.extra.INCIDENT_SERVER_ID"
        const val EXTRA_TYPE = "com.varisahayak.extra.NOTIFICATION_TYPE"
    }
}
