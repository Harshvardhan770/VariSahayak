package com.varisahayak.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.location.LocationFix
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.usecase.Hotspot
import com.varisahayak.domain.usecase.HotspotCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncidentMapUiState(
    val incidents: List<Incident> = emptyList(),
    val hotspots: List<Hotspot> = emptyList(),
    val myLocation: GeoPoint? = null,
    val locationMessage: LocationMessage? = null,
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * What to tell the user about their location, if anything. Null means "working normally"
 * — the map does not nag when there is nothing to act on.
 */
enum class LocationMessage {
    PermissionDenied,
    PermissionApproximate,
    LocationDisabled,
    Unavailable,
}

@HiltViewModel
class IncidentMapViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val locationProvider: LocationProvider,
    private val hotspotCalculator: HotspotCalculator,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncidentMapUiState())
    val uiState: StateFlow<IncidentMapUiState> = _uiState.asStateFlow()

    init {
        // Incidents come from the local database, so the map's pins are populated even
        // when the tile layer underneath them cannot load.
        combine(
            incidentRepository.observeOpen(),
            connectivityObserver.isOnline,
        ) { incidents, isOnline ->
            incidents to isOnline
        }
            .onEach { (incidents, isOnline) ->
                _uiState.update {
                    it.copy(
                        incidents = incidents,
                        hotspots = hotspotCalculator.cluster(incidents),
                        isOffline = !isOnline,
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Called once the permission flow settles. Safe to call repeatedly — a denied or
     * disabled result simply updates the message and leaves the map fully usable.
     */
    fun refreshMyLocation() {
        viewModelScope.launch {
            when (val fix = locationProvider.currentFix()) {
                is LocationFix.Available -> _uiState.update {
                    it.copy(
                        myLocation = fix.point,
                        locationMessage = if (fix.point.isApproximate) {
                            LocationMessage.PermissionApproximate
                        } else {
                            null
                        },
                    )
                }

                is LocationFix.LastKnown -> _uiState.update {
                    it.copy(myLocation = fix.point, locationMessage = null)
                }

                LocationFix.PermissionDenied -> _uiState.update {
                    it.copy(locationMessage = LocationMessage.PermissionDenied)
                }

                LocationFix.LocationDisabled -> _uiState.update {
                    it.copy(locationMessage = LocationMessage.LocationDisabled)
                }

                LocationFix.Timeout, is LocationFix.Unavailable -> _uiState.update {
                    it.copy(locationMessage = LocationMessage.Unavailable)
                }
            }
        }
    }

    fun dismissLocationMessage() {
        _uiState.update { it.copy(locationMessage = null) }
    }
}
