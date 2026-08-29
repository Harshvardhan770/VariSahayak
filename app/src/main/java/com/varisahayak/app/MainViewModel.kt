package com.varisahayak.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.core.walkie.WalkieController
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.ProfileRepository
import com.varisahayak.data.sync.SyncScheduler
import com.varisahayak.feature.notifications.NotificationDeepLinkBus
import dagger.hilt.android.lifecycle.HiltViewModel
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
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    connectivityObserver: ConnectivityObserver,
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

    fun consumeNotification() = deepLinkBus.consume()

    fun startTransmit() = walkieController.startTransmit()

    fun stopTransmit() = walkieController.stopTransmit()
}
