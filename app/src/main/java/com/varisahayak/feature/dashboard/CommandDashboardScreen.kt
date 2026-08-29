package com.varisahayak.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.AccentTone
import com.varisahayak.core.designsystem.Accents
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.accentTone
import com.varisahayak.core.designsystem.component.ChartSeries
import com.varisahayak.core.designsystem.component.DonutChart
import com.varisahayak.core.designsystem.component.DonutLegend
import com.varisahayak.core.designsystem.component.DonutSlice
import com.varisahayak.core.designsystem.component.IconPlate
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.ShimmerLoadingState
import com.varisahayak.core.designsystem.component.NotConnectedPanel
import com.varisahayak.core.designsystem.component.OperationalCard
import com.varisahayak.core.designsystem.component.SectionHeader
import com.varisahayak.core.designsystem.component.StatTile
import com.varisahayak.core.designsystem.component.TrendLineChart
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.utils.rememberNowMillis
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.usecase.HotspotCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The organiser and administrator overview.
 *
 * Four headline counts, a week of trend, the category mix, then what actually needs
 * attention. Every figure is computed from the incidents this device holds, so the numbers
 * and the queue beneath them can never disagree.
 *
 * Two things the mockup shows are deliberately absent. There is no "vs yesterday" delta,
 * because nothing snapshots yesterday's totals — a delta would be arithmetic on data that
 * does not exist. And there is no service-health board, because nothing here health-checks
 * a server, a database or an SMS gateway; a row of green "Operational" badges that check
 * nothing is the most dangerous widget a command screen could carry.
 */
@Composable
fun CommandDashboardScreen(
    viewModel: DashboardViewModel,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
    walkieChannelName: String? = null,
    walkieVisible: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    val nowMillis by rememberNowMillis()
    var showSosDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshMyLocation() }

    if (uiState.isLoading && uiState.profile == null) {
        ShimmerLoadingState(modifier = modifier)
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

    val open = uiState.openIncidents
    val all = uiState.allIncidents

    val clusterer = remember { HotspotCalculator() }
    val hotspotCount = remember(open) { clusterer.cluster(open).size }
    val sosToday = remember(all, nowMillis) { DashboardMetrics.sosToday(all, nowMillis) }

    // Triage order for the queue: worst first, then oldest. An unattended critical from
    // twenty minutes ago outranks one raised thirty seconds ago, and command is the only
    // view where that ordering is the whole job.
    val triaged = remember(open) {
        open.sortedWith(
            compareByDescending<Incident> { it.isSos }
                .thenByDescending { it.priority.rank }
                .thenBy { it.reportedAtEpochMillis },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenPadding,
            end = Dimens.ScreenPadding,
            top = Dimens.SpaceSm,
            bottom = Dimens.SpaceXl,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
    ) {
        dashboardHeaderItems(
            uiState = uiState,
            actions = actions.copy(onSos = { showSosDialog = true }),
            walkieChannelName = walkieChannelName,
            walkieVisible = walkieVisible,
            onRetrySync = viewModel::retrySync,
        )

        item(key = "headline-stats") {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    StatTile(
                        label = stringResource(R.string.dashboard_open_incidents),
                        value = open.size.toString(),
                        icon = Icons.Filled.ReportProblem,
                        tone = Accents.red,
                        modifier = Modifier.weight(1f),
                        onClick = actions.onMap,
                    )
                    StatTile(
                        label = stringResource(R.string.admin_active_responders),
                        value = uiState.nearbyResponders.size.toString(),
                        icon = Icons.Filled.Groups,
                        tone = Accents.green,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    StatTile(
                        label = stringResource(R.string.admin_sos_today),
                        value = sosToday.toString(),
                        icon = Icons.Filled.Campaign,
                        tone = Accents.amber,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.command_hotspots),
                        value = hotspotCount.toString(),
                        icon = Icons.Filled.LocationOn,
                        tone = Accents.purple,
                        modifier = Modifier.weight(1f),
                        caption = stringResource(R.string.dashboard_view_map),
                        captionColor = VariTheme.colors.info,
                        onClick = actions.onMap,
                    )
                }
            }
        }

        item(key = "trend") {
            IncidentTrendSection(incidents = all, nowMillis = nowMillis)
        }

        item(key = "mix") {
            IncidentMixSection(incidents = open)
        }

        item(key = "alerts") {
            OperationalAlertsSection(uiState = uiState, incidents = all)
        }

        item(key = "queue-header") {
            SectionHeader(
                title = stringResource(R.string.command_open_incidents),
                actionLabel = stringResource(R.string.dashboard_view_map),
                onAction = actions.onMap,
            )
        }

        if (triaged.isEmpty()) {
            item(key = "queue-empty") {
                OperationalCard {
                    NotConnectedPanel(message = stringResource(R.string.map_no_incidents))
                }
            }
        } else {
            items(
                items = triaged,
                key = { it.clientId },
            ) { incident ->
                IncidentCard(
                    incident = incident,
                    nowMillis = nowMillis,
                    myLocation = uiState.myLocation,
                    assigneeInitials = incident.assigneeId?.take(2),
                    onClick = { actions.onDetail(incident.clientId) },
                )
            }
        }
    }
}

/**
 * Seven days of incidents, one line per category.
 *
 * Categories with nothing in the window are dropped rather than drawn flat at zero: a
 * legend entry for a line that is not there costs attention and gives nothing back.
 */
@Composable
private fun IncidentTrendSection(
    incidents: List<Incident>,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val days = 7
    val trend = remember(incidents, nowMillis) {
        DashboardMetrics.dailyCategoryTrend(incidents, nowMillis, days)
    }
    val bucketStarts = remember(nowMillis) {
        DashboardMetrics.trendBucketStarts(nowMillis, days)
    }

    val dayFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val labels = remember(bucketStarts) { bucketStarts.map { dayFormat.format(Date(it)) } }

    val series = trend.map { (category, points) ->
        ChartSeries(
            label = stringResource(category.labelRes()),
            color = category.accentTone().accent,
            points = points,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        SectionHeader(
            title = stringResource(R.string.admin_incidents_overview),
            actionLabel = stringResource(R.string.admin_last_days, days),
        )

        OperationalCard {
            Column(modifier = Modifier.padding(Dimens.SpaceMd)) {
                TrendLineChart(
                    series = series,
                    xLabels = labels,
                    emptyMessage = stringResource(R.string.not_connected_chart),
                )
            }
        }
    }
}

/** What the open load is made of. */
@Composable
private fun IncidentMixSection(
    incidents: List<Incident>,
    modifier: Modifier = Modifier,
) {
    val breakdown = remember(incidents) { DashboardMetrics.categoryBreakdown(incidents) }
    val slices = breakdown.map { (category, count) ->
        DonutSlice(
            label = stringResource(category.labelRes()),
            value = count,
            color = category.accentTone().accent,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        SectionHeader(title = stringResource(R.string.admin_incident_mix))

        OperationalCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                DonutChart(
                    slices = slices,
                    centreValue = slices.sumOf { it.value }.toString(),
                    centreLabel = stringResource(R.string.admin_total),
                    emptyMessage = stringResource(R.string.not_connected_breakdown),
                )
                DonutLegend(slices = slices, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Things that are wrong right now.
 *
 * Every alert here is a real condition computed from real records — failed uploads,
 * unclaimed work, open criticals — not a service-status poll. When none of them fire the
 * section says so, because "all clear" is information and an empty panel is not.
 */
@Composable
private fun OperationalAlertsSection(
    uiState: DashboardUiState,
    incidents: List<Incident>,
    modifier: Modifier = Modifier,
) {
    val failed = remember(incidents) { DashboardMetrics.failedSyncCount(incidents) }
    val unassigned = remember(uiState.openIncidents) {
        DashboardMetrics.unassignedCount(uiState.openIncidents)
    }
    val critical = remember(uiState.openIncidents) {
        DashboardMetrics.criticalCount(uiState.openIncidents)
    }

    val alerts = buildList {
        if (critical > 0) {
            add(
                AlertRow(
                    title = stringResource(R.string.admin_alert_critical, critical),
                    detail = stringResource(R.string.admin_alert_critical_detail),
                    icon = Icons.Filled.Campaign,
                    tone = Accents.red,
                ),
            )
        }
        if (unassigned > 0) {
            add(
                AlertRow(
                    title = stringResource(R.string.admin_alert_unassigned, unassigned),
                    detail = stringResource(R.string.admin_alert_unassigned_detail),
                    icon = Icons.Filled.PersonSearch,
                    tone = Accents.amber,
                ),
            )
        }
        if (failed > 0) {
            add(
                AlertRow(
                    title = stringResource(R.string.admin_alert_failed_sync, failed),
                    detail = stringResource(R.string.admin_alert_failed_sync_detail),
                    icon = Icons.Filled.CloudOff,
                    tone = Accents.blue,
                ),
            )
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        SectionHeader(title = stringResource(R.string.admin_operational_alerts))

        OperationalCard {
            Column(modifier = Modifier.padding(vertical = Dimens.SpaceXs)) {
                if (alerts.isEmpty()) {
                    AlertRowView(
                        alert = AlertRow(
                            title = stringResource(R.string.admin_all_clear),
                            detail = null,
                            icon = Icons.Filled.TaskAlt,
                            tone = Accents.green,
                        ),
                    )
                } else {
                    alerts.forEach { AlertRowView(alert = it) }
                }
            }
        }
    }
}

private data class AlertRow(
    val title: String,
    val detail: String?,
    val icon: ImageVector,
    val tone: AccentTone,
)

@Composable
private fun AlertRowView(
    alert: AlertRow,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        IconPlate(icon = alert.icon, tone = alert.tone, size = 36.dp, iconSize = Dimens.IconSm)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (alert.detail != null) {
                Text(
                    text = alert.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

