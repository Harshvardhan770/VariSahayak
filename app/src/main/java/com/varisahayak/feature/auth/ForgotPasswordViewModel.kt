package com.varisahayak.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val isSending: Boolean = false,
    val isSent: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) =
        _uiState.update { it.copy(email = value, error = null) }

    fun send() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }

            when (val result = authRepository.sendPasswordReset(_uiState.value.email)) {
                // Reported as sent whether or not the address is registered. Confirming
                // which emails have accounts would let anyone enumerate the volunteer roll.
                is Outcome.Success -> _uiState.update {
                    it.copy(isSending = false, isSent = true)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSending = false, error = result.error)
                }
            }
        }
    }
}
