package com.varisahayak.feature.lostfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * One candidate, with both sides loaded so a volunteer can compare them.
 *
 * The reports are carried rather than looked up on demand because the whole purpose of
 * this screen is putting the two descriptions side by side.
 */
data class MatchCandidate(
    val match: LostFoundMatch,
    val lost: LostFoundReport?,
    val found: LostFoundReport?,
)

data class MatchReviewUiState(
    val isReviewing: Boolean = false,
    val error: AppError? = null,
    val justReviewed: MatchStatus? = null,
)

/**
 * The human confirmation gate.
 *
 * Nothing in this product marks a case reunited except a person tapping confirm here.
 * Facial similarity, however strong, only ever produces the candidate that lands on this
 * screen — §7.32 is explicit that no automatic reunification may occur from a face match.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MatchReviewViewModel @Inject constructor(
    private val lostFoundRepository: LostFoundRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchReviewUiState())
    val uiState: StateFlow<MatchReviewUiState> = _uiState.asStateFlow()

    /**
     * Candidates with both reports resolved.
     *
     * A candidate whose counterpart has not reached this device yet is still shown, with
     * whichever side is available — hiding it would silently drop a pending reunification
     * because of a sync gap.
     */
    val candidates: StateFlow<List<MatchCandidate>> =
        lostFoundRepository.observeCandidateMatches()
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
