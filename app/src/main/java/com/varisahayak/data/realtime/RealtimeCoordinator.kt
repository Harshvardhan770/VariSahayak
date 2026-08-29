package com.varisahayak.data.realtime

import android.util.Log
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.AuthState
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.ResponderRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How live the app currently is.
 *
 * Surfaced so a screen can tell the user their view may be stale, rather than silently
 * showing old data as though it were current.
 */
enum class RealtimeHealth {
    /** No session, or nothing subscribed yet. */
    IDLE,

    /** Connecting or reconnecting. Data on screen is from the last reconciliation. */
    CONNECTING,

    /** Subscribed and receiving. */
    LIVE,

    /**
     * The SDK exhausted its reconnection attempts. The app has fallen back to periodic
     * polling and the user is told the connection is degraded — the workflow continues,
     * it is just slower to update.
     */
    DEGRADED,
}

/**
 * Live server changes, delivered into Room.
 *
 * Four rules govern everything below, all from plan 06 §6.4:
 *
 * 1. **The collector is registered before `subscribe()`.** Reversing the two loses every
 *    event that arrives between the socket opening and the flow being collected — which
 *    is exactly the window in which the backlog arrives.
 * 2. **Realtime writes into Room, never into the UI.** Screens observe Room. That is what
 *    makes online and offline behave identically, and it means a dropped socket degrades
 *    freshness rather than blanking a screen.
 * 3. **Realtime is not the source of truth.** Every transition into `SUBSCRIBED` triggers
 *    a reconciliation fetch, because anything that changed while the socket was down was
 *    never delivered and never will be.
 * 4. **A DELETE payload is not an authorisation-filtered signal.** RLS does not apply to
 *    realtime deletes, so a delete reaches every subscriber regardless of policy. Delete
 *    is revoked on all published tables, so one arriving means something is wrong: log it
 *    and reconcile from the server rather than acting on the payload.
 *
 * Reconnection itself is the SDK's job (7s delay, 5 attempts) and is deliberately not
 * reimplemented here.
 */
@Singleton
class RealtimeCoordinator @Inject constructor(
    private val supabase: SupabaseClient,
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository,
    private val authRepository: AuthRepository,
    private val dispatchers: DispatcherProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _health = MutableStateFlow(RealtimeHealth.IDLE)
    val health: StateFlow<RealtimeHealth> = _health.asStateFlow()

    private var sessionJob: Job? = null
    private val channels = mutableListOf<RealtimeChannel>()

    /**
     * Follows the session.
     *
     * Channels come up on sign-in and go down on sign-out, so a signed-out process holds
     * no socket and a new user never inherits the previous user's subscriptions.
     * [AuthState.Unknown] is deliberately ignored — it fires every time the app is
     * backgrounded, and tearing down on it would mean reconnecting on every app switch.
     */
    fun start() {
        if (sessionJob != null) return

        sessionJob = authRepository.authState
            .distinctUntilChanged()
            .onEach { state ->
                when (state) {
                    is AuthState.SignedIn -> connect()
                    is AuthState.SignedOut, is AuthState.SessionExpired -> disconnect()
                    AuthState.Unknown -> Unit
                }
            }
            .launchIn(scope)
    }

    private suspend fun connect() {
        if (channels.isNotEmpty()) return

        _health.value = RealtimeHealth.CONNECTING

        try {
            subscribeIncidents()
            subscribeAssignments()
            subscribeResponders()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // A socket that will not open must not take the app down with it. The rest of
            // the product works from Room and the periodic sync worker.
            Log.w(TAG, "Realtime subscription failed; falling back to polling", error)
            _health.value = RealtimeHealth.DEGRADED
        }
    }

    private suspend fun subscribeIncidents() {
        val channel = supabase.channel(CHANNEL_INCIDENTS)

        // Collector first. Always.
        channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "incidents" }
            .onEach(::onIncidentChange)
            .launchIn(scope)

        watchStatus(channel) { incidentRepository.refreshFromServer() }

        channel.subscribe()
        channels += channel
    }

    private suspend fun subscribeAssignments() {
        val channel = supabase.channel(CHANNEL_ASSIGNMENTS)

        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "incident_assignments"
        }
            .onEach { action ->
                // The assignment row itself is not cached; what matters is that the
                // incident's assignee changed, so pull the incident that now owns it.
                logIfDelete(action, "incident_assignments")
                incidentRepository.refreshFromServer()
            }
            .launchIn(scope)

        watchStatus(channel) { incidentRepository.refreshFromServer() }

        channel.subscribe()
        channels += channel
    }

    private suspend fun subscribeResponders() {
        val channel = supabase.channel(CHANNEL_RESPONDERS)

        channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "responders" }
            .onEach { action ->
                logIfDelete(action, "responders")
                responderRepository.refresh(areaId = null)
            }
            .launchIn(scope)

        watchStatus(channel) { responderRepository.refresh(areaId = null) }

        channel.subscribe()
        channels += channel
    }

    /**
     * Reconciles on every entry into SUBSCRIBED, not just the first.
     *
     * The first is the initial connect; every subsequent one is a reconnection, and a
     * reconnection is precisely the case where events were missed. Treating only the first
     * as interesting is the bug this guards against.
     */
    private fun watchStatus(channel: RealtimeChannel, reconcile: suspend () -> Unit) {
        channel.status
            .onEach { status ->
                when (status) {
                    RealtimeChannel.Status.SUBSCRIBED -> {
                        _health.value = RealtimeHealth.LIVE
                        Log.d(TAG, "SUBSCRIBED; reconciling from server")
                        runCatching { reconcile() }
                            .onFailure { Log.w(TAG, "Reconciliation after resubscribe failed", it) }
                    }

                    RealtimeChannel.Status.SUBSCRIBING -> _health.value = RealtimeHealth.CONNECTING

                    RealtimeChannel.Status.UNSUBSCRIBED,
                    RealtimeChannel.Status.UNSUBSCRIBING,
                    -> if (_health.value == RealtimeHealth.LIVE) {
                        _health.value = RealtimeHealth.CONNECTING
                    }
                }
            }
            .launchIn(scope)
    }

    private suspend fun onIncidentChange(action: PostgresAction) {
        logIfDelete(action, "incidents")

        // Deliberately a refetch rather than decoding the payload into Room.
        //
        // The payload is the raw table row; Room's copy is reconciled through
        // IncidentDao.reconcileFromServer, which knows not to clobber a record that is
        // still waiting to sync. Writing the payload straight in would let a realtime
        // frame overwrite unsynced local work — the one thing the offline design forbids.
        runCatching { incidentRepository.refreshFromServer() }
            .onFailure { Log.w(TAG, "Refresh after realtime incident change failed", it) }
    }

    /**
     * A delete on a published table should be impossible — delete is revoked from
     * `authenticated` on every one of them precisely because RLS cannot filter realtime
     * delete payloads. If one arrives, it is a signal that something changed server-side,
     * not an instruction to remove a row locally.
     */
    private fun logIfDelete(action: PostgresAction, table: String) {
        if (action is PostgresAction.Delete) {
            Log.w(
                TAG,
                "Unexpected realtime DELETE on $table. Delete is revoked on published " +
                    "tables and RLS does not filter delete payloads, so this is not acted " +
                    "on — reconciling from the server instead.",
            )
        }
    }

    private suspend fun disconnect() {
        channels.forEach { channel ->
            runCatching { supabase.realtime.removeChannel(channel) }
                .onFailure { Log.w(TAG, "Failed to remove channel", it) }
        }
        channels.clear()
        _health.value = RealtimeHealth.IDLE
    }

    /** Tears everything down. For process shutdown and tests. */
    fun stop() {
        scope.launch { disconnect() }
        sessionJob?.cancel()
        sessionJob = null
    }

    private companion object {
        const val TAG = "RealtimeCoordinator"

        // Distinct channels per table rather than one broad channel: realtime authorises
        // every event per subscriber, so a single channel carrying everything makes every
        // subscriber pay for every other subscriber's traffic (contract §0.6).
        const val CHANNEL_INCIDENTS = "varisahayak-incidents"
        const val CHANNEL_ASSIGNMENTS = "varisahayak-incident-assignments"
        const val CHANNEL_RESPONDERS = "varisahayak-responders"
    }
}
