package com.varisahayak.core.location

import android.util.Log
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.repository.LocationRepository
import com.varisahayak.domain.repository.ResponderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the device's position while somebody is signed in and looking at the app.
 *
 * [LocationProvider.locationUpdates] existed and was collected by nothing; the position of
 * a volunteer's phone therefore never left it. That is not only a missing feature — the
 * matcher in `match_responder` scores proximity from `responders.last_location_at`, and
 * with nothing ever writing it, every candidate scored zero on distance and dispatch was
 * decided entirely by role, area and workload.
 *
 * Two destinations, because they answer different questions:
 *  - `public.locations` is the append-only track, written for every role.
 *  - `responders.last_*` is the single current fix the matcher ranks on, and only exists
 *    for responders. The update simply matches no rows for a volunteer.
 *
 * Foreground-only, deliberately. Following a volunteer around after they have put their
 * phone away needs a foreground service and a background-location grant, and this product
 * has not asked anyone for that.
 */
@Singleton
class LocationTracker @Inject constructor(
    private val locationProvider: LocationProvider,
    private val locationRepository: LocationRepository,
    private val responderRepository: ResponderRepository,
) {

    private val scope = CoroutineScope(SupervisorJob())
    private var job: Job? = null

    private val _lastPublished = MutableStateFlow<GeoPoint?>(null)

    /** The most recent position actually sent, for anything that wants to show it. */
    val lastPublished: StateFlow<GeoPoint?> = _lastPublished.asStateFlow()

    /**
     * Starts publishing, or does nothing if already running.
     *
     * Idempotent because the caller is a Compose effect that re-runs on configuration
     * change, and restarting the collection each time would drop and re-acquire the
     * location subscription for no reason.
     */
    fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            locationProvider.locationUpdates().collect { fix ->
                val point = fix.pointOrNull ?: return@collect
                publish(point)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun publish(point: GeoPoint) {
        try {
            locationRepository.record(point)
            // Attempted for everyone rather than gated on the role. A volunteer has no
            // responders row, so this matches nothing and costs one request; reading the
            // role here would mean this class needed a profile it otherwise does not.
            responderRepository.reportLocation(point)
            _lastPublished.value = point
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // The collection must survive a bad upload. Cancelling it here would stop
            // tracking for the rest of the session over one failed request.
            Log.w(TAG, "Could not publish position", error)
        }
    }

    private companion object {
        const val TAG = "LocationTracker"
    }
}
