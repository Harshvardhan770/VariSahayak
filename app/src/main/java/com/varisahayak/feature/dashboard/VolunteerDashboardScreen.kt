package com.varisahayak.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.varisahayak.R
import com.varisahayak.core.designsystem.Accents
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.accentTone
import com.varisahayak.core.designsystem.component.ActiveAssignmentCard
import com.varisahayak.core.designsystem.component.CompactStat
import com.varisahayak.core.designsystem.component.IncidentRow
import com.varisahayak.core.designsystem.component.ShimmerLoadingState
import com.varisahayak.core.designsystem.component.NotConnectedPanel
import com.varisahayak.core.designsystem.component.OperationalCard
import com.varisahayak.core.designsystem.component.PersonCard
import com.varisahayak.core.designsystem.component.SectionHeader
import com.varisahayak.core.designsystem.component.StatStrip
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.utils.formatDistance
import com.varisahayak.core.utils.rememberNowMillis
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.ResponderAvailability
import com.varisahayak.domain.usecase.distanceMetresTo

/**
 * The volunteer's home surface.
 *
 * Ordered by how urgently a volunteer needs each thing: who and where they are, the four
 * actions they take, the work that is theirs, then the work around them. Nothing on this
 * screen is aggregate reporting — a volunteer on the route does not need a chart, they
 * need the next thing to do.
 */
@Composable
fun VolunteerDashboardScreen(
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

    val userId = uiState.profile?.userId
    val tasks = remember(uiState.allIncidents, userId) {
        DashboardMetrics.myTasks(uiState.allIncidents, userId)
    }
    val active = remember(uiState.allIncidents, userId) {
        DashboardMetrics.activeAssignment(uiState.allIncidents, userId)
    }
    val nearby = remember(uiState.openIncidents, uiState.myLocation) {
        uiState.openIncidents.nearestFirst(uiState.myLocation).take(5)
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

        item(key = "my-tasks") {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                SectionHeader(title = stringResource(R.string.dashboard_my_tasks))
                MyTasksStrip(tasks = tasks)
            }
        }

        if (active != null) {
            item(key = "active") {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    SectionHeader(
                        title = stringResource(R.string.dashboard_active_incident),
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

        item(key = "nearby-responders") {
            NearbyRespondersSection(
                uiState = uiState,
                emptyMessage = stringResource(R.string.dashboard_no_responders),
            )
        }

        item(key = "near-you") {
            SectionHeader(
                title = stringResource(R.string.dashboard_incidents_near_you),
                actionLabel = stringResource(R.string.dashboard_view_map),
                onAction = actions.onMap,
            )
        }

        if (nearby.isEmpty()) {
            item(key = "near-you-empty") {
                OperationalCard {
                    NotConnectedPanel(message = stringResource(R.string.map_no_incidents))
                }
            }
        } else {
            item(key = "near-you-list") {
                OperationalCard {
                    Column(modifier = Modifier.padding(vertical = Dimens.SpaceXs)) {
                        nearby.forEach { incident ->
                            IncidentRow(
                                incident = incident,
                                nowMillis = nowMillis,
                                myLocation = uiState.myLocation,
                                onClick = { actions.onDetail(incident.clientId) },
                                modifier = Modifier.padding(horizontal = Dimens.SpaceSm),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The three task counters.
 *
 * Shared with the responder dashboards, because "what is mine" is the same question
 * whatever the uniform.
 */
@Composable
internal fun MyTasksStrip(
    tasks: MyTasks,
    modifier: Modifier = Modifier,
) {
    StatStrip(
        modifier = modifier,
        stats = listOf(
            CompactStat(
                label = stringResource(R.string.dashboard_new_assigned),
                value = tasks.newAssigned.toString(),
                icon = Icons.Filled.ErrorOutline,
                tone = Accents.red,
            ),
            CompactStat(
                label = stringResource(R.string.dashboard_in_progress),
                value = tasks.inProgress.toString(),
                icon = Icons.Filled.Schedule,
                tone = Accents.amber,
            ),
            CompactStat(
                label = stringResource(R.string.dashboard_completed),
                value = tasks.completed.toString(),
                icon = Icons.Filled.CheckCircle,
                tone = Accents.green,
            ),
        ),
    )
}

/**
 * Who else is on shift nearby.
 *
 * Real roster data, from `ResponderRepository.observeAvailable()`. Row-level security
 * decides who can see it: a volunteer typically cannot, and the section then reports that
 * nobody is visible rather than pretending the route is empty.
 */
@Composable
internal fun NearbyRespondersSection(
    uiState: DashboardUiState,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val ranked = remember(uiState.nearbyResponders, uiState.myLocation) {
        val mine = uiState.myLocation
        uiState.nearbyResponders.sortedBy { responder ->
            val theirs = responder.lastKnownLocation
            if (mine != null && theirs != null) mine.distanceMetresTo(theirs) else Double.MAX_VALUE
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        SectionHeader(title = stringResource(R.string.dashboard_nearby_responders))

        if (ranked.isEmpty()) {
            OperationalCard {
                NotConnectedPanel(message = emptyMessage)
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                items(items = ranked, key = { it.userId }) { responder ->
                    val separation = uiState.myLocation?.let { mine ->
                        responder.lastKnownLocation?.let { mine.distanceMetresTo(it) }
                    }
                    PersonCard(
                        displayName = responder.displayName
                            ?: stringResource(responder.role.labelRes()),
                        roleLabel = stringResource(responder.role.labelRes()),
                        tone = responder.role.accentTone(),
                        // Capabilities are free-text codes from the server. Shown raw and
                        // humanised rather than mapped, because the set is open-ended and a
                        // when() over it would silently drop anything new.
                        capabilityLabel = responder.capabilities.firstOrNull()?.humanise(),
                        distanceLabel = separation?.let { formatDistance(it) },
                        isAvailable = responder.availability == ResponderAvailability.AVAILABLE,
                    )
                }
            }
        }
    }
}

/** FIRST_AID -> First aid. */
private fun String.humanise(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Nearest first, then most recent. Incidents without a fix sort last, never dropped. */
internal fun List<Incident>.nearestFirst(
    myLocation: com.varisahayak.domain.model.GeoPoint?,
): List<Incident> = sortedWith(
    compareBy<Incident> { incident ->
        val theirs = incident.location
        if (myLocation != null && theirs != null) {
            myLocation.distanceMetresTo(theirs)
        } else {
            Double.MAX_VALUE
        }
    }.thenByDescending { it.reportedAtEpochMillis },
)
