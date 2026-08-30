package com.varisahayak.feature.incidents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.common.AppError
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.ShimmerLoadingState
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.utils.ExternalNavigation
import com.varisahayak.domain.model.IncidentStatus

@Composable
fun IncidentDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: IncidentDetailViewModel = hiltViewModel(),
) {
    val incident by viewModel.incident.collectAsStateWithLifecycle()
    val actions by viewModel.availableActions.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val current = incident
    if (current == null) {
        ShimmerLoadingState(modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Text(
            text = stringResource(current.category.labelRes()),
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            PriorityBadge(priority = current.priority)
            StatusChip(status = current.status)
            SyncBadge(syncState = current.syncState)
        }

        Text(text = current.description, style = MaterialTheme.typography.bodyLarge)

        current.affectedPersonNote?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The navigate action only appears when there is somewhere to navigate to. An
        // incident reported without a location is normal, not an error.
        current.location?.let { location ->
            VariSecondaryButton(
                text = stringResource(R.string.incident_navigate),
                onClick = {
                    val launched = ExternalNavigation.navigateTo(context, location)
                    if (!launched) {
                        // Nothing on the device can handle it; say so rather than
                        // appearing to do nothing.
                        viewModel.dismissError()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        uiState.error?.let { error ->
            Text(
                text = (error as? AppError.Validation)?.message
                    ?: stringResource(R.string.state_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Command and admin only. A volunteer looking at their own report has no use for
        // match scores and responder workloads, and canSeeAreaWideIncidents is the existing
        // capability that already means "this user runs operations". RLS enforces the same
        // boundary server-side: incident_events admits command users and participants only.
        if (capabilities.canSeeAreaWideIncidents) {
            IncidentTimelineSection(
                incident = current,
                events = timeline,
                metrics = metrics,
            )
        }

        if (actions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.incident_detail_title),
                style = MaterialTheme.typography.titleMedium,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                actions.forEach { status ->
                    VariPrimaryButton(
                        text = stringResource(status.actionLabelRes()),
                        onClick = { viewModel.updateStatus(status) },
                        enabled = !uiState.isUpdating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Action wording, which differs from the status name: the button says "Accept", the badge
 * says "Accepted".
 */
private fun IncidentStatus.actionLabelRes(): Int = when (this) {
    IncidentStatus.ACCEPTED -> R.string.incident_accept
    IncidentStatus.IN_PROGRESS -> R.string.incident_start
    IncidentStatus.RESOLVED -> R.string.incident_resolve
    IncidentStatus.ESCALATED -> R.string.incident_escalate
    IncidentStatus.REASSIGNMENT_REQUIRED -> R.string.incident_reject
    IncidentStatus.CANCELLED -> R.string.action_cancel
    else -> labelRes()
}
