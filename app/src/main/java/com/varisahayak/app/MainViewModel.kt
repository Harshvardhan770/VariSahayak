package com.varisahayak.app

import androidx.lifecycle.ViewModel
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.core.walkie.WalkieController
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {
    val authState = authRepository.authState
    val profile = profileRepository.observeCurrentProfile()
    val isOnline = connectivityObserver.isOnline
    val walkieState = walkieController.state

    fun startTransmit() = walkieController.startTransmit()

    fun stopTransmit() = walkieController.stopTransmit()
}
