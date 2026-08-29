package com.varisahayak.feature.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.varisahayak.app.navigation.Destination
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentStateMachine
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentDetailUiState(
    val error: AppError? = null,
    val isUpdating: Boolean = false,
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val clientId = savedStateHandle.toRoute<Destination.IncidentDetail>().clientId

    val incident: StateFlow<Incident?> = incidentRepository.observeById(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Only the transitions the state machine actually permits are offered. Showing a
     * button that will be refused is worse than not showing it.
     */
    val availableActions: StateFlow<List<IncidentStatus>> = incidentRepository
        .observeById(clientId)
        .map { current ->
            current?.let { IncidentStateMachine.allowedTransitions(it.status).toList() }.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()

    fun updateStatus(status: IncidentStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, error = null) }

            when (val result = incidentRepository.updateStatus(clientId, status)) {
                is Outcome.Success -> _uiState.update { it.copy(isUpdating = false) }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isUpdating = false, error = result.error)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
