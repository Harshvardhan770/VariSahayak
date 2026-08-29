package com.varisahayak.feature.lostfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.location.LocationFix
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.domain.model.LostFoundItem
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.repository.LostFoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LostFoundUiState(
    val query: String = "",
    val isReportOpen: Boolean = false,
    val kind: LostFoundKind = LostFoundKind.PERSON,
    val title: String = "",
    val description: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val justReported: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LostFoundViewModel @Inject constructor(
    private val lostFoundRepository: LostFoundRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LostFoundUiState())
    val uiState: StateFlow<LostFoundUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow("")

    /**
     * Search reads from the local database, so it works with no connectivity — which is
     * exactly when a volunteer is most likely to be looking for a match on the route.
     */
    val items: StateFlow<List<LostFoundItem>> = query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .flatMapLatest { text ->
            if (text.isBlank()) {
                lostFoundRepository.observeAll()
            } else {
                lostFoundRepository.search(text)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(value: String) {
        _uiState.update { it.copy(query = value) }
        query.value = value
    }

    fun setReportOpen(open: Boolean) {
        _uiState.update {
            it.copy(isReportOpen = open, error = null, justReported = false)
        }
    }

    fun onKindChanged(kind: LostFoundKind) = _uiState.update { it.copy(kind = kind) }

    fun onTitleChanged(value: String) = _uiState.update { it.copy(title = value, error = null) }

    fun onDescriptionChanged(value: String) = _uiState.update { it.copy(description = value) }

    fun submitReport() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            // Best-effort location. A missing-person report is never blocked on a fix —
            // "last seen somewhere near here" filed now beats a precise report filed later.
            val location = when (val fix = locationProvider.currentFix()) {
                is LocationFix.Available -> fix.point
                is LocationFix.LastKnown -> fix.point
                else -> null
            }

            when (
                val result = lostFoundRepository.report(
                    kind = state.kind,
                    title = state.title,
                    description = state.description,
                    lastSeenLocation = location,
                    qrToken = null,
                    photoLocalPath = null,
                )
            ) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isReportOpen = false,
                        title = "",
                        description = "",
                        justReported = true,
                    )
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }

    fun dismissConfirmation() = _uiState.update { it.copy(justReported = false) }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
