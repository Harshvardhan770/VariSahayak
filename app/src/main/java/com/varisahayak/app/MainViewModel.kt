package com.varisahayak.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.location.LocationTracker
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.core.walkie.WalkieController
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.AuthState
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.ProfileRepository
import com.varisahayak.data.sync.SyncScheduler
import com.varisahayak.feature.notifications.NotificationDeepLinkBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the app shell — the chrome that persists across every destination.
 *
 * The radio and the connectivity indicator live here rather than on individual screens
 * because both are properties of the session, not of whatever surface happens to be open.
 * A responder must not drop off the channel by navigating from the map to an incident.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    connectivityObserver: ConnectivityObserver,
    private val locationTracker: LocationTracker,
    private val walkieController: WalkieController,
    private val incidentRepository: IncidentRepository,
    private val syncScheduler: SyncScheduler,
    private val deepLinkBus: NotificationDeepLinkBus,
) : ViewModel() {
    val authState = authRepository.authState
    val profile = profileRepository.observeCurrentProfile()
    val isOnline = connectivityObserver.isOnline
    val walkieState = walkieController.state

    /** A tapped notification waiting to be turned into a navigation. */
    val pendingNotification = deepLinkBus.pending

    init {
        // A restored session with no cached profile goes nowhere. Every destination past
        // the splash screen is chosen from the role, so the navigation in VariSahayakApp
        // holds until a profile appears — and if one never does, it holds forever and the
        // app is a splash screen with no way out.
        //
        // Fetch it once per session, and end the session if it cannot be had. Landing back
        // on sign-in is a poor outcome; it is a far better one than a frozen splash.
        viewModelScope.launch {
            authRepository.authState
                .filterIsInstance<AuthState.SignedIn>()
                .map { it.userId }
                .distinctUntilChanged()
                .collect(::ensureProfileFor)
        }
    }

    /**
     * The profile has to belong to [userId]. The store keeps a single row, so a leftover
     * from a previous account would otherwise satisfy the check and route this user under
     * somebody else's role.
     */
    private suspend fun ensureProfileFor(userId: String) {
        if (cachedProfileMatches(userId)) return
        if (profileRepository.refresh(userId) is Outcome.Success) return
        // The sign-in screen refreshes the same profile on the path that creates a
        // session. If it won that race its result is just as good as ours, and signing
        // out over a redundant request would undo a sign-in that actually worked.
        if (cachedProfileMatches(userId)) return

        authRepository.signOut()
        profileRepository.clearCache()
    }

    private suspend fun cachedProfileMatches(userId: String): Boolean =
        profileRepository.observeCurrentProfile().first()?.userId == userId

    /**
     * Resolves a notification's server incident id to the local client id routes use.
     *
     * Returns null when this device has never seen the incident — which is the normal
     * case for a push that arrives before the sync worker has run. A sync is requested so
     * the record lands shortly; the user sees their dashboard rather than an empty screen,
     * and the incident is in the list when they look.
     */
    fun resolveNotificationTarget(serverId: String, onResolved: (String?) -> Unit) {
        viewModelScope.launch {
            val clientId = incidentRepository.findClientIdByServerId(serverId)
            if (clientId == null) syncScheduler.requestSync()
            onResolved(clientId)
        }
    }

    /**
     * Position publishing, driven by the shell rather than by any one screen.
     *
     * Foreground-scoped on purpose: [LocationTracker] holds no service and no wake lock,
     * so tracking has to end when the app does. Both calls are idempotent.
     */
    fun startLocationTracking() = locationTracker.start()

    fun stopLocationTracking() = locationTracker.stop()

    fun consumeNotification() = deepLinkBus.consume()

    fun startTransmit() = walkieController.startTransmit()

    fun stopTransmit() = walkieController.stopTransmit()
}
