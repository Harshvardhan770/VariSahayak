package com.varisahayak.feature.map

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Cap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.GlassSurface
import com.varisahayak.domain.model.Palkhi
import com.varisahayak.domain.model.PalkhiTrackingInfo
import com.varisahayak.core.utils.formatDistance
import kotlin.math.roundToInt

@Composable
fun PalkhiMapContent(
    palkhis: List<Palkhi>,
    visible: Boolean
) {
    if (!visible) return

    palkhis.forEach { palkhi ->
        // Draw Route
        val routePoints = palkhi.route.map { LatLng(it.location.latitude, it.location.longitude) }
        Polyline(
            points = routePoints,
            color = palkhi.color.copy(alpha = 0.8f),
            width = 12f,
            startCap = RoundCap(),
            endCap = RoundCap(),
            jointType = JointType.ROUND
        )
        
        // Inner line for a better "track" look
        Polyline(
            points = routePoints,
            color = Color.White.copy(alpha = 0.4f),
            width = 4f,
            startCap = RoundCap(),
            endCap = RoundCap(),
            jointType = JointType.ROUND
        )

        // Draw Stops as small dots
        palkhi.route.forEach { stop ->
            Circle(
                center = LatLng(stop.location.latitude, stop.location.longitude),
                radius = 120.0, // Small but visible
                fillColor = palkhi.color,
                strokeColor = Color.White,
                strokeWidth = 2f
            )
        }

        // Draw Current Position Marker
        palkhi.currentPosition?.let { pos ->
            Marker(
                state = rememberUpdatedMarkerState(position = LatLng(pos.latitude, pos.longitude)),
                title = "${palkhi.name} (Live)",
                snippet = "Moving towards Pandharpur",
                zIndex = 1.0f,
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (palkhi.id == "tukaram") 15f else 230f // Slightly different hue for live
                )
            )
        }
    }
}

@Composable
fun PalkhiTrackingPanel(
    trackingInfo: List<PalkhiTrackingInfo>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.FloatingInset),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        trackingInfo.forEach { info ->
            PalkhiInfoCard(
                info = info,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PalkhiInfoCard(
    info: PalkhiTrackingInfo,
    modifier: Modifier = Modifier
) {
    GlassSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = if (info.palkhiId == "tukaram") Color(0xFFFF9800) else Color(0xFF2196F3),
                modifier = Modifier.size(Dimens.IconSm)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.palkhiName.replace(" Maharaj Palkhi", "").replace("Sant ", ""),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = info.distanceMetres?.let { formatDistance(it) } ?: "---",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val distance = info.distanceMetres
            if (distance != null && distance > 100) {
                // Estimate walking time at ~3.5 km/h (procession speed)
                val minutes = (distance / (3500.0 / 60.0)).roundToInt()
                val timeText = when {
                    minutes < 60 -> "${minutes}m"
                    else -> "${minutes / 60}h"
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
