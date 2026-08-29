package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentPriority
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

/**
 * Groups incidents into geographic clusters for the map and the command dashboard.
 *
 * This is pure domain logic rather than a map-library feature, for two reasons: it is
 * fully unit-testable without a device, and the same output feeds the organiser's hotspot
 * view, which is not a map at all.
 *
 * The grid is a plain equirectangular approximation. Over the scale of a pilgrimage route
 * — tens of kilometres, far from the poles — the distortion is irrelevant, and it avoids
 * dragging a geospatial library in for a bucketing operation.
 */
@Singleton
class HotspotCalculator @Inject constructor() {

    /**
     * Buckets incidents into cells of roughly [cellSizeMeters] and returns them ordered by
     * severity: highest priority first, then by count. Incidents with no location are
     * excluded from clustering but are never dropped from the underlying list — a report
     * without coordinates is still a real report.
     */
    fun cluster(
        incidents: List<Incident>,
        cellSizeMeters: Double = DEFAULT_CELL_SIZE_METRES,
    ): List<Hotspot> {
        val located = incidents.filter { it.location != null }
        if (located.isEmpty()) return emptyList()

        val latStep = cellSizeMeters / METRES_PER_DEGREE_LATITUDE

        return located
            .groupBy { incident ->
                val point = incident.location!!
                // Longitude degrees shrink toward the poles; scale the step by cos(lat) so
                // cells stay roughly square instead of stretching east-west.
                val lonStep = latStep / cos(Math.toRadians(point.latitude)).coerceAtLeast(MIN_COS)
                CellKey(
                    latIndex = floor(point.latitude / latStep).toInt(),
                    lonIndex = floor(point.longitude / lonStep).toInt(),
                )
            }
            .map { (_, cellIncidents) -> cellIncidents.toHotspot() }
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

    private data class CellKey(val latIndex: Int, val lonIndex: Int)

    companion object {
        /**
         * Roughly a city block. Small enough that a cluster still means "over there",
         * large enough that a dense stretch of route does not become an unreadable pile of
         * overlapping pins.
         */
        const val DEFAULT_CELL_SIZE_METRES = 150.0

        private const val METRES_PER_DEGREE_LATITUDE = 111_320.0

        /** Guards against a division blow-up at the poles. Irrelevant in practice here. */
        private const val MIN_COS = 0.01
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
