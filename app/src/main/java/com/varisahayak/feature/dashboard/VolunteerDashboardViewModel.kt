package com.varisahayak.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.Profile
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VolunteerDashboardUiState(
    val profile: Profile? = null,
    val activeSos: List<Incident> = emptyList(),
    val assigned: List<Incident> = emptyList(),
    val unsyncedCount: Int = 0,
    val isOffline: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VolunteerDashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    incidentRepository: IncidentRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val profile = profileRepository.observeCurrentProfile()

    val uiState: StateFlow<VolunteerDashboardUiState> = combine(
        profile,
        incidentRepository.observeActiveSos(),
        // Assignments depend on who is signed in, so this has to re-subscribe when the
        // profile resolves rather than being fixed at construction.
        profile.flatMapLatest { current ->
            current?.let { incidentRepository.observeAssignedTo(it.userId) } ?: flowOf(emptyList())
        },
        incidentRepository.observeUnsyncedCount(),
        connectivity.isOnline,
    ) { profile, sos, assigned, unsynced, isOnline ->
        VolunteerDashboardUiState(
            profile = profile,
            activeSos = sos,
            assigned = assigned,
            unsyncedCount = unsynced,
            isOffline = !isOnline,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VolunteerDashboardUiState(),
    )

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
