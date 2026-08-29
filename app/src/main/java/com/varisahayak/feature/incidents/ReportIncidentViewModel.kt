package com.varisahayak.feature.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.varisahayak.app.navigation.Destination
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.location.LocationFix
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportIncidentUiState(
    val category: IncidentCategory? = null,
    val description: String = "",
    val affectedPersonNote: String = "",
    val location: GeoPoint? = null,
    val locationState: LocationCaptureState = LocationCaptureState.Idle,
    val isSos: Boolean = false,
    val sosBridgeToken: String? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val isOffline: Boolean = false,
    val savedClientId: String? = null,
)

enum class LocationCaptureState { Idle, Capturing, Captured, Approximate, Unavailable }

@HiltViewModel
class ReportIncidentViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val locationProvider: LocationProvider,
    private val connectivity: ConnectivityObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Destination.ReportIncident>()

    private val _uiState = MutableStateFlow(
        ReportIncidentUiState(
            isSos = route.isSos,
            sosBridgeToken = route.sosBridgeToken,
            // An SOS Bridge request arrives with no category chosen; MEDICAL is the most
            // common reason a volunteer scans a tag, but it stays editable.
            category = if (route.isSos) IncidentCategory.MEDICAL else null,
            isOffline = !connectivity.isCurrentlyOnline(),
        ),
    )
    val uiState: StateFlow<ReportIncidentUiState> = _uiState.asStateFlow()

    init {
        captureLocation()
    }

    fun onCategoryChanged(category: IncidentCategory) =
        _uiState.update { it.copy(category = category, error = null) }

    fun onDescriptionChanged(value: String) =
        _uiState.update { it.copy(description = value, error = null) }

    fun onAffectedPersonNoteChanged(value: String) =
        _uiState.update { it.copy(affectedPersonNote = value) }

    /**
     * Best-effort location. Never blocks the form: the submit button stays enabled
     * throughout, and a failure only changes the label shown next to it.
     */
    fun captureLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(locationState = LocationCaptureState.Capturing) }

            when (val fix = locationProvider.currentFix()) {
                is LocationFix.Available -> _uiState.update {
                    it.copy(
                        location = fix.point,
                        locationState = if (fix.point.isApproximate) {
                            LocationCaptureState.Approximate
                        } else {
                            LocationCaptureState.Captured
                        },
                    )
                }

                is LocationFix.LastKnown -> _uiState.update {
                    it.copy(location = fix.point, locationState = LocationCaptureState.Approximate)
                }

                else -> _uiState.update {
                    it.copy(location = null, locationState = LocationCaptureState.Unavailable)
                }
            }
        }
    }

    fun submit() {
        val state = _uiState.value

        val category = state.category
        if (category == null) {
            _uiState.update {
                it.copy(error = AppError.Validation("category", CATEGORY_REQUIRED))
            }
            return
        }
        if (state.description.isBlank()) {
            _uiState.update {
                it.copy(error = AppError.Validation("description", DESCRIPTION_REQUIRED))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            // This writes locally and returns; it does not wait for the network. An
            // incident is never lost to a bad signal, and the volunteer is not held on a
            // spinner while standing next to somebody who needs help.
            val result = incidentRepository.createIncident(
                category = category,
                description = state.description,
                location = state.location,
                photoLocalPath = null,
                affectedPersonNote = state.affectedPersonNote.takeIf { it.isNotBlank() },
                isSos = state.isSos,
                sosBridgeToken = state.sosBridgeToken,
            )

            when (result) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, savedClientId = result.data.clientId)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val CATEGORY_REQUIRED = "Choose a category."
        const val DESCRIPTION_REQUIRED = "Add a short description."
    }
}
