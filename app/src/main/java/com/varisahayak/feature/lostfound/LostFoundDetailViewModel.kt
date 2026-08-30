package com.varisahayak.feature.lostfound

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.varisahayak.app.navigation.Destination
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.LostFoundMatch
import com.varisahayak.domain.model.LostFoundReport
import com.varisahayak.domain.model.MatchStatus
import com.varisahayak.domain.repository.LostFoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LostFoundDetailUiState(
    val report: LostFoundReport? = null,
    val isReviewing: Boolean = false,
    val error: AppError? = null,
    val justReviewed: MatchStatus? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LostFoundDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lostFoundRepository: LostFoundRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Destination.LostFoundDetail>()
    val clientId = route.clientId

    private val _uiState = MutableStateFlow(LostFoundDetailUiState())
    val uiState: StateFlow<LostFoundDetailUiState> = combine(
        _uiState,
        lostFoundRepository.observeById(clientId)
    ) { state, report ->
        state.copy(report = report)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LostFoundDetailUiState())

    val candidates: StateFlow<List<MatchCandidate>> =
        lostFoundRepository.observeMatchesForReport(clientId)
            .flatMapLatest { matches ->
                if (matches.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    combine(
                        matches.map { match ->
                            combine(
                                lostFoundRepository.observeById(match.lostReportClientId),
                                lostFoundRepository.observeById(match.foundReportClientId),
                            ) { lost, found -> MatchCandidate(match, lost, found) }
                        },
                    ) { it.toList() }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirm(matchClientId: String, note: String? = null) =
        review(matchClientId, MatchStatus.CONFIRMED, note)

    fun reject(matchClientId: String, note: String? = null) =
        review(matchClientId, MatchStatus.REJECTED, note)

    private fun review(matchClientId: String, verdict: MatchStatus, note: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReviewing = true, error = null) }

            when (val result = lostFoundRepository.reviewMatch(matchClientId, verdict, note)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(isReviewing = false, justReviewed = verdict)
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isReviewing = false, error = result.error)
                }
            }
        }
    }

    fun dismissConfirmation() = _uiState.update { it.copy(justReviewed = null) }
    fun dismissError() = _uiState.update { it.copy(error = null) }
}
