package com.varisahayak.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.OfflineQueuePill
import com.varisahayak.core.utils.rememberNowMillis
import com.varisahayak.domain.model.IncidentStatus

/**
 * The responder's work queue.
 *
 * Two sections, in the only order that makes sense: what is already yours, then what is
 * available. Each assigned card carries exactly one forward action, derived from the
 * incident's current state — a responder should never have to decide which of four buttons
 * advances the job.
 */
@Composable
fun ResponderDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val nowMillis by rememberNowMillis()

    if (uiState.isLoading && uiState.assignedIncidents.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    // Anything already mine drops out of the "available" list. Showing a job in both places
    // is how two responders end up driving to the same incident.
    val assignedIds = uiState.assignedIncidents.mapTo(HashSet()) { it.clientId }
    val available = uiState.openIncidents.filter { it.clientId !in assignedIds }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenPadding,
            end = Dimens.ScreenPadding,
            top = Dimens.SpaceSm,
            bottom = Dimens.SpaceXl,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        if (uiState.unsyncedCount > 0) {
            item {
                OfflineQueuePill(
                    unsyncedCount = uiState.unsyncedCount,
                    isOnline = !uiState.isOffline,
                    onRetry = viewModel::retrySync,
                )
            }
        }

        item {
            SectionHeading(stringResource(R.string.dashboard_active_assignment))
        }

        if (uiState.assignedIncidents.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.dashboard_no_active_assignment))
            }
        } else {
            items(items = uiState.assignedIncidents, key = { it.clientId }) { incident ->
                val next = incident.status.nextAction()
                IncidentCard(
                    incident = incident,
                    nowMillis = nowMillis,
                    onClick = { onNavigateToDetail(incident.clientId) },
                    actionLabel = next?.let { stringResource(it.labelRes) },
                    onAction = next?.let {
                        { viewModel.updateStatus(incident.clientId, it.target) }
                    },
                )
            }
        }

        item {
            SectionHeading(stringResource(R.string.dashboard_pending_assignments))
        }

        if (available.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.state_empty))
            }
        } else {
            items(items = available, key = { it.clientId }) { incident ->
                IncidentCard(
                    incident = incident,
                    nowMillis = nowMillis,
                    onClick = { onNavigateToDetail(incident.clientId) },
                    actionLabel = stringResource(R.string.incident_accept),
                    onAction = {
                        viewModel.updateStatus(incident.clientId, IncidentStatus.ACCEPTED)
                    },
                )
            }
        }
    }
}

/**
 * The single forward step from a given state.
 *
 * Mirrors the main line of [com.varisahayak.domain.model.IncidentStateMachine]; the state
 * machine itself remains the authority and rejects anything illegal, so a stale card cannot
 * push an incident somewhere it should not go.
 */
private data class NextAction(val labelRes: Int, val target: IncidentStatus)

private fun IncidentStatus.nextAction(): NextAction? = when (this) {
    IncidentStatus.ASSIGNED -> NextAction(R.string.incident_accept, IncidentStatus.ACCEPTED)
    IncidentStatus.ACCEPTED -> NextAction(R.string.incident_start, IncidentStatus.IN_PROGRESS)
    IncidentStatus.IN_PROGRESS -> NextAction(R.string.incident_resolve, IncidentStatus.RESOLVED)

    // Nothing to advance: either terminal, not yet triaged, or waiting on command.
    else -> null
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = VariTheme.colors.textSecondary,
    )
}
