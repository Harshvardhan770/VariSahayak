package com.varisahayak.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.domain.model.Incident

@Composable
fun CommandDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading && uiState.openIncidents.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    val criticalCount = uiState.openIncidents.count { it.priority == com.varisahayak.domain.model.IncidentPriority.CRITICAL || it.isSos }
    val openCount = uiState.openIncidents.size
    val assignedCount = uiState.openIncidents.count { it.assigneeId != null }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        item {
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
            Text(
                text = stringResource(R.string.command_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Summary Stats Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                StatCard(
                    label = stringResource(R.string.command_open_incidents),
                    value = openCount.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.dashboard_sos_section),
                    value = criticalCount.toString(),
                    color = VariTheme.colors.critical,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                StatCard(
                    label = stringResource(R.string.command_assigned_incidents),
                    value = assignedCount.toString(),
                    color = VariTheme.colors.info,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Unsynced",
                    value = uiState.unsyncedCount.toString(),
                    color = if (uiState.unsyncedCount == 0) VariTheme.colors.success else VariTheme.colors.warning,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Text(
                text = "Live Incident Operations Feed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Dimens.SpaceSm),
            )
        }

        if (uiState.openIncidents.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.state_empty))
            }
        } else {
            items(
                items = uiState.openIncidents,
                key = { it.clientId },
            ) { incident ->
                Card(
                    onClick = { onNavigateToDetail(incident.clientId) },
                    shape = RoundedCornerShape(Dimens.CornerMd),
                    modifier = Modifier.fillMaxWidth(),
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
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.SpaceLg))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(Dimens.CornerMd),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.height(90.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.SpaceSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
