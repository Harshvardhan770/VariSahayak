package com.varisahayak.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.OfflineBanner
import com.varisahayak.core.designsystem.component.PriorityBadge
import com.varisahayak.core.designsystem.component.SosButton
import com.varisahayak.core.designsystem.component.StatusChip
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.domain.model.Incident

/**
 * The volunteer's home surface.
 *
 * Ordered by the product's priority list rather than by convenience: critical alerts,
 * then the active assignment, then reporting. The SOS control is the largest element on
 * the screen and reachable in one tap plus a confirmation — raising an emergency must
 * never mean hunting for a small target while walking in a crowd.
 */
@Composable
fun VolunteerDashboardScreen(
    onReportIncident: () -> Unit,
    onRaiseSos: () -> Unit,
    onScanQr: () -> Unit,
    onIncidentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VolunteerDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmSos by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        if (uiState.isOffline) {
            OfflineBanner()
        }

        Column(
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            uiState.profile?.let { profile ->
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(profile.role.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 1. Critical alerts, above everything else.
            if (uiState.activeSos.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_sos_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = VariTheme.colors.critical,
                )
                uiState.activeSos.forEach { incident ->
                    IncidentSummaryCard(incident) { onIncidentSelected(incident.clientId) }
                }
            }

            // 2. Current assignment.
            Text(
                text = stringResource(R.string.dashboard_active_assignment),
                style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.assigned.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_no_active_assignment),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.assigned.forEach { incident ->
                    IncidentSummaryCard(incident) { onIncidentSelected(incident.clientId) }
                }
            }

            // 3. Reporting.
            VariPrimaryButton(
                text = stringResource(R.string.dashboard_report_incident),
                onClick = onReportIncident,
                icon = Icons.Filled.Add,
            )

            SosButton(
                text = stringResource(R.string.dashboard_raise_sos),
                onClick = { confirmSos = true },
            )

            VariSecondaryButton(
                text = stringResource(R.string.sos_bridge_subtitle),
                onClick = onScanQr,
                icon = Icons.Filled.QrCodeScanner,
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.unsyncedCount > 0) {
                Surface(
                    color = VariTheme.colors.warningContainer,
                    contentColor = VariTheme.colors.onWarningContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.sync_unsynced_count, uiState.unsyncedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Dimens.SpaceMd),
                    )
                }
            }

            VariSecondaryButton(
                text = stringResource(R.string.auth_sign_out),
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Confirmation, because a pocket-press must not scramble a medical team. One extra
    // tap is the whole safeguard — no multi-screen flow.
    if (confirmSos) {
        AlertDialog(
            onDismissRequest = { confirmSos = false },
            title = { Text(stringResource(R.string.sos_confirm_title)) },
            text = { Text(stringResource(R.string.sos_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSos = false
                        onRaiseSos()
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSos = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun IncidentSummaryCard(incident: Incident, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                PriorityBadge(priority = incident.priority)
                StatusChip(status = incident.status)
            }
        }
    }
}
