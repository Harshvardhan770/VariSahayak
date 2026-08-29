package com.varisahayak.domain.model

/**
 * A captured position.
 *
 * [accuracyMeters] is nullable and [isApproximate] is explicit because Android 12+ lets a
 * user grant coarse location only. That is a supported state, not a failure: the incident
 * is still created, and the reduced accuracy is carried through to whoever responds.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val isApproximate: Boolean = false,
    val capturedAtEpochMillis: Long? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}
