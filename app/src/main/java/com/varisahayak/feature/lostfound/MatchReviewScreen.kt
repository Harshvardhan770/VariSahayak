package com.varisahayak.feature.lostfound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.domain.model.LostFoundReport
import com.varisahayak.domain.model.MatchConfidence
import com.varisahayak.domain.model.MatchSignal
import com.varisahayak.domain.model.SignalStrength

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

    if (candidates.isEmpty()) {
        EmptyState(message = stringResource(R.string.match_no_candidates), modifier = modifier)
        return
    }

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

@Composable
private fun CandidateCard(
    candidate: MatchCandidate,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    val score = candidate.match.score

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            // Confidence as a word plus the percentage. Never a colour on its own.
            Text(
                text = stringResource(
                    when (score.confidence) {
                        MatchConfidence.HIGH -> R.string.match_confidence_high
                        MatchConfidence.MEDIUM -> R.string.match_confidence_medium
                        MatchConfidence.LOW -> R.string.match_confidence_low
                    },
                ) + " · ${score.percent}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                SidePanel(
                    labelRes = R.string.match_lost_side,
                    report = candidate.lost,
                    modifier = Modifier.weight(1f),
                )
                SidePanel(
                    labelRes = R.string.match_found_side,
                    report = candidate.found,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.match_why),
                style = MaterialTheme.typography.titleSmall,
            )

            // Everything the engine looked at, including what it could not compare. A
            // volunteer needs to know a photo was missing, not merely that the score is
            // lower than they expected.
            score.signals.forEach { signal -> SignalRow(signal) }

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                VariPrimaryButton(
                    text = stringResource(R.string.match_confirm),
                    onClick = onConfirm,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.match_reject))
                }
            }
        }
    }
}

@Composable
private fun SidePanel(labelRes: Int, report: LostFoundReport?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (report == null) {
            // The counterpart has not reached this device yet. Say so rather than showing
            // a blank column that reads as "no information recorded".
            Text(
                text = stringResource(R.string.state_loading),
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        Text(text = report.title, style = MaterialTheme.typography.bodyMedium)

        listOfNotNull(
            report.personName,
            report.approximateAge?.let { stringResource(R.string.lostfound_age_approx, it) },
            report.clothingDescription,
            report.language,
            report.qrLocationName,
            report.custodianName?.let { stringResource(R.string.lostfound_with_custodian, it) },
        ).forEach { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignalRow(signal: MatchSignal) {
    // A leading glyph plus the sentence. The glyph is redundant with the words on purpose
    // — it is a scanning aid, never the only carrier of meaning.
    val marker = when (signal.strength) {
        SignalStrength.SUPPORTS -> "✓"
        SignalStrength.NEUTRAL -> "·"
        SignalStrength.CONTRADICTS -> "✗"
        SignalStrength.NO_SIGNAL -> "⚠"
    }

    Text(
        text = "$marker ${signal.explanation}",
        style = MaterialTheme.typography.bodySmall,
        color = when (signal.strength) {
            SignalStrength.CONTRADICTS -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
    )
}
