package com.varisahayak.core.location

import com.varisahayak.domain.model.GeoPoint

/**
 * The result of asking for a position.
 *
 * Every failure mode is named rather than collapsed into a null, because the UI reacts to
 * them differently: a denied permission sends the user to settings, disabled location
 * services sends them to the system toggle, and a timeout is simply worth retrying.
 *
 * None of these block incident creation. Location enriches a report; it never gates one.
 */
sealed interface LocationFix {

    data class Available(val point: GeoPoint) : LocationFix

    /**
     * A stored fix returned because no fresh one could be obtained in time. Carries its
     * age so the caller can decide whether it is still worth attaching.
     */
    data class LastKnown(val point: GeoPoint, val ageMillis: Long) : LocationFix

    data object PermissionDenied : LocationFix

    /** Permission is granted but the device's location services are switched off. */
    data object LocationDisabled : LocationFix

    data object Timeout : LocationFix

    data class Unavailable(val cause: Throwable? = null) : LocationFix

    /** The position, whatever its provenance, or null if there is none to attach. */
    val pointOrNull: GeoPoint?
        get() = when (this) {
            is Available -> point
            is LastKnown -> point
            else -> null
        }
}

/**
 * What the user has actually granted.
 *
 * [GrantedCoarse] is a supported operating state, not a failure — Android 12+ lets a user
 * grant approximate location, and a roughly-placed incident is far better than none.
 * Fixes obtained under it are flagged approximate so whoever responds knows.
 */
enum class LocationPermissionStatus {
    GrantedFine,
    GrantedCoarse,
    Denied,

    /** Denied with "don't ask again" — only the app settings screen can undo this. */
    PermanentlyDenied,
    ;

    val isGranted: Boolean get() = this == GrantedFine || this == GrantedCoarse
}

object LocationDefaults {
    /** A volunteer standing still should not be waiting longer than this to file a report. */
    const val CURRENT_FIX_TIMEOUT_MILLIS = 8_000L

    /** A stored fix older than this is treated as not worth attaching. */
    const val LAST_KNOWN_MAX_AGE_MILLIS = 2 * 60 * 1000L

    /** On-shift tracking cadence. Balanced power, not high accuracy — this runs for hours. */
    const val TRACKING_INTERVAL_MILLIS = 60_000L
    const val TRACKING_MIN_INTERVAL_MILLIS = 30_000L

    /**
     * Beyond this age a responder's position is treated as unknown by the matcher rather
     * than as current. Dispatching on a stale position is worse than dispatching on none.
     */
    const val RESPONDER_POSITION_STALE_AFTER_MILLIS = 10 * 60 * 1000L
}

/** True when this position is too old to be treated as the subject's current location. */
fun GeoPoint.isStale(
    nowEpochMillis: Long,
    thresholdMillis: Long = LocationDefaults.RESPONDER_POSITION_STALE_AFTER_MILLIS,
): Boolean {
    val capturedAt = capturedAtEpochMillis ?: return true
    return nowEpochMillis - capturedAt > thresholdMillis
}
