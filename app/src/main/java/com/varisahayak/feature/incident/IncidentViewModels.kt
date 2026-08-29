package com.varisahayak.feature.incident

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

// --- IncidentListViewModel ---

data class IncidentListUiState(
    val incidents: List<Incident> = emptyList(),
    val selectedCategory: IncidentCategory? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<IncidentCategory?>(null)

    val uiState: StateFlow<IncidentListUiState> = combine(
        incidentRepository.observeAll(),
        _selectedCategory,
    ) { allIncidents, category ->
        val filtered = if (category != null) {
            allIncidents.filter { it.category == category }
        } else {
            allIncidents
        }
        IncidentListUiState(
            incidents = filtered,
            selectedCategory = category,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IncidentListUiState(isLoading = true),
    )

    fun selectCategory(category: IncidentCategory?) {
        _selectedCategory.value = category
    }
}

// --- IncidentDetailViewModel ---

data class IncidentDetailUiState(
    val incident: Incident? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
) : ViewModel() {

    private val _clientId = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<AppError?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<IncidentDetailUiState> = _clientId
        .filterNotNull()
        .flatMapLatest { id -> incidentRepository.observeById(id) }
        .map { IncidentDetailUiState(incident = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = IncidentDetailUiState(isLoading = true),
        )

    fun setClientId(clientId: String) {
        _clientId.value = clientId
    }

    fun updateStatus(newStatus: IncidentStatus) {
        val id = _clientId.value ?: return
        viewModelScope.launch {
            incidentRepository.updateStatus(id, newStatus)
        }
    }
}

// --- ReportIncidentViewModel ---

data class ReportIncidentUiState(
    val category: IncidentCategory? = null,
    val description: String = "",
    val affectedPersonNote: String = "",
    val isSos: Boolean = false,
    val sosBridgeToken: String? = null,
    val currentLocation: GeoPoint? = null,
    val isCapturingLocation: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class ReportIncidentViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportIncidentUiState())
    val uiState: StateFlow<ReportIncidentUiState> = _uiState.asStateFlow()

    init {
        fetchLocation()
    }

    fun initParams(sosBridgeToken: String?, isSos: Boolean) {
        _uiState.value = _uiState.value.copy(
            sosBridgeToken = sosBridgeToken,
            isSos = isSos,
            category = if (isSos) IncidentCategory.MEDICAL else _uiState.value.category
        )
    }

    fun onCategorySelected(category: IncidentCategory) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onAffectedPersonNoteChanged(note: String) {
        _uiState.value = _uiState.value.copy(affectedPersonNote = note)
    }

    fun fetchLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCapturingLocation = true)
            val fix = locationProvider.currentFix(timeoutMillis = 4000)
            _uiState.value = _uiState.value.copy(
                currentLocation = fix.pointOrNull,
                isCapturingLocation = false,
            )
        }
    }

    fun submitReport() {
        val state = _uiState.value
        val cat = state.category
        if (cat == null) {
            _uiState.value = state.copy(
                error = AppError.Validation(field = "category", message = "Select a category.")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            val outcome = incidentRepository.createIncident(
                category = cat,
                description = state.description.ifBlank { "Incident report" },
                location = state.currentLocation,
                photoLocalPath = null,
                affectedPersonNote = state.affectedPersonNote.ifBlank { null },
                isSos = state.isSos,
                sosBridgeToken = state.sosBridgeToken,
            )

            when (outcome) {
                is Outcome.Success -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, isSuccess = true)
                }
                is Outcome.Failure -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, error = outcome.error)
                }
            }
        }
    }
}
