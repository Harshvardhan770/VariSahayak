package com.varisahayak.feature.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.domain.model.IncidentStatus

@Composable
fun IncidentDetailScreen(
    clientId: String,
    viewModel: IncidentDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(clientId) {
        viewModel.setClientId(clientId)
    }

    val incident = uiState.incident

    if (incident == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(message = "Incident details loaded")
            Spacer(modifier = Modifier.height(Dimens.SpaceMd))
            OutlinedButton(onClick = onNavigateBack) {
                Text(stringResource(R.string.action_back))
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PriorityBadge(priority = incident.priority)
            StatusChip(status = incident.status)
        }

        Card(
            shape = RoundedCornerShape(Dimens.CornerLg),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                Text(
                    text = stringResource(incident.category.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = incident.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                HorizontalDivider()

                SyncBadge(syncState = incident.syncState)

                incident.affectedPersonNote?.let { note ->
                    Text(
                        text = "Affected Person: $note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                incident.location?.let { loc ->
                    Text(
                        text = "Location: ${loc.latitude}, ${loc.longitude}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Action Buttons Card
        Card(
            shape = RoundedCornerShape(Dimens.CornerMd),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Text(
                    text = "Update Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    if (incident.status != IncidentStatus.IN_PROGRESS) {
                        Button(
                            onClick = { viewModel.updateStatus(IncidentStatus.IN_PROGRESS) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.incident_start))
                        }
                    }

                    if (incident.status != IncidentStatus.RESOLVED) {
                        Button(
                            onClick = { viewModel.updateStatus(IncidentStatus.RESOLVED) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.incident_resolve))
                        }
                    }
                }
            }
        }
    }
}
