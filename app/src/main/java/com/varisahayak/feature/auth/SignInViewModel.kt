package com.varisahayak.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.common.UiState
import com.varisahayak.data.sync.SyncScheduler
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.DeviceTokenRepository
import com.varisahayak.domain.repository.ProfileRepository
import com.varisahayak.core.common.onFailure
import com.varisahayak.core.common.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val error: AppError? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val syncScheduler: SyncScheduler,
    private val deviceTokenRepository: DeviceTokenRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun signIn() {
        val email = _uiState.value.email
        val password = _uiState.value.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = AppError.Validation(message = "Email and password are required")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            authRepository.signIn(email, password)
                .onSuccess {
                    // Auth success triggers AuthState.SignedIn, handled by VariSahayakApp
                    // — but only once a profile exists. Every destination past this screen
                    // is chosen from the role, so the navigation state machine holds until
                    // the profile resolves and a session without one goes nowhere at all.
                    //
                    // That made a server-side failure invisible: sign-in returned success,
                    // no error was raised, and the user was left on a form that had already
                    // worked. A profile that cannot be resolved is a failed sign-in.
                    if (!resolveProfile()) {
                        // Drop the session rather than keep one the app cannot route on.
                        // Left in place it survives a restart, and the next cold start
                        // holds on the splash screen forever waiting for the same profile.
                        authRepository.signOut()
                        // A profile left over from whoever signed in last is exactly what
                        // resolveProfile refused to accept. It must not outlive the
                        // attempt either, or the next one inherits the wrong role.
                        profileRepository.clearCache()
                        _uiState.update {
                            it.copy(isLoading = false, error = AppError.ProfileUnavailable())
                        }
                        return@onSuccess
                    }
                    // Fill the local store before the first dashboard frame, so a
                    // responder signing in sees the open queue rather than an empty one.
                    syncScheduler.requestSync()
                    // A token issued before anyone signed in was never attached to a
                    // profile. This is where it gets one. Failure is logged and ignored:
                    // push is best-effort and must never block a sign-in.
                    deviceTokenRepository.registerCurrentToken()
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error) }
                }
        }
    }

    /**
     * Brings the signed-in user's profile into the local store, and reports whether the
     * app now has one to route on.
     *
     * A refresh that fails while this user's own profile is already cached is not worth
     * reporting — that is the offline-capable path the cache exists for. The cached row
     * has to belong to *this* user, though: the store keeps one profile and a stale row
     * from a previous account would otherwise sign the new user in under the old role.
     */
    private suspend fun resolveProfile(): Boolean {
        val userId = authRepository.currentUserId() ?: return false
        if (profileRepository.refresh(userId) is Outcome.Success) return true
        return profileRepository.observeCurrentProfile().first()?.userId == userId
    }
}
