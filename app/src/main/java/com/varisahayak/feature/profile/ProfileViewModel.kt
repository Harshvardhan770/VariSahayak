package com.varisahayak.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.Profile
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.DeviceTokenRepository
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: Profile? = null,
    val unsyncedCount: Int = 0,
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val incidentRepository: IncidentRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.observeCurrentProfile(),
        incidentRepository.observeUnsyncedCount(),
        _isLoading,
        _error,
    ) { profile, unsynced, isLoading, error ->
        ProfileUiState(
            profile = profile,
            unsyncedCount = unsynced,
            isLoading = isLoading,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(isLoading = true),
    )

    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Before signOut, not after: removing the token needs the session that is
                // about to be destroyed. Skipping this leaves a shared device delivering
                // the previous volunteer's assignments to whoever picks it up next.
                deviceTokenRepository.unregister()
                authRepository.signOut()
                profileRepository.clearCache()
            } catch (e: Exception) {
                _error.value = AppError.Unknown(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun syncPending() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val outcome = incidentRepository.syncPending()) {
                is Outcome.Success -> {
                    // Refreshed
                }
                is Outcome.Failure -> {
                    _error.value = outcome.error
                }
            }
            _isLoading.value = false
        }
    }
}
