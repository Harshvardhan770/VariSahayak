package com.varisahayak.feature.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.data.sync.SyncScheduler
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class IncidentListUiState(
    val incidents: List<Incident> = emptyList(),
    val unsyncedCount: Int = 0,
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    incidentRepository: IncidentRepository,
    connectivity: ConnectivityObserver,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    init {
        // Opening the list is a request to see current work. The read path stays local;
        // this only asks the worker to top the local store up in the background.
        syncScheduler.requestSync()
    }

    /**
     * Reads from Room, never from the network. That is what makes this list identical
     * online and offline, and why it renders instantly on a cold start in a dead spot.
     */
    val uiState: StateFlow<IncidentListUiState> = combine(
        incidentRepository.observeAll(),
        incidentRepository.observeUnsyncedCount(),
        connectivity.isOnline,
    ) { incidents, unsynced, isOnline ->
        IncidentListUiState(
            incidents = incidents,
            unsyncedCount = unsynced,
            isOffline = !isOnline,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IncidentListUiState(),
    )

    /** Manual retry for the "N waiting to sync" banner. */
    fun retrySync() = syncScheduler.requestSync()
}
