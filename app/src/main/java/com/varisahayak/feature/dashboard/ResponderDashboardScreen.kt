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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentStatus

@Composable
fun ResponderDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading && uiState.assignedIncidents.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        item {
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
            Text(
                text = stringResource(R.string.dashboard_active_assignment),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (uiState.assignedIncidents.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.dashboard_no_active_assignment))
            }
        } else {
            items(
                items = uiState.assignedIncidents,
                key = { it.clientId },
            ) { incident ->
                ResponderIncidentCard(
                    incident = incident,
                    onStatusChange = { newStatus ->
                        viewModel.updateStatus(incident.clientId, newStatus)
                    },
                    onClick = { onNavigateToDetail(incident.clientId) },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.SpaceMd))
            Text(
                text = "All Open Route Incidents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        items(
            items = uiState.openIncidents,
            key = { "open-${it.clientId}" },
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
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.SpaceLg))
        }
    }
}

@Composable
private fun ResponderIncidentCard(
    incident: Incident,
    onStatusChange: (IncidentStatus) -> Unit,
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
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                when (incident.status) {
                    IncidentStatus.ASSIGNED -> {
                        Button(
                            onClick = { onStatusChange(IncidentStatus.ACCEPTED) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.incident_accept))
                        }
                    }
                    IncidentStatus.ACCEPTED -> {
                        Button(
                            onClick = { onStatusChange(IncidentStatus.IN_PROGRESS) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.incident_start))
                        }
                    }
                    IncidentStatus.IN_PROGRESS -> {
                        Button(
                            onClick = { onStatusChange(IncidentStatus.RESOLVED) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.incident_resolve))
                        }
                    }
                    else -> {}
                }

                if (incident.status != IncidentStatus.RESOLVED) {
                    OutlinedButton(
                        onClick = { onStatusChange(IncidentStatus.ESCALATED) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.incident_escalate))
                    }
                }
            }
        }
    }
}
