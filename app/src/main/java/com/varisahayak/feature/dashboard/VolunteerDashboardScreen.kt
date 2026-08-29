package com.varisahayak.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.OfflineBanner
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.SosButton
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.domain.model.Incident

@Composable
fun VolunteerDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToReport: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToLostFound: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSosDialog by remember { mutableStateOf(false) }

    if (uiState.isLoading && uiState.openIncidents.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = { Text(text = stringResource(R.string.sos_confirm_title)) },
            text = { Text(text = stringResource(R.string.sos_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSosDialog = false
                        viewModel.raiseEmergencySos()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = VariTheme.colors.critical,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        item {
            Spacer(modifier = Modifier.height(Dimens.SpaceSm))
            
            // EMERGENCY SOS BUTTON
            SosButton(
                text = stringResource(R.string.dashboard_raise_sos),
                onClick = { showSosDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (uiState.unsyncedCount > 0) {
            item {
                OfflineBanner(
                    detail = stringResource(R.string.sync_unsynced_count, uiState.unsyncedCount)
                )
            }
        }

        // Quick Actions Grid
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Dimens.SpaceSm),
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                QuickActionCard(
                    title = stringResource(R.string.dashboard_report_incident),
                    icon = Icons.Filled.ReportProblem,
                    onClick = onNavigateToReport,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    title = stringResource(R.string.nav_scan),
                    icon = Icons.Filled.QrCodeScanner,
                    onClick = onNavigateToScan,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                QuickActionCard(
                    title = stringResource(R.string.nav_map),
                    icon = Icons.Filled.Map,
                    onClick = onNavigateToMap,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    title = stringResource(R.string.lostfound_title),
                    icon = Icons.Filled.FindInPage,
                    onClick = onNavigateToLostFound,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Open Incidents Feed Section
        item {
            Text(
                text = "Open Incidents & Feed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Dimens.SpaceMd),
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
                VolunteerIncidentCard(
                    incident = incident,
                    onClick = { onNavigateToDetail(incident.clientId) },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.SpaceLg))
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceXs))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VolunteerIncidentCard(
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
