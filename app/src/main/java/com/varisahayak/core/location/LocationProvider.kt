package com.varisahayak.core.location

import kotlinx.coroutines.flow.Flow

/**
 * Position access, abstracted so use cases and ViewModels never touch Play Services
 * directly and can be tested without a device.
 */
interface LocationProvider {

    fun permissionStatus(): LocationPermissionStatus

    fun isLocationEnabled(): Boolean

    /**
     * One fix for attaching to an incident.
     *
     * Tries for a fresh reading, falls back to a recent stored one, and gives up after
     * [timeoutMillis] rather than leaving a volunteer waiting. Never throws — every
     * failure is a [LocationFix] variant.
     */
    suspend fun currentFix(
        timeoutMillis: Long = LocationDefaults.CURRENT_FIX_TIMEOUT_MILLIS,
    ): LocationFix

    /**
     * Continuous updates for on-shift tracking.
     *
     * Collect this only while the responder is available and the app is foregrounded;
     * cancelling the collection stops the underlying updates.
     */
    fun locationUpdates(
        intervalMillis: Long = LocationDefaults.TRACKING_INTERVAL_MILLIS,
    ): Flow<LocationFix>
}
