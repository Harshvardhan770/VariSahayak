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
import com.varisahayak.domain.repository.ClassificationRepository
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.usecase.AiSuggestion
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
    /** The fixed QR sign this report is filed against, when one was scanned. */
    val qrLocationToken: String? = null,
    val qrLocationName: String? = null,
    val isSubmitting: Boolean = false,
    /** Null until the classifier answers, and null forever if it never does. */
    val suggestion: AiSuggestion? = null,
    val isSuggesting: Boolean = false,
    val error: AppError? = null,
    val isOffline: Boolean = false,
    val savedClientId: String? = null,
)

/**
 * Why the location line says what it says.
 *
 * [PermissionRequired] and [LocationOff] are split out from [Unavailable] because they are
 * the only two the volunteer can do anything about, and they need different actions — a
 * permission prompt and the system location toggle. Collapsed together they produced a
 * Retry button that could never succeed however many times it was pressed.
 */
enum class LocationCaptureState {
    Idle,
    Capturing,
    Captured,
    Approximate,
    PermissionRequired,
    LocationOff,
    Unavailable,
}

/** The description must say something before it is worth asking a model about it. */
private const val MIN_DESCRIPTION_FOR_SUGGESTION = 15

@HiltViewModel
class ReportIncidentViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val classificationRepository: ClassificationRepository,
    private val locationProvider: LocationProvider,
    private val connectivity: ConnectivityObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Destination.ReportIncident>()

    private val _uiState = MutableStateFlow(
        ReportIncidentUiState(
            isSos = route.isSos,
            qrLocationToken = route.qrLocationToken,
            qrLocationName = route.qrLocationName,
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
        _uiState.update {
            // A changed description invalidates any previous suggestion. Leaving a stale
            // one on screen would attach the model's opinion of the old text to the new.
            it.copy(description = value, error = null, suggestion = null)
        }

    /** True once there is enough text for a suggestion to be worth asking for. */
    val canRequestSuggestion: Boolean
        get() = _uiState.value.description.trim().length >= MIN_DESCRIPTION_FOR_SUGGESTION

    /**
     * Asks the server-side classifier for a category.
     *
     * Explicitly user-initiated rather than fired on every pause in typing: each call is a
     * network round trip and a model invocation, and a volunteer on a metered connection
     * in a field should not be spending either without asking.
     *
     * Nothing here can fail in a way the user sees. A null result simply leaves the
     * suggestion row absent.
     */
    fun requestSuggestion() {
        val description = _uiState.value.description
        if (description.trim().length < MIN_DESCRIPTION_FOR_SUGGESTION) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSuggesting = true) }
            val suggestion = classificationRepository.suggest(
                description = description,
                selectedCategory = _uiState.value.category,
            )
            _uiState.update { it.copy(isSuggesting = false, suggestion = suggestion) }
        }
    }

    /** Applies a suggestion the volunteer chose to accept. Always their decision. */
    fun acceptSuggestion() {
        val suggested = _uiState.value.suggestion?.category ?: return
        _uiState.update { it.copy(category = suggested, suggestion = null) }
    }

    fun dismissSuggestion() = _uiState.update { it.copy(suggestion = null) }

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

                // Named rather than collapsed, so the screen can offer the one action that
                // actually resolves each: the permission prompt, or the system toggle.
                is LocationFix.PermissionDenied -> _uiState.update {
                    it.copy(location = null, locationState = LocationCaptureState.PermissionRequired)
                }

                is LocationFix.LocationDisabled -> _uiState.update {
                    it.copy(location = null, locationState = LocationCaptureState.LocationOff)
                }

                // Timeout and Unavailable really are worth another try — a fix that did not
                // arrive in eight seconds often arrives in the next eight.
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
                sosBridgeToken = state.qrLocationToken,
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
