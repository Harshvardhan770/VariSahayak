package com.varisahayak.domain.repository

import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.QrLocation
import com.varisahayak.domain.model.QrLocationResolution
import kotlinx.coroutines.flow.Flow

/**
 * Resolves the fixed QR signs installed along the route.
 *
 * A token identifies a *place*, so its resolution is not sensitive and can be cached
 * aggressively — which is the point. A volunteer scanning a sign at a water point with no
 * signal still needs the location's name and coordinates to file a report against it, and
 * the whole route is small enough to hold on the device.
 */
interface QrLocationRepository {

    /** Every cached location, for the map and for offline resolution. */
    fun observeAll(): Flow<List<QrLocation>>

    /**
     * Resolves a scanned or typed token.
     *
     * Checks the local cache first and only then the network, so a scan in a dead spot
     * still returns a usable location. Returns [QrLocationResolution.Offline] — carrying
     * the raw token — when neither is available, because a report must remain fileable
     * against an unresolved token and be reconciled on sync.
     */
    suspend fun resolve(rawPayload: String): QrLocationResolution

    /** Pulls the route's locations for offline use. Safe to call repeatedly. */
    suspend fun refresh(): Outcome<Unit>

    /**
     * Records that a location was resolved, for the audit trail in §7.12.
     *
     * Deliberately narrow: who scanned, which location, when, and what it led to. The
     * device's own position is included only when permission was already granted for
     * another purpose — a scan is not a reason to start tracking a volunteer.
     */
    suspend fun recordScan(
        token: String,
        deviceLocation: GeoPoint?,
        incidentClientId: String? = null,
        reportClientId: String? = null,
    ): Outcome<Unit>
}
