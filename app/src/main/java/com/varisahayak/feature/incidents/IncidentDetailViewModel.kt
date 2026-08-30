package com.varisahayak.feature.incidents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.varisahayak.app.navigation.Destination
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.Capabilities
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.TimelineEvent
import com.varisahayak.domain.model.capabilities
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.ProfileRepository
import com.varisahayak.domain.usecase.IncidentActionPolicy
import com.varisahayak.domain.usecase.IncidentTimelineMetrics
import com.varisahayak.domain.usecase.ResponseMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val profileRepository: ProfileRepository,
    private val timelineMetrics: IncidentTimelineMetrics,
    private val clock: Clock,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val clientId = savedStateHandle.toRoute<Destination.IncidentDetail>().clientId

    val incident: StateFlow<Incident?> = incidentRepository.observeById(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What this role may do at all, regardless of which incident is open. */
    val capabilities: StateFlow<Capabilities> = profileRepository.observeCurrentProfile()
        .map { it.capabilities }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Capabilities.NONE)

    /**
     * The actions to offer: legal by the state machine, permitted for this role, and
     * permitted on *this* incident given whether the user reported it, is assigned to it,
     * or is neither. Showing a button that will be refused is worse than not showing it —
     * and every one of these rules is enforced again by RLS server-side.
     */
    val availableActions: StateFlow<List<IncidentStatus>> = combine(
        incidentRepository.observeById(clientId),
        profileRepository.observeCurrentProfile(),
    ) { current, profile ->
        if (current == null) {
            emptyList()
        } else {
            IncidentActionPolicy.allowedActions(
                incident = current,
                capabilities = profile.capabilities,
                userId = profile?.userId ?: authRepository.currentUserId(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The incident's lifecycle, oldest first.
     *
     * Realtime rather than polled: `incident_events` is in the `supabase_realtime`
     * publication and RealtimeCoordinator already refreshes on every change, so a command
     * user watching this screen sees an acceptance or an arrival appear as it happens.
     */
    val timeline: StateFlow<List<TimelineEvent>> = incidentRepository.observeTimeline(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Response timings, recomputed whenever the trail changes.
     *
     * Derived from the events rather than stored, so a metric read a week later gives the
     * same answer as one read now.
     */
    val metrics: StateFlow<ResponseMetrics> = timeline
        .map { timelineMetrics.calculate(it, clock.nowEpochMillis()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            timelineMetrics.calculate(emptyList(), clock.nowEpochMillis()),
        )

    init {
        // Most of this trail is written by database triggers this device never saw, so it
        // has to be fetched rather than waited for.
        //
        // Re-fetched whenever the incident row itself changes, which is precisely when new
        // events exist: RealtimeCoordinator is already subscribed to `incidents` and
        // refreshes on every change, so an acceptance or an arrival pulls its own events in
        // behind it. That reuses the existing realtime rather than opening a second channel
        // for the one screen that needs it, and it means no polling.
        viewModelScope.launch {
            incidentRepository.observeById(clientId)
                .map { it?.status to it?.updatedAtEpochMillis }
                .distinctUntilChanged()
                .collect { incidentRepository.refreshTimeline(clientId) }
        }
    }

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
