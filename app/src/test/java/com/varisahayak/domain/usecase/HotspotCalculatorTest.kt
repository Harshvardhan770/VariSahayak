package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.SyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HotspotCalculatorTest {

    private val calculator = HotspotCalculator()

    @Test
    @DisplayName("incidents without coordinates are excluded rather than crashing")
    fun `excludes unlocated incidents`() {
        val result = calculator.cluster(
            listOf(
                incident("a", location = null),
                incident("b", location = point(17.6799, 75.3233)),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(listOf("b"), result.single().incidentClientIds)
    }

    @Test
    fun `returns empty when nothing has a location`() {
        val result = calculator.cluster(listOf(incident("a", location = null)))
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("nearby incidents collapse into one hotspot")
    fun `groups nearby incidents`() {
        // ~30 m apart, well inside the default 150 m cell.
        val result = calculator.cluster(
            listOf(
                incident("a", location = point(17.67990, 75.32330)),
                incident("b", location = point(17.68015, 75.32330)),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(2, result.single().incidentCount)
        assertFalse(result.single().isSingleIncident)
    }

    @Test
    @DisplayName("distant incidents stay separate")
    fun `separates distant incidents`() {
        // ~1.1 km apart.
        val result = calculator.cluster(
            listOf(
                incident("a", location = point(17.6799, 75.3233)),
                incident("b", location = point(17.6899, 75.3233)),
            ),
        )

        assertEquals(2, result.size)
    }

    @Test
    @DisplayName("a cluster takes the priority of its worst member, not an average")
    fun `cluster reports highest priority`() {
        val result = calculator.cluster(
            listOf(
                incident("low", location = point(17.6799, 75.3233), priority = IncidentPriority.LOW),
                incident("low2", location = point(17.67991, 75.32331), priority = IncidentPriority.LOW),
                incident(
                    "critical",
                    location = point(17.67992, 75.32332),
                    priority = IncidentPriority.CRITICAL,
                ),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(IncidentPriority.CRITICAL, result.single().highestPriority)
    }

    @Test
    @DisplayName("an SOS anywhere in a cluster flags the whole cluster")
    fun `cluster flags sos`() {
        val result = calculator.cluster(
            listOf(
                incident("a", location = point(17.6799, 75.3233)),
                incident("sos", location = point(17.67991, 75.32331), isSos = true),
            ),
        )

        assertTrue(result.single().hasSos)
    }

    @Test
    @DisplayName("hotspots are ordered by priority before count")
    fun `orders by priority then count`() {
        val result = calculator.cluster(
            listOf(
                // Three low-priority incidents in one place.
                incident("l1", location = point(17.6799, 75.3233), priority = IncidentPriority.LOW),
                incident("l2", location = point(17.67991, 75.32331), priority = IncidentPriority.LOW),
                incident("l3", location = point(17.67992, 75.32332), priority = IncidentPriority.LOW),
                // A single critical one, far away.
                incident(
                    "c1",
                    location = point(17.7099, 75.3533),
                    priority = IncidentPriority.CRITICAL,
                ),
            ),
        )

        assertEquals(IncidentPriority.CRITICAL, result.first().highestPriority)
        assertEquals(1, result.first().incidentCount)
    }

    @Test
    @DisplayName("a lone incident exposes its id so a tap can open it directly")
    fun `single incident exposes its id`() {
        val result = calculator.cluster(listOf(incident("solo", location = point(17.6799, 75.3233))))

        assertTrue(result.single().isSingleIncident)
        assertEquals("solo", result.single().singleIncidentId)
    }

    @Test
    fun `multi incident cluster has no single id`() {
        val result = calculator.cluster(
            listOf(
                incident("a", location = point(17.6799, 75.3233)),
                incident("b", location = point(17.67991, 75.32331)),
            ),
        )

        assertEquals(null, result.single().singleIncidentId)
    }

    private fun point(lat: Double, lon: Double) = GeoPoint(latitude = lat, longitude = lon)

    private fun incident(
        id: String,
        location: GeoPoint?,
        priority: IncidentPriority = IncidentPriority.MEDIUM,
        isSos: Boolean = false,
    ) = Incident(
        clientId = id,
        category = IncidentCategory.OTHER,
        description = "test",
        location = location,
        reporterId = "reporter",
        reportedAtEpochMillis = 0L,
        status = IncidentStatus.REPORTED,
        priority = priority,
        syncState = SyncState.SYNCED,
        isSos = isSos,
    )
}
