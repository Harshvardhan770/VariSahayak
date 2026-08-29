package com.varisahayak.domain.model

import androidx.compose.ui.graphics.Color

data class Palkhi(
    val id: String,
    val name: String,
    val color: Color,
    val route: List<PalkhiStop>,
    val currentPosition: GeoPoint? = null,
    val lastUpdatedEpochMillis: Long? = null,
)

data class PalkhiStop(
    val name: String,
    val location: GeoPoint,
    val date: String, // e.g., "July 7"
)

/**
 * Derived metrics for a Palkhi relative to a user's location.
 */
data class PalkhiTrackingInfo(
    val palkhiId: String,
    val palkhiName: String,
    val distanceMetres: Double?,
    val nextStop: PalkhiStop?,
    val isTrackingActive: Boolean = true,
)
