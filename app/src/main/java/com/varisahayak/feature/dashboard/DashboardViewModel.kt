package com.varisahayak.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.data.sync.SyncScheduler
import com.varisahayak.domain.model.Capabilities
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.Profile
import com.varisahayak.domain.model.ResponderAvailability
import com.varisahayak.domain.model.capabilities
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
    /** What this role may do. Denies everything until the profile resolves. */
    val capabilities: Capabilities = Capabilities.NONE,
    val openIncidents: List<Incident> = emptyList(),
    val assignedIncidents: List<Incident> = emptyList(),
    val activeSosList: List<Incident> = emptyList(),
    val unsyncedCount: Int = 0,
    /** Drives the offline queue pill. UI messaging only — it never gates a write. */
    val isOffline: Boolean = false,
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
    private val syncScheduler: SyncScheduler,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    init {
        // A responder or organiser may never write anything, and sync used to be driven
        // only by writes — so their dashboard stayed empty no matter how many incidents
        // volunteers filed. Opening the dashboard now asks for a pull.
        syncScheduler.requestSync()
    }

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<AppError?>(null)
    private val _sosRaisedSuccess = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        profileRepository.observeCurrentProfile(),
        incidentRepository.observeOpen(),
        incidentRepository.observeActiveSos(),
        incidentRepository.observeUnsyncedCount(),
        connectivityObserver.isOnline,
    ) { profile, openList, sosList, unsynced, isOnline ->
        val assigned = if (profile != null) {
            openList.filter { it.assigneeId == profile.userId }
        } else emptyList()

        DashboardUiState(
            profile = profile,
            capabilities = profile.capabilities,
            openIncidents = openList,
            assignedIncidents = assigned,
            activeSosList = sosList,
            unsyncedCount = unsynced,
            isOffline = !isOnline,
            isLoading = false,
            error = _error.value,
            sosRaisedSuccess = _sosRaisedSuccess.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true),
    )

    /**
     * The signed-in responder's own shift state.
     *
     * Exposed separately rather than folded into [DashboardUiState] because only
     * responders have one, and because `combine` already carries five flows — a sixth
     * would push it onto the untyped vararg overload for a value most roles ignore.
     */
    val availability: StateFlow<ResponderAvailability?> =
        responderRepository.observeOwnAvailability()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Publishes a shift change.
     *
     * Availability is what the server-side matcher filters on before it scores anybody, so
     * a responder who is not AVAILABLE is not a candidate at all. This is the control that
     * puts them in the pool.
     */
    fun setAvailability(next: ResponderAvailability) {
        viewModelScope.launch {
            when (val outcome = responderRepository.setAvailability(next)) {
                is Outcome.Success -> _error.value = null
                is Outcome.Failure -> _error.value = outcome.error
            }
        }
    }

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

    /**
     * Manual retry behind the "N waiting to sync" pill.
     *
     * Enqueues rather than uploading directly: WorkManager already owns the retry policy
     * and the network constraint, and a second upload path would be a second thing that
     * can disagree about what has been sent.
     */
    fun retrySync() = syncScheduler.requestSync()
}
