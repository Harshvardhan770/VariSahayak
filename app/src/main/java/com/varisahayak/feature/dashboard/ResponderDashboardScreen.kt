package com.varisahayak.feature.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.varisahayak.R
import com.varisahayak.core.designsystem.Accents
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.accentTone
import com.varisahayak.core.designsystem.component.ActiveAssignmentCard
import com.varisahayak.core.designsystem.component.CompactStat
import com.varisahayak.core.designsystem.component.DonutChart
import com.varisahayak.core.designsystem.component.DonutLegend
import com.varisahayak.core.designsystem.component.DonutSlice
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.IncidentRow
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.NotConnectedPanel
import com.varisahayak.core.designsystem.component.OperationalCard
import com.varisahayak.core.designsystem.component.SectionHeader
import com.varisahayak.core.designsystem.component.StatStrip
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.utils.rememberNowMillis
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.ResponderAvailability
import com.varisahayak.domain.model.UserRole

/**
 * The responder dashboards.
 *
 * One screen with three faces. Medical, police and NGO responders share a route, a shift
 * and an incident stream; what differs is which slice of it is theirs. Splitting them into
 * three files would have duplicated the header, the shift control and the queue three
 * times and let them drift apart.
 *
 * Every counter is derived from incidents this device holds — see [DashboardMetrics].
 * Where the mockups show a figure with no source in this system (supply stock levels,
 * ambulance fleet state, checkpoint counts), the panel says so rather than inventing one.
 */
@Composable
fun ResponderDashboardScreen(
    viewModel: DashboardViewModel,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
    walkieChannelName: String? = null,
    walkieVisible: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val nowMillis by rememberNowMillis()
    var showSosDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshMyLocation() }

    if (uiState.isLoading && uiState.profile == null) {
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

    val role = uiState.profile?.role ?: UserRole.VOLUNTEER
    val userId = uiState.profile?.userId

    val active = remember(uiState.allIncidents, userId) {
        DashboardMetrics.activeAssignment(uiState.allIncidents, userId)
    }

    // Anything already mine drops out of the queue below. Showing a job in both places is
    // how two responders end up driving to the same incident.
    val assignedIds = remember(uiState.assignedIncidents) {
        uiState.assignedIncidents.mapTo(HashSet()) { it.clientId }
    }
    val queue = remember(uiState.openIncidents, assignedIds, uiState.myLocation) {
        uiState.openIncidents
            .filter { it.clientId !in assignedIds }
            .nearestFirst(uiState.myLocation)
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

        // Availability gates dispatch entirely: the server-side matcher filters on it
        // before it scores anybody, so a responder who never sets this is never matched.
        // It sits high because it is the thing that makes the rest of the screen fill up.
        if (uiState.capabilities.canSetOwnAvailability) {
            item(key = "availability") {
                AvailabilityControl(
                    current = availability,
                    onChange = viewModel::setAvailability,
                )
            }
        }

        item(key = "role-stats") {
            RoleStatStrip(role = role, uiState = uiState, nowMillis = nowMillis)
        }

        if (active != null) {
            item(key = "active") {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    SectionHeader(
                        title = stringResource(R.string.dashboard_active_assignment),
                        actionLabel = stringResource(R.string.dashboard_view_details),
                        onAction = { actions.onDetail(active.clientId) },
                    )
                    ActiveAssignmentCard(
                        incident = active,
                        nowMillis = nowMillis,
                        myLocation = uiState.myLocation,
                        statusLabel = stringResource(active.status.labelRes()),
                        onClick = { actions.onDetail(active.clientId) },
                    )
                }
            }
        }

        // Every assigned job, each with the one action that advances it. The active card
        // above is a summary; this is where the work actually gets moved along.
        assignedWorkItems(
            incidents = uiState.assignedIncidents,
            nowMillis = nowMillis,
            myLocation = uiState.myLocation,
            onDetail = actions.onDetail,
            onAdvance = viewModel::updateStatus,
        )

        // NGO work is a mix question: which kinds of relief are open right now. Medical and
        // police work is a proximity question, so they get the roster instead.
        if (role == UserRole.NGO_RESPONDER) {
            item(key = "relief-mix") { ReliefMixSection(uiState = uiState) }
        } else {
            item(key = "nearby-responders") {
                NearbyRespondersSection(
                    uiState = uiState,
                    emptyMessage = stringResource(R.string.dashboard_no_responders),
                )
            }
        }

        unclaimedQueueItems(
            titleRes = if (role == UserRole.NGO_RESPONDER) {
                R.string.ngo_open_requests
            } else {
                R.string.dashboard_incidents_near_you
            },
            incidents = queue.take(6),
            nowMillis = nowMillis,
            myLocation = uiState.myLocation,
            onMap = actions.onMap,
            onDetail = actions.onDetail,
        )
    }
}

/** The responder's own assignments, each carrying its single forward action. */
private fun LazyListScope.assignedWorkItems(
    incidents: List<Incident>,
    nowMillis: Long,
    myLocation: GeoPoint?,
    onDetail: (String) -> Unit,
    onAdvance: (String, IncidentStatus) -> Unit,
) {
    if (incidents.isEmpty()) return

    item(key = "assigned-header") {
        SectionHeader(title = stringResource(R.string.dashboard_pending_assignments))
    }

    items(items = incidents, key = { "assigned-${it.clientId}" }) { incident ->
        val next = incident.status.nextAction()
        IncidentCard(
            incident = incident,
            nowMillis = nowMillis,
            myLocation = myLocation,
            onClick = { onDetail(incident.clientId) },
            actionLabel = next?.let { stringResource(it.labelRes) },
            onAction = next?.let { { onAdvance(incident.clientId, it.target) } },
        )
    }
}

/**
 * Open work nobody has taken.
 *
 * Rendered as rows in a single card rather than as full cards: this is a browse list, and
 * six stacked dispatch cards would push the assigned work off the screen entirely. Acting
 * on one happens on its detail screen, where the state machine and the audit trail live.
 */
private fun LazyListScope.unclaimedQueueItems(
    @StringRes titleRes: Int,
    incidents: List<Incident>,
    nowMillis: Long,
    myLocation: GeoPoint?,
    onMap: () -> Unit,
    onDetail: (String) -> Unit,
) {
    item(key = "queue-header") {
        SectionHeader(
            title = stringResource(titleRes),
            actionLabel = stringResource(R.string.dashboard_view_map),
            onAction = onMap,
        )
    }

    if (incidents.isEmpty()) {
        item(key = "queue-empty") {
            OperationalCard {
                NotConnectedPanel(message = stringResource(R.string.map_no_incidents))
            }
        }
        return
    }

    item(key = "queue-list") {
        OperationalCard {
            Column(modifier = Modifier.padding(vertical = Dimens.SpaceXs)) {
                incidents.forEach { incident ->
                    IncidentRow(
                        incident = incident,
                        nowMillis = nowMillis,
                        myLocation = myLocation,
                        onClick = { onDetail(incident.clientId) },
                        modifier = Modifier.padding(horizontal = Dimens.SpaceSm),
                    )
                }
            }
        }
    }
}

/**
 * The stat strip, per uniform.
 *
 * Each role gets four counters answering the question that role is actually asked at a
 * handover: a medic how many calls and how many treated, a police responder how many tasks
 * and how much of the crowd load is unclaimed, an NGO coordinator how many requests are
 * open and how many people were reached.
 */
@Composable
private fun RoleStatStrip(
    role: UserRole,
    uiState: DashboardUiState,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val all = uiState.allIncidents
    val open = uiState.openIncidents
    val userId = uiState.profile?.userId
    val mine = remember(all, userId) {
        all.filter { userId != null && it.assigneeId == userId }
    }
    val rate = remember(all, userId) { DashboardMetrics.responseRate(all, userId) }

    val stats = when (role) {
        UserRole.MEDICAL_RESPONDER -> listOf(
            CompactStat(
                label = stringResource(R.string.medical_calls_today),
                value = DashboardMetrics
                    .reportedToday(all, nowMillis, IncidentCategory.MEDICAL).toString(),
                icon = Icons.Filled.LocalHospital,
                tone = Accents.red,
            ),
            CompactStat(
                label = stringResource(R.string.dashboard_in_progress),
                value = DashboardMetrics.myTasks(all, userId).inProgress.toString(),
                icon = Icons.Filled.Schedule,
                tone = Accents.amber,
            ),
            CompactStat(
                label = stringResource(R.string.medical_patients_treated),
                value = DashboardMetrics
                    .countByStatus(mine, IncidentStatus.RESOLVED, IncidentCategory.MEDICAL)
                    .toString(),
                icon = Icons.Filled.CheckCircle,
                tone = Accents.green,
            ),
            CompactStat(
                label = stringResource(R.string.medical_sos_active),
                value = DashboardMetrics.activeSosCount(all).toString(),
                icon = Icons.Filled.Campaign,
                tone = Accents.purple,
            ),
        )

        UserRole.POLICE_RESPONDER -> listOf(
            CompactStat(
                label = stringResource(R.string.police_patrol_tasks),
                value = uiState.assignedIncidents.size.toString(),
                icon = Icons.Filled.LocalPolice,
                tone = Accents.blue,
            ),
            CompactStat(
                label = stringResource(R.string.dashboard_open_incidents),
                value = open.size.toString(),
                icon = Icons.Filled.Schedule,
                tone = Accents.amber,
            ),
            CompactStat(
                label = stringResource(R.string.police_crowd_incidents),
                value = DashboardMetrics.countByCategory(
                    open,
                    IncidentCategory.CROWD_SURGE,
                    IncidentCategory.BLOCKED_ROAD,
                ).toString(),
                icon = Icons.Filled.Groups,
                tone = Accents.purple,
            ),
            CompactStat(
                label = stringResource(R.string.dashboard_unassigned),
                value = DashboardMetrics.unassignedCount(open).toString(),
                icon = Icons.Filled.PersonSearch,
                tone = Accents.red,
            ),
        )

        else -> listOf(
            CompactStat(
                label = stringResource(R.string.ngo_relief_activities),
                value = uiState.assignedIncidents.size.toString(),
                icon = Icons.Filled.VolunteerActivism,
                tone = Accents.blue,
            ),
            CompactStat(
                label = stringResource(R.string.ngo_open_requests),
                value = DashboardMetrics.countByCategory(
                    open,
                    IncidentCategory.WATER,
                    IncidentCategory.SANITATION,
                ).toString(),
                icon = Icons.Filled.WaterDrop,
                tone = Accents.amber,
            ),
            CompactStat(
                label = stringResource(R.string.ngo_people_helped),
                value = DashboardMetrics
                    .countByStatus(mine, IncidentStatus.RESOLVED).toString(),
                icon = Icons.Filled.CheckCircle,
                tone = Accents.green,
            ),
            CompactStat(
                label = stringResource(R.string.dashboard_response_rate),
                // A dash, not "0%": a responder with no assignments yet has not failed to
                // respond to anything.
                value = rate?.let { "$it%" } ?: "—",
                icon = Icons.Filled.Speed,
                tone = Accents.purple,
            ),
        )
    }

    StatStrip(stats = stats, modifier = modifier)
}

/**
 * What kind of relief is open, as a donut.
 *
 * Built from open incident categories, which is the closest real analogue to the mockup's
 * relief-activity breakdown. There is no relief-activity entity in this system, and
 * inventing one to fill the chart would put a number in front of a coordinator that no
 * record supports.
 */
@Composable
private fun ReliefMixSection(
    uiState: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    val breakdown = remember(uiState.openIncidents) {
        DashboardMetrics.categoryBreakdown(uiState.openIncidents)
    }
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
        SectionHeader(title = stringResource(R.string.ngo_relief_overview))

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
 * The single forward step from a given state.
 *
 * Mirrors the main line of [com.varisahayak.domain.model.IncidentStateMachine]; the state
 * machine itself remains the authority and rejects anything illegal, so a stale card cannot
 * push an incident somewhere it should not go.
 */
private data class NextAction(val labelRes: Int, val target: IncidentStatus)

private fun IncidentStatus.nextAction(): NextAction? = when (this) {
    IncidentStatus.ASSIGNED -> NextAction(R.string.incident_accept, IncidentStatus.ACCEPTED)
    IncidentStatus.ACCEPTED -> NextAction(R.string.incident_start, IncidentStatus.IN_PROGRESS)
    IncidentStatus.IN_PROGRESS -> NextAction(R.string.incident_resolve, IncidentStatus.RESOLVED)

    // Nothing to advance: either terminal, not yet triaged, or waiting on command.
    else -> null
}

/**
 * Shift state as three explicit choices rather than an on/off switch.
 *
 * BUSY and OFF_SHIFT both remove a responder from dispatch, but they mean different
 * things to an organiser reading the roster — mid-incident versus gone home — and
 * collapsing them into one toggle would throw that away.
 *
 * Each chip pairs its colour with an icon and a label: priority and status are never
 * communicated by colour alone anywhere in this app, and shift state is no exception.
 */
@Composable
private fun AvailabilityControl(
    current: ResponderAvailability?,
    onChange: (ResponderAvailability) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        AvailabilityOption.entries.forEach { option ->
            val selected = current == option.state
            FilterChip(
                selected = selected,
                onClick = { onChange(option.state) },
                label = { Text(stringResource(option.labelRes)) },
                leadingIcon = { Icon(option.icon, contentDescription = null) },
                shape = FilterChipDefaults.shape,
                modifier = Modifier
                    // 48dp minimum touch target: this is tapped with gloves on, in
                    // sunlight, while walking.
                    .defaultMinSize(minHeight = Dimens.MinTouchTarget)
                    .semantics {
                        stateDescription = if (selected) "Selected" else "Not selected"
                    },
            )
        }
    }
}

private enum class AvailabilityOption(
    val state: ResponderAvailability,
    val labelRes: Int,
    val icon: ImageVector,
) {
    AVAILABLE(ResponderAvailability.AVAILABLE, R.string.dashboard_available, Icons.Filled.Bolt),
    BUSY(ResponderAvailability.BUSY, R.string.dashboard_busy, Icons.Filled.DoNotDisturbOn),
    OFF_SHIFT(
        ResponderAvailability.OFF_SHIFT,
        R.string.dashboard_off_shift,
        Icons.Filled.NightsStay,
    ),
}
