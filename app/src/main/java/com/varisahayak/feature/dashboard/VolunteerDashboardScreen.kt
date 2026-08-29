package com.varisahayak.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.GlassSurface
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.OfflineQueuePill
import com.varisahayak.core.designsystem.component.OperationalCard
import com.varisahayak.core.designsystem.component.SosButton
import com.varisahayak.core.utils.rememberNowMillis

/**
 * The volunteer's home surface.
 *
 * Laid out for one hand and one thumb. The SOS control is anchored to the bottom of the
 * screen in a fixed drawer rather than scrolling with the feed, because the one action
 * that must never require hunting is the one you take when something has gone wrong. The
 * feed scrolls underneath it.
 */
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
    val nowMillis by rememberNowMillis()

    if (uiState.isLoading && uiState.openIncidents.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    if (showSosDialog) {
        SosConfirmationDialog(
            onConfirm = {
                showSosDialog = false
                viewModel.raiseEmergencySos()
            },
            onDismiss = { showSosDialog = false },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = Dimens.SpaceSm,
                // Clears the anchored SOS drawer so the last card is never trapped behind it.
                bottom = SOS_DRAWER_CLEARANCE,
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
                SectionHeading(text = stringResource(R.string.dashboard_quick_actions))
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
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
            }

            item {
                SectionHeading(text = stringResource(R.string.dashboard_open_incidents))
            }

            if (uiState.openIncidents.isEmpty()) {
                item {
                    EmptyState(message = stringResource(R.string.state_empty))
                }
            } else {
                items(items = uiState.openIncidents, key = { it.clientId }) { incident ->
                    IncidentCard(
                        incident = incident,
                        nowMillis = nowMillis,
                        onClick = { onNavigateToDetail(incident.clientId) },
                    )
                }
            }
        }

        // --- bottom-anchored action drawer ---
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Dimens.FloatingInset),
        ) {
            Column(modifier = Modifier.padding(Dimens.SpaceSm)) {
                SosButton(
                    text = stringResource(R.string.dashboard_raise_sos),
                    onClick = { showSosDialog = true },
                )
            }
        }
    }
}

@Composable
private fun SosConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VariTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.sos_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.sos_confirm_message),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_confirm),
                    color = colors.critical,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        containerColor = colors.cardSurface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
    )
}

@Composable
private fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = VariTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    OperationalCard(
        modifier = modifier.height(QUICK_ACTION_HEIGHT),
        onClick = onClick,
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
                tint = colors.brandAccent,
                modifier = Modifier.size(Dimens.IconLg),
            )
            Spacer(Modifier.height(Dimens.SpaceXs))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val QUICK_ACTION_HEIGHT = 96.dp

/** Height of the anchored SOS drawer plus its inset, reserved at the foot of the feed. */
private val SOS_DRAWER_CLEARANCE = 128.dp
