package com.varisahayak.feature.incidents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.ShimmerLoadingState
import com.varisahayak.core.designsystem.component.OfflineQueuePill
import com.varisahayak.core.utils.rememberNowMillis

/**
 * Every incident this device knows about.
 *
 * Reads from Room, never from the network — which is why it renders instantly on a cold
 * start in a dead spot, and why it looks identical online and off. The only thing
 * connectivity changes here is the queue pill.
 */
@Composable
fun IncidentListScreen(
    onIncidentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nowMillis by rememberNowMillis()

    Column(modifier = modifier.fillMaxSize()) {
        // Always visible when non-zero, online or not. A volunteer must be able to see at
        // a glance that something has not reached the server yet — and tapping it forces a
        // sync attempt rather than making them wait for the next scheduled window.
        if (uiState.unsyncedCount > 0) {
            OfflineQueuePill(
                unsyncedCount = uiState.unsyncedCount,
                isOnline = !uiState.isOffline,
                onRetry = viewModel::retrySync,
                modifier = Modifier.padding(
                    horizontal = Dimens.ScreenPadding,
                    vertical = Dimens.SpaceXs,
                ),
            )
        }

        when {
            uiState.isLoading -> ShimmerLoadingState()

            uiState.incidents.isEmpty() -> EmptyState()

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.ScreenPadding,
                    end = Dimens.ScreenPadding,
                    top = Dimens.SpaceSm,
                    bottom = Dimens.SpaceXl,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                items(items = uiState.incidents, key = { it.clientId }) { incident ->
                    IncidentCard(
                        incident = incident,
                        nowMillis = nowMillis,
                        onClick = { onIncidentSelected(incident.clientId) },
                    )
                }
            }
        }
    }
}
