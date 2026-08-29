package com.varisahayak.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.OfflineQueuePill
import com.varisahayak.core.designsystem.component.OperationalCard
import com.varisahayak.core.utils.rememberNowMillis
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentPriority

/**
 * The organiser's overview.
 *
 * Four numbers and a triage-ordered queue. Deliberately not a chart dashboard: at the scale
 * this operates — tens of open incidents, not thousands — a sparkline tells a coordinator
 * nothing a count does not, and costs the screen space the queue needs.
 *
 * The counts are all derived from the same incident list the queue renders, so they can
 * never disagree with it.
 */
@Composable
fun CommandDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val nowMillis by rememberNowMillis()

    if (uiState.isLoading && uiState.openIncidents.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    val incidents = uiState.openIncidents

    val criticalCount = remember(incidents) {
        incidents.count { it.priority == IncidentPriority.CRITICAL || it.isSos }
    }
    val unassignedCount = remember(incidents) { incidents.count { it.assigneeId == null } }

    // Worst first, then oldest. An unattended critical from twenty minutes ago outranks one
    // raised thirty seconds ago, and command is the only view where that ordering is the
    // whole job.
    val triaged = remember(incidents) {
        incidents.sortedWith(
            compareByDescending<Incident> { it.isSos }
                .thenByDescending { it.priority.rank }
                .thenBy { it.reportedAtEpochMillis },
        )
    }

    val colors = VariTheme.colors

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
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    StatCard(
                        label = stringResource(R.string.command_escalations),
                        value = criticalCount.toString(),
                        // A zero here is good news and should read as calm, not as an alarm
                        // that happens to say nothing is wrong.
                        valueColor = if (criticalCount == 0) colors.textPrimary else colors.critical,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.command_open_incidents),
                        value = incidents.size.toString(),
                        valueColor = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    StatCard(
                        label = stringResource(R.string.command_assigned_incidents),
                        value = unassignedCount.toString(),
                        valueColor = if (unassignedCount == 0) colors.success else colors.warning,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.sync_pending),
                        value = uiState.unsyncedCount.toString(),
                        valueColor = if (uiState.unsyncedCount == 0) colors.success else colors.warning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.command_open_incidents),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
        }

        if (triaged.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.state_empty))
            }
        } else {
            items(items = triaged, key = { it.clientId }) { incident ->
                IncidentCard(
                    incident = incident,
                    nowMillis = nowMillis,
                    onClick = { onNavigateToDetail(incident.clientId) },
                    assigneeInitials = incident.assigneeId?.take(2),
                )
            }
        }
    }
}

/**
 * One number and its label.
 *
 * The number is set at display size because it is read across a table in a control tent,
 * not at arm's length. The label sits underneath in muted text — it is the part you only
 * need once, to learn what the number means.
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    OperationalCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = valueColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = VariTheme.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
