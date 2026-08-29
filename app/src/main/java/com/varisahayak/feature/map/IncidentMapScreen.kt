package com.varisahayak.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
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
import com.varisahayak.core.designsystem.component.OfflineBanner
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.permissions.AppPermissions
import com.varisahayak.core.permissions.PermissionPermanentlyDeniedDialog
import com.varisahayak.core.permissions.PermissionRationaleDialog
import com.varisahayak.core.permissions.rememberPermissionController
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.usecase.Hotspot

/**
 * The operational map.
 *
 * Two behaviours matter more than the map itself. First, pins come from the local
 * database, so they render even when the tile layer cannot load — the offline banner
 * explains the grey background rather than leaving the volunteer wondering. Second,
 * nothing here is gated on location permission: the map opens, incidents appear, and the
 * only thing a denied permission costs is the blue dot.
 */
@Composable
fun IncidentMapScreen(
    onIncidentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissions = rememberPermissionController(AppPermissions.LOCATION) {
        viewModel.refreshMyLocation()
    }
    var showRationale by remember { mutableStateOf(false) }
    var showPermanentlyDenied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (permissions.state.isAnyGranted) viewModel.refreshMyLocation()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(PANDHARPUR, ROUTE_ZOOM)
    }

    // Only follow the user the first time a fix arrives. Re-centring on every update would
    // fight a volunteer who is panning the map to look somewhere else.
    var hasCentredOnUser by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.myLocation) {
        val location = uiState.myLocation
        if (location != null && !hasCentredOnUser) {
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(LatLng(location.latitude, location.longitude), LOCAL_ZOOM)
            hasCentredOnUser = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = permissions.state.isAnyGranted,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
            ),
        ) {
            uiState.hotspots.forEach { hotspot ->
                HotspotMarker(
                    hotspot = hotspot,
                    onClick = { hotspot.singleIncidentId?.let(onIncidentSelected) },
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            if (uiState.isOffline) {
                OfflineBanner(detail = stringResource(R.string.map_offline_detail))
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
                )
            }
        }

        FloatingActionButton(
            onClick = {
                hasCentredOnUser = false
                if (permissions.state.isAnyGranted) {
                    viewModel.refreshMyLocation()
                } else if (permissions.isPermanentlyDenied) {
                    showPermanentlyDenied = true
                } else {
                    showRationale = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimens.ScreenPadding)
                .size(Dimens.MinTouchTarget),
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.cd_map_recenter),
            )
        }
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
    val colors = VariTheme.colors
    val (fill, content) = when (hotspot.highestPriority) {
        IncidentPriority.CRITICAL -> colors.critical to colors.onCritical
        IncidentPriority.HIGH -> colors.warning to colors.onWarning
        IncidentPriority.MEDIUM -> colors.info to colors.onInfo
        IncidentPriority.LOW -> MaterialTheme.colorScheme.outline to
            MaterialTheme.colorScheme.surface
    }

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

    Surface(
        color = colors.infoContainer,
        contentColor = colors.onInfoContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSm),
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceMd)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null) {
                VariSecondaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.padding(top = Dimens.SpaceSm),
                )
            }
        }
    }
}

/** Pandharpur — the destination of the Wari, and a sensible default view. */
private val PANDHARPUR = LatLng(17.6799, 75.3233)
private const val ROUTE_ZOOM = 11f
private const val LOCAL_ZOOM = 15f
