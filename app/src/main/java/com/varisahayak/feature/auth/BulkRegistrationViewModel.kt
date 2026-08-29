package com.varisahayak.feature.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.data.utils.ExcelUserParser
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.BulkSignUpResult
import com.varisahayak.domain.repository.BulkUserRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BulkRegistrationUiState(
    val selectedUri: Uri? = null,
    val fileName: String? = null,
    val parsedUsers: List<BulkUserRequest> = emptyList(),
    val isParsing: Boolean = false,
    val isRegistering: Boolean = false,
    val result: BulkSignUpResult? = null,
    val error: AppError? = null
)

@HiltViewModel
class BulkRegistrationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BulkRegistrationUiState())
    val uiState: StateFlow<BulkRegistrationUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri, name: String) {
        _uiState.update { it.copy(selectedUri = uri, fileName = name, error = null, result = null) }
        parseFile(uri)
    }

    private fun parseFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true) }
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val users = ExcelUserParser.parse(input)
                    _uiState.update { it.copy(parsedUsers = users, isParsing = false) }
                } ?: throw Exception("Could not open file")
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isParsing = false, 
                        error = AppError.Validation(message = "Failed to parse Excel: ${e.message}")
                    ) 
                }
            }
        }
    }

    fun startImport() {
        val users = _uiState.value.parsedUsers
        if (users.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, error = null) }
            try {
                val result = authRepository.bulkSignUp(users)
                _uiState.update { it.copy(isRegistering = false, result = result) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isRegistering = false, 
                        error = AppError.Unknown(e)
                    ) 
                }
            }
        }
    }

    fun clear() {
        _uiState.value = BulkRegistrationUiState()
    }
}
