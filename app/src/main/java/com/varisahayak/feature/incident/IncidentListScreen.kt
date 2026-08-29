package com.varisahayak.feature.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory

@Composable
fun IncidentListScreen(
    viewModel: IncidentListViewModel,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading && uiState.incidents.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding),
    ) {
        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        Text(
            text = stringResource(R.string.nav_incidents),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("All") },
                )
            }
            items(IncidentCategory.entries) { cat ->
                FilterChip(
                    selected = uiState.selectedCategory == cat,
                    onClick = { viewModel.selectCategory(cat) },
                    label = { Text(stringResource(cat.labelRes())) },
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        if (uiState.incidents.isEmpty()) {
            EmptyState(message = stringResource(R.string.state_empty))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = uiState.incidents,
                    key = { it.clientId },
                ) { incident ->
                    IncidentListItemCard(
                        incident = incident,
                        onClick = { onNavigateToDetail(incident.clientId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IncidentListItemCard(
    incident: Incident,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CornerMd),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriorityBadge(priority = incident.priority)
                StatusChip(status = incident.status)
            }

            Text(
                text = incident.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyncBadge(syncState = incident.syncState)
                Text(
                    text = stringResource(incident.category.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
