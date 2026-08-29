package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentPriority
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Groups incidents into geographic clusters for the map and the command dashboard.
 *
 * This is pure domain logic rather than a map-library feature, for two reasons: it is
 * fully unit-testable without a device, and the same output feeds the organiser's hotspot
 * view, which is not a map at all.
 *
 * Distance uses a plain equirectangular approximation. Over the scale of a pilgrimage
 * route — tens of kilometres, far from the poles — the distortion is irrelevant, and it
 * avoids dragging in a geospatial library to compare two nearby points.
 */
@Singleton
class HotspotCalculator @Inject constructor() {

    /**
     * Groups incidents that fall within [radiusMeters] of each other and returns the
     * groups ordered by severity: highest priority first, then by count.
     *
     * Deliberately greedy agglomeration rather than grid bucketing. A fixed grid produces
     * an arbitrary artefact — two incidents thirty metres apart render as separate pins
     * whenever a cell boundary happens to fall between them, which is precisely the
     * overlapping-pin mess clustering exists to prevent. Proximity should decide grouping,
     * not where the grid lines land.
     *
     * O(n²), which is fine for the number of open incidents a map ever shows at once.
     *
     * Incidents with no location are excluded from clustering but are never dropped from
     * the underlying list — a report without coordinates is still a real report.
     */
    fun cluster(
        incidents: List<Incident>,
        radiusMeters: Double = DEFAULT_CLUSTER_RADIUS_METRES,
    ): List<Hotspot> {
        val located = incidents.filter { it.location != null }
        if (located.isEmpty()) return emptyList()

        // Seed from the most severe incidents first, so a critical one anchors its cluster
        // rather than being absorbed into a neighbouring group of routine reports. clientId
        // breaks ties, which keeps the output stable across recompositions.
        val ordered = located.sortedWith(
            compareByDescending<Incident> { it.priority.rank }.thenBy { it.clientId },
        )

        val unassigned = ordered.toMutableList()
        val clusters = mutableListOf<List<Incident>>()

        while (unassigned.isNotEmpty()) {
            val seed = unassigned.removeAt(0)
            val seedPoint = seed.location!!

            val members = mutableListOf(seed)
            val iterator = unassigned.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (seedPoint.isWithin(radiusMeters, candidate.location!!)) {
                    members += candidate
                    iterator.remove()
                }
            }
            clusters += members
        }

        return clusters
            .map { it.toHotspot() }
            .sortedWith(
                compareByDescending<Hotspot> { it.highestPriority.rank }
                    .thenByDescending { it.incidentCount },
            )
    }

    private fun List<Incident>.toHotspot(): Hotspot {
        val points = mapNotNull { it.location }
        val highest = maxOf { it.priority.rank }

        return Hotspot(
            centre = GeoPoint(
                latitude = points.sumOf { it.latitude } / points.size,
                longitude = points.sumOf { it.longitude } / points.size,
            ),
            incidentCount = size,
            highestPriority = IncidentPriority.entries.first { it.rank == highest },
            hasSos = any { it.isSos },
            incidentClientIds = map { it.clientId },
        )
    }

    companion object {
        /**
         * Roughly a city block. Small enough that a cluster still means "over there",
         * large enough that a dense stretch of route does not become an unreadable pile of
         * overlapping pins.
         */
        const val DEFAULT_CLUSTER_RADIUS_METRES = 150.0
    }
}

/**
 * A group of nearby incidents.
 *
 * [highestPriority] and [hasSos] drive the marker's appearance. A cluster is rendered at
 * the severity of its worst member — averaging would let one critical incident disappear
 * into a crowd of routine ones.
 */
data class Hotspot(
    val centre: GeoPoint,
    val incidentCount: Int,
    val highestPriority: IncidentPriority,
    val hasSos: Boolean,
    val incidentClientIds: List<String>,
) {
    val isSingleIncident: Boolean get() = incidentCount == 1

    val singleIncidentId: String? get() = incidentClientIds.singleOrNull()
}

/** Whether two points are within [metres] of each other, same approximation as above. */
fun GeoPoint.isWithin(metres: Double, other: GeoPoint): Boolean {
    val latDelta = abs(latitude - other.latitude) * 111_320.0
    val lonDelta = abs(longitude - other.longitude) *
        111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
    return (latDelta * latDelta + lonDelta * lonDelta) <= metres * metres
}

/**
 * Separation in metres, same equirectangular approximation as [isWithin].
 *
 * Exists so the incident cards can show "120 m away" without a second, subtly different
 * idea of distance living in the UI layer. Over a pilgrimage route the error is well under
 * the GPS accuracy the number is derived from.
 */
fun GeoPoint.distanceMetresTo(other: GeoPoint): Double {
    val latDelta = abs(latitude - other.latitude) * 111_320.0
    val lonDelta = abs(longitude - other.longitude) *
        111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
    return sqrt(latDelta * latDelta + lonDelta * lonDelta)
}
