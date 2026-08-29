package com.varisahayak.feature.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.CategoryFilterRow
import com.varisahayak.core.designsystem.component.GlassSurface
import com.varisahayak.core.designsystem.component.IncidentCard
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.designsystem.component.solidColor
import com.varisahayak.core.permissions.AppPermissions
import com.varisahayak.core.permissions.PermissionPermanentlyDeniedDialog
import com.varisahayak.core.permissions.PermissionRationaleDialog
import com.varisahayak.core.permissions.rememberPermissionController
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.usecase.Hotspot
import com.varisahayak.domain.usecase.HotspotCalculator
import com.varisahayak.core.utils.rememberNowMillis
import com.varisahayak.domain.usecase.distanceMetresTo
/**
 * The live command map.
 *
 * Three behaviours matter more than the map itself.
 *
 * First, pins come from the local database, so they render even when the tile layer cannot
 * load. A dead spot costs the basemap, not the incidents — and the network pill in the app
 * bar is what explains the grey background rather than leaving the volunteer wondering.
 *
 * Second, nothing here is gated on location permission: the map opens, incidents appear,
 * and the only thing a denied permission costs is the blue dot and the distance readouts.
 *
 * Third, the basemap is deliberately desaturated (`R.raw.map_style_operational`). Google's
 * default styling competes with the pins — a red hospital icon and a red critical marker
 * are the same colour at a glance. Pushing the basemap to slate means the only saturated
 * things on screen are incidents.
 */
@Composable
fun IncidentMapScreen(
    onIncidentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberPermissionController(AppPermissions.LOCATION) {
        viewModel.refreshMyLocation()
    }
    var showRationale by remember { mutableStateOf(false) }
    var showPermanentlyDenied by remember { mutableStateOf(false) }
    // Filter and drawer state live here rather than in the ViewModel: neither survives
    // process death meaningfully, neither is business logic, and putting them in the VM
    // would change a contract that other screens share.
    var selectedCategories by rememberSaveable(
        saver = CategorySetSaver,
    ) { mutableStateOf(emptySet<IncidentCategory>()) }
    var drawerExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (permissions.state.isAnyGranted) viewModel.refreshMyLocation()
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(PANDHARPUR, ROUTE_ZOOM)
    }
    // Only follow the user the first time a fix arrives. Re-centring on every update would
    // fight a volunteer who is panning the map to look somewhere else.
    var hasCentredOnUser by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.myLocation) {
        val location = uiState.myLocation
        if (location != null && !hasCentredOnUser) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(location.latitude, location.longitude),
                LOCAL_ZOOM,
            )
            hasCentredOnUser = true
        }
    }
    val mapStyle = remember(context) {
        MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_operational)
    }
    val categoryCounts = remember(uiState.incidents) {
        uiState.incidents.groupingBy { it.category }.eachCount()
    }
    val visibleIncidents = remember(uiState.incidents, selectedCategories) {
        if (selectedCategories.isEmpty()) {
            uiState.incidents
        } else {
            uiState.incidents.filter { it.category in selectedCategories }
        }
    }
    // Reuse the ViewModel's clustering when nothing is filtered; only re-cluster when the
    // filter actually changes the input set. HotspotCalculator is pure and dependency-free,
    // so constructing one here costs nothing and keeps the VM contract untouched.
    val clusterer = remember { HotspotCalculator() }
    val visibleHotspots = remember(uiState.hotspots, visibleIncidents, selectedCategories) {
        if (selectedCategories.isEmpty()) uiState.hotspots else clusterer.cluster(visibleIncidents)
    }
    // Triage order: worst first, then nearest. A responder scanning the drawer should never
    // have to scroll past routine work to find a critical one.
    val myLocation = uiState.myLocation
    val triaged = remember(visibleIncidents, myLocation) {
        visibleIncidents.sortedWith(
            compareByDescending<Incident> { it.isSos }
                .thenByDescending { it.priority.rank }
                .thenBy { incident ->
                    val mine = myLocation
                    val theirs = incident.location
                    if (mine != null && theirs != null) {
                        mine.distanceMetresTo(theirs)
                    } else {
                        Double.MAX_VALUE
                    }
                }
                .thenByDescending { it.reportedAtEpochMillis },
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A right-hand panel is correct on a tablet or in landscape and actively wrong on a
        // 360dp phone, where it would leave a sliver of map. Below the breakpoint the same
        // content becomes a bottom sheet.
        val useSidePanel = maxWidth >= SIDE_PANEL_BREAKPOINT
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = permissions.state.isAnyGranted,
                mapStyleOptions = mapStyle,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = true,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                // Keeps Google's attribution clear of the drawer. Obscuring it breaks the
                // Maps terms of service, and a hidden legal notice is a shipping blocker.
                bottom = if (useSidePanel) 0.dp else Dimens.DrawerPeekHeight,
                end = if (useSidePanel) Dimens.DrawerWidth else 0.dp,
            ),
        ) {
            if (uiState.showPalkhiTracks) {
                PalkhiMapContent(palkhis = uiState.palkhis, visible = true)
            }
            
            visibleHotspots.forEach { hotspot ->
                HotspotMarker(
                    hotspot = hotspot,
                    onClick = { hotspot.singleIncidentId?.let(onIncidentSelected) },
                )
            }
        }
        // --- top-left floating controls ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = Dimens.FloatingInset)
                .fillMaxWidth(if (useSidePanel) 0.6f else 1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            CategoryFilterRow(
                selected = selectedCategories,
                counts = categoryCounts,
                onToggle = { category ->
                    selectedCategories = if (category in selectedCategories) {
                        selectedCategories - category
                    } else {
                        selectedCategories + category
                    }
                },
                onClear = { selectedCategories = emptySet() },
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = Dimens.FloatingInset)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.showPalkhiTracks,
                    onClick = viewModel::togglePalkhiTracks,
                    label = { Text(stringResource(R.string.map_palkhi_tracking)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.IconSm)
                        )
                    }
                )
            }

            AnimatedVisibility(
                visible = uiState.showPalkhiTracks,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PalkhiTrackingPanel(trackingInfo = uiState.palkhiTracking)
            }

            uiState.locationMessage?.let { message ->
                LocationMessageCard(
                    message = message,
                    onAction = {
                        when (message) {
                            LocationMessage.PermissionDenied ->
                                if (permissions.isPermanentlyDenied) {
                                    showPermanentlyDenied = true
                                } else {
                                    showRationale = true
                                }
                            else -> viewModel.refreshMyLocation()
                        }
                    },
                    onDismiss = viewModel::dismissLocationMessage,
                    modifier = Modifier.padding(horizontal = Dimens.FloatingInset),
                )
            }
        }
        // --- recentre ---
        RecentreButton(
            onClick = {
                hasCentredOnUser = false
                when {
                    permissions.state.isAnyGranted -> viewModel.refreshMyLocation()
                    permissions.isPermanentlyDenied -> showPermanentlyDenied = true
                    else -> showRationale = true
                }
            },
            modifier = Modifier
                .align(if (useSidePanel) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(
                    start = Dimens.ScreenPadding,
                    end = Dimens.ScreenPadding,
                    bottom = if (useSidePanel) {
                        Dimens.ScreenPadding
                    } else {
                        Dimens.DrawerPeekHeight + Dimens.SpaceSm
                    },
                ),
        )
        // --- incident drawer ---
        IncidentDrawer(
            incidents = triaged,
            myLocation = uiState.myLocation,
            expanded = drawerExpanded,
            useSidePanel = useSidePanel,
            onToggleExpanded = { drawerExpanded = !drawerExpanded },
            onIncidentSelected = onIncidentSelected,
            modifier = Modifier.align(
                if (useSidePanel) Alignment.TopEnd else Alignment.BottomCenter,
            ),
        )
    }
    if (showRationale) {
        PermissionRationaleDialog(
            title = stringResource(R.string.permission_location_title),
            rationale = stringResource(R.string.permission_location_rationale),
            onConfirm = {
                showRationale = false
                permissions.request()
            },
            onDismiss = { showRationale = false },
        )
    }
    if (showPermanentlyDenied) {
        PermissionPermanentlyDeniedDialog(
            title = stringResource(R.string.permission_location_title),
            message = stringResource(R.string.permission_location_denied),
            onOpenSettings = {
                showPermanentlyDenied = false
                permissions.openAppSettings()
            },
            onDismiss = { showPermanentlyDenied = false },
        )
    }
}
/**
 * The active-incident drawer.
 *
 * Collapsing hides the list but never the header — the count stays visible, because "how
 * many open incidents are there" is the one number a coordinator checks constantly and
 * should never have to expand a panel to read.
 */
@Composable
private fun IncidentDrawer(
    incidents: List<Incident>,
    myLocation: com.varisahayak.domain.model.GeoPoint?,
    expanded: Boolean,
    useSidePanel: Boolean,
    onToggleExpanded: () -> Unit,
    onIncidentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val nowMillis by rememberNowMillis()
    val panelModifier = if (useSidePanel) {
        modifier
            .padding(Dimens.FloatingInset)
            .width(Dimens.DrawerWidth)
    } else {
        modifier
            .padding(Dimens.FloatingInset)
            .fillMaxWidth()
    }
    GlassSurface(modifier = panelModifier) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.map_active_incidents),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = incidents.size.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = stringResource(
                        if (expanded) R.string.map_drawer_collapse else R.string.map_drawer_expand,
                    ),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(Dimens.IconMd),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + if (useSidePanel) {
                    slideInHorizontally { it / 4 }
                } else {
                    slideInVertically { it / 4 }
                },
                exit = fadeOut() + if (useSidePanel) {
                    slideOutHorizontally { it / 4 }
                } else {
                    slideOutVertically { it / 4 }
                },
            ) {
                if (incidents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.map_no_incidents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(
                            horizontal = Dimens.SpaceMd,
                            vertical = Dimens.SpaceLg,
                        ),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = if (useSidePanel) 620.dp else 340.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = Dimens.SpaceSm,
                            end = Dimens.SpaceSm,
                            bottom = Dimens.SpaceSm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                    ) {
                        items(items = incidents, key = { it.clientId }) { incident ->
                            IncidentCard(
                                incident = incident,
                                nowMillis = nowMillis,
                                myLocation = myLocation,
                                onClick = { onIncidentSelected(incident.clientId) },
                                actionLabel = stringResource(R.string.action_dispatch),
                                onAction = { onIncidentSelected(incident.clientId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun RecentreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    GlassSurface(
        modifier = modifier
            .size(Dimens.MinTouchTarget)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.cd_map_recenter),
                tint = colors.textPrimary,
                modifier = Modifier.size(Dimens.IconMd),
            )
        }
    }
}
/**
 * One marker per cluster.
 *
 * Isolated in its own composable because this is the only place the app touches the
 * maps-compose marker API. If `rememberUpdatedMarkerState` does not resolve against the
 * pinned maps-compose version, this function is the single site to adjust — earlier
 * releases expose `rememberMarkerState(key, position)` instead.
 */
@Composable
private fun HotspotMarker(
    hotspot: Hotspot,
    onClick: () -> Unit,
) {
    val fill = hotspot.highestPriority.solidColor()
    val content = VariTheme.colors.cardSurface
    val priorityLabel = stringResource(hotspot.highestPriority.labelRes())
    val title = if (hotspot.isSingleIncident) {
        priorityLabel
    } else {
        stringResource(R.string.map_hotspot_incidents, hotspot.incidentCount)
    }
    val icon = remember(hotspot, fill, content) {
        MapMarkerIcons.forHotspot(hotspot, fill, content)
    }
    Marker(
        state = rememberUpdatedMarkerState(
            position = LatLng(hotspot.centre.latitude, hotspot.centre.longitude),
        ),
        icon = icon,
        // Title and snippet are what make this readable without relying on the marker's
        // colour, and they are what TalkBack announces.
        title = title,
        snippet = priorityLabel,
        onClick = {
            onClick()
            // false lets the map show the info window as well as handling the tap.
            false
        },
    )
}
@Composable
private fun LocationMessageCard(
    message: LocationMessage,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val (text, actionLabel) = when (message) {
        LocationMessage.PermissionDenied ->
            stringResource(R.string.permission_location_denied) to
                stringResource(R.string.map_grant_location)
        LocationMessage.PermissionApproximate ->
            stringResource(R.string.permission_location_coarse_only) to null
        LocationMessage.LocationDisabled ->
            stringResource(R.string.permission_location_disabled) to
                stringResource(R.string.action_retry)
        LocationMessage.Unavailable ->
            stringResource(R.string.map_location_unavailable) to
                stringResource(R.string.action_retry)
    }
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.SpaceMd)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = Dimens.SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                if (actionLabel != null) {
                    VariSecondaryButton(text = actionLabel, onClick = onAction)
                }
                VariSecondaryButton(
                    text = stringResource(R.string.action_close),
                    onClick = onDismiss,
                )
            }
        }
    }
}
/**
 * Persists the category filter across configuration change.
 *
 * Enums are stored by name, not ordinal: an ordinal written before a reorder would restore
 * as a different category, which is exactly the kind of silent wrongness a filter must not
 * have.
 */
private val CategorySetSaver = androidx.compose.runtime.saveable.Saver<
    androidx.compose.runtime.MutableState<Set<IncidentCategory>>,
    List<String>,
    >(
    save = { state -> state.value.map { it.name } },
    restore = { names ->
        mutableStateOf(
            names.mapNotNull { name ->
                IncidentCategory.entries.firstOrNull { it.name == name }
            }.toSet(),
        )
    },
)
/** Pandharpur — the destination of the Wari, and a sensible default view. */
private val PANDHARPUR = LatLng(17.6799, 75.3233)
private const val ROUTE_ZOOM = 11f
private const val LOCAL_ZOOM = 15f
/** Below this the right-hand panel becomes a bottom sheet. */
private val SIDE_PANEL_BREAKPOINT = 600.dp
