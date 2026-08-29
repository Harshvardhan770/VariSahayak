package com.varisahayak.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.OfflineQueuePill
import com.varisahayak.core.utils.rememberNowMillis
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.ResponderAvailability

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
    val availability by viewModel.availability.collectAsState()
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

        // Availability gates dispatch entirely: the server-side matcher filters on it
        // before it scores anybody, so a responder who never sets this is never matched.
        // It sits above the assignment list because it is the thing that makes the rest
        // of the screen fill up.
        if (uiState.capabilities.canSetOwnAvailability) {
            item {
                AvailabilityControl(
                    current = availability,
                    onChange = viewModel::setAvailability,
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

/**
 * Shift state as three explicit choices rather than an on/off switch.
 *
 * BUSY and OFF_SHIFT both remove a responder from dispatch, but they mean different
 * things to an organiser reading the roster — mid-incident versus gone home — and
 * collapsing them into one toggle would throw that away.
 *
 * Each chip pairs its colour with an icon and a label: priority and status are never
 * communicated by colour alone anywhere in this app, and shift state is no exception.
 */
@Composable
private fun AvailabilityControl(
    current: ResponderAvailability?,
    onChange: (ResponderAvailability) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        AvailabilityOption.entries.forEach { option ->
            val selected = current == option.state
            FilterChip(
                selected = selected,
                onClick = { onChange(option.state) },
                label = { Text(stringResource(option.labelRes)) },
                leadingIcon = { Icon(option.icon, contentDescription = null) },
                shape = FilterChipDefaults.shape,
                modifier = Modifier
                    // 48dp minimum touch target: this is tapped with gloves on, in
                    // sunlight, while walking.
                    .defaultMinSize(minHeight = Dimens.MinTouchTarget)
                    .semantics {
                        stateDescription = if (selected) "Selected" else "Not selected"
                    },
            )
        }
    }
}

private enum class AvailabilityOption(
    val state: ResponderAvailability,
    val labelRes: Int,
    val icon: ImageVector,
) {
    AVAILABLE(ResponderAvailability.AVAILABLE, R.string.dashboard_available, Icons.Filled.Bolt),
    BUSY(ResponderAvailability.BUSY, R.string.dashboard_busy, Icons.Filled.DoNotDisturbOn),
    OFF_SHIFT(
        ResponderAvailability.OFF_SHIFT,
        R.string.dashboard_off_shift,
        Icons.Filled.NightsStay,
    ),
}
