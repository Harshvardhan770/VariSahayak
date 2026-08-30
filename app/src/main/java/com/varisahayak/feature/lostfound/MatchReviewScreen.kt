package com.varisahayak.feature.lostfound

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.EmptyState

/**
 * Candidate review.
 *
 * The screen is built around one rule from §7.30: never present an unexplained score. A
 * volunteer deciding whether to walk two route points to check on a child needs to know
 * *why* the system thinks it is worth walking, so every candidate shows its reasoning —
 * including what it could not compare.
 */
@Composable
fun MatchReviewScreen(
    modifier: Modifier = Modifier,
    viewModel: MatchReviewViewModel = hiltViewModel(),
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    if (candidates.isEmpty()) {
        EmptyState(message = stringResource(R.string.match_no_candidates), modifier = modifier)
        return
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 20 }
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            item {
                // Stated up front, not buried under the buttons. The system proposes; a person
                // decides.
                Text(
                    text = stringResource(R.string.match_human_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(candidates, key = { it.match.clientId }) { candidate ->
                CandidateCard(
                    candidate = candidate,
                    enabled = !uiState.isReviewing,
                    onConfirm = { viewModel.confirm(candidate.match.clientId) },
                    onReject = { viewModel.reject(candidate.match.clientId) },
                )
            }
        }
    }
}
