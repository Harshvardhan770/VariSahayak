package com.varisahayak.feature.incidents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.OfflineBanner
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.domain.model.Incident

@Composable
fun IncidentListScreen(
    onIncidentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.isOffline) {
            OfflineBanner()
        }

        // Always visible when non-zero, online or not. A volunteer must be able to see at
        // a glance that something has not reached the server yet.
        if (uiState.unsyncedCount > 0) {
            UnsyncedBanner(count = uiState.unsyncedCount, onRetry = viewModel::retrySync)
        }

        when {
            uiState.isLoading -> LoadingState()
            uiState.incidents.isEmpty() -> EmptyState()
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    Dimens.ScreenPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                items(uiState.incidents, key = { it.clientId }) { incident ->
                    IncidentRow(
                        incident = incident,
                        onClick = { onIncidentSelected(incident.clientId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnsyncedBanner(count: Int, onRetry: () -> Unit) {
    val colors = VariTheme.colors
    Surface(
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sync_unsynced_count, count),
                style = MaterialTheme.typography.bodyMedium,
            )
            VariSecondaryButton(
                text = stringResource(R.string.sync_retry_now),
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun IncidentRow(incident: Incident, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.MinTouchTarget)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(
                text = stringResource(incident.category.labelRes()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = incident.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                PriorityBadge(priority = incident.priority)
                StatusChip(status = incident.status)
                // Shown alongside status, not instead of it: an incident can be in
                // progress and still unsynced, and both facts matter.
                if (incident.needsSync) {
                    SyncBadge(syncState = incident.syncState)
                }
            }
        }
    }
}
