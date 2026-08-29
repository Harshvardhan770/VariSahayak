package com.varisahayak.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.Profile
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.ProfileRepository
import com.varisahayak.domain.repository.ResponderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val profile: Profile? = null,
    val openIncidents: List<Incident> = emptyList(),
    val assignedIncidents: List<Incident> = emptyList(),
    val activeSosList: List<Incident> = emptyList(),
    val unsyncedCount: Int = 0,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val sosRaisedSuccess: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val incidentRepository: IncidentRepository,
    private val responderRepository: ResponderRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<AppError?>(null)
    private val _sosRaisedSuccess = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        profileRepository.observeCurrentProfile(),
        incidentRepository.observeOpen(),
        incidentRepository.observeActiveSos(),
        incidentRepository.observeUnsyncedCount(),
    ) { profile, openList, sosList, unsynced ->
        val assigned = if (profile != null) {
            openList.filter { it.assigneeId == profile.userId }
        } else emptyList()

        DashboardUiState(
            profile = profile,
            openIncidents = openList,
            assignedIncidents = assigned,
            activeSosList = sosList,
            unsyncedCount = unsynced,
            isLoading = false,
            error = _error.value,
            sosRaisedSuccess = _sosRaisedSuccess.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true),
    )

    fun raiseEmergencySos(note: String = "Emergency SOS raised by Volunteer") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // An SOS with no location tells a responder that somebody needs help but not
            // where to go, which is close to useless. Best-effort only: a failed or slow
            // fix still raises the alert rather than blocking it.
            val location = locationProvider.currentFix().pointOrNull

            val outcome = incidentRepository.createIncident(
                category = IncidentCategory.MEDICAL,
                description = note,
                location = location,
                photoLocalPath = null,
                affectedPersonNote = null,
                isSos = true,
                sosBridgeToken = null,
            )
            when (outcome) {
                is Outcome.Success -> {
                    _sosRaisedSuccess.value = true
                }
                is Outcome.Failure -> {
                    _error.value = outcome.error
                }
            }
            _isLoading.value = false
        }
    }

    fun updateStatus(clientId: String, newStatus: IncidentStatus) {
        viewModelScope.launch {
            incidentRepository.updateStatus(clientId, newStatus)
        }
    }

    fun clearSosSuccessFlag() {
        _sosRaisedSuccess.value = false
    }
}
