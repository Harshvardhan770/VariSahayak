package com.varisahayak.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.varisahayak.core.common.Clock
import com.varisahayak.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Play Services implementation of [LocationProvider].
 *
 * Deliberately uses the Task API through [suspendCancellableCoroutine] rather than pulling
 * in kotlinx-coroutines-play-services — it is a handful of lines and one fewer dependency
 * to keep version-aligned.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : LocationProvider {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    override fun permissionStatus(): LocationPermissionStatus = when {
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ->
            LocationPermissionStatus.GrantedFine

        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ->
            LocationPermissionStatus.GrantedCoarse

        // This class cannot distinguish "denied" from "permanently denied" — that needs an
        // Activity to ask shouldShowRequestPermissionRationale. The Compose permission
        // holder makes that call.
        else -> LocationPermissionStatus.Denied
    }

    override fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission") // guarded by permissionStatus() immediately below
    override suspend fun currentFix(timeoutMillis: Long): LocationFix {
        val status = permissionStatus()
        if (!status.isGranted) return LocationFix.PermissionDenied
        if (!isLocationEnabled()) return LocationFix.LocationDisabled

        val approximate = status == LocationPermissionStatus.GrantedCoarse
        val priority = if (approximate) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        } else {
            Priority.PRIORITY_HIGH_ACCURACY
        }

        return try {
            val fresh = withTimeoutOrNull(timeoutMillis) {
                requestCurrentLocation(priority, timeoutMillis)
            }

            if (fresh != null) {
                return LocationFix.Available(fresh.toGeoPoint(approximate, clock.nowEpochMillis()))
            }

            // No fresh reading in time — fall back rather than making the volunteer wait.
            val lastKnown = withTimeoutOrNull(LAST_KNOWN_TIMEOUT_MILLIS) { requestLastLocation() }
                ?: return LocationFix.Timeout

            val ageMillis = clock.nowEpochMillis() - lastKnown.time
            if (ageMillis > LocationDefaults.LAST_KNOWN_MAX_AGE_MILLIS) {
                LocationFix.Timeout
            } else {
                LocationFix.LastKnown(
                    point = lastKnown.toGeoPoint(approximate, lastKnown.time),
                    ageMillis = ageMillis,
                )
            }
        } catch (cancellation: CancellationException) {
            // Never swallowed: the caller navigated away or the scope died.
            // withTimeoutOrNull already handles expiry, so this is only real cancellation.
            throw cancellation
        } catch (error: SecurityException) {
            // Permission revoked between the check above and the call.
            LocationFix.PermissionDenied
        } catch (error: Exception) {
            LocationFix.Unavailable(error)
        }
    }

    @SuppressLint("MissingPermission") // guarded before the callback is registered
    override fun locationUpdates(intervalMillis: Long): Flow<LocationFix> = callbackFlow {
        val status = permissionStatus()
        if (!status.isGranted) {
            trySend(LocationFix.PermissionDenied)
            close()
            return@callbackFlow
        }
        if (!isLocationEnabled()) {
            trySend(LocationFix.LocationDisabled)
            close()
            return@callbackFlow
        }

        val approximate = status == LocationPermissionStatus.GrantedCoarse

        // Balanced power, not high accuracy: this runs for hours on a volunteer's phone
        // and a continuously-woken GPS radio will flatten it before the day is out.
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMillis,
        )
            .setMinUpdateIntervalMillis(LocationDefaults.TRACKING_MIN_INTERVAL_MILLIS)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    LocationFix.Available(
                        location.toGeoPoint(approximate, clock.nowEpochMillis()),
                    ),
                )
            }
        }

        try {
            client.requestLocationUpdates(request, callback, context.mainLooper)
        } catch (error: SecurityException) {
            trySend(LocationFix.PermissionDenied)
            close()
            return@callbackFlow
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }

    private suspend fun requestCurrentLocation(
        priority: Int,
        durationMillis: Long,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val tokenSource = CancellationTokenSource()

        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setDurationMillis(durationMillis)
            .setMaxUpdateAgeMillis(LocationDefaults.LAST_KNOWN_MAX_AGE_MILLIS)
            .build()

        client.getCurrentLocation(request, tokenSource.token)
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { continuation.resume(null) }
            .addOnCanceledListener { continuation.resume(null) }

        continuation.invokeOnCancellation { tokenSource.cancel() }
    }

    private suspend fun requestLastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { location -> continuation.resume(location) }
                .addOnFailureListener { continuation.resume(null) }
                .addOnCanceledListener { continuation.resume(null) }
        }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LAST_KNOWN_TIMEOUT_MILLIS = 2_000L
    }
}

private fun Location.toGeoPoint(approximate: Boolean, capturedAt: Long): GeoPoint = GeoPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    isApproximate = approximate,
    capturedAtEpochMillis = capturedAt,
)
