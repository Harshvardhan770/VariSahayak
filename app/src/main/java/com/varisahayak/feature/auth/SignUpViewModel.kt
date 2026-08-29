package com.varisahayak.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.SignUpResult
import com.varisahayak.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val selectedRole: UserRole = UserRole.VOLUNTEER,
    val error: AppError? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    /** Set when the account exists but the email link has not been clicked yet. */
    val confirmationEmail: String? = null,
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onDisplayNameChanged(displayName: String) {
        _uiState.update { it.copy(displayName = displayName, error = null) }
    }

    fun onRoleChanged(role: UserRole) {
        _uiState.update { it.copy(selectedRole = role, error = null) }
    }

    fun signUp() {
        val email = _uiState.value.email
        val password = _uiState.value.password
        val displayName = _uiState.value.displayName
        val role = _uiState.value.selectedRole

        // Field-level validation lives in the repository so the sign-in, sign-up, and
        // reset flows all reject the same inputs with the same wording.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = authRepository.signUp(email, password, displayName, role)) {
                is Outcome.Success -> _uiState.update {
                    when (val signUp = result.data) {
                        SignUpResult.SignedIn ->
                            it.copy(isLoading = false, isSuccess = true)

                        is SignUpResult.ConfirmationRequired -> it.copy(
                            isLoading = false,
                            isSuccess = true,
                            confirmationEmail = signUp.email,
                        )
                    }
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
