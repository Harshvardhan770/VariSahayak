package com.varisahayak.core.location

import com.varisahayak.domain.model.GeoPoint
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Staleness decides whether the matcher treats a responder's position as current. Getting
 * it wrong dispatches somebody to where a responder used to be, so the boundary
 * behaviour is worth pinning down explicitly.
 */
class LocationStalenessTest {

    private val now = 1_000_000L
    private val threshold = LocationDefaults.RESPONDER_POSITION_STALE_AFTER_MILLIS

    @Test
    @DisplayName("a position with no capture time is treated as stale, not as current")
    fun `missing timestamp is stale`() {
        val point = GeoPoint(latitude = 17.6799, longitude = 75.3233)
        assertTrue(point.isStale(now))
    }

    @Test
    fun `a recent position is not stale`() {
        val point = pointCapturedAt(now - 60_000L)
        assertFalse(point.isStale(now))
    }

    @Test
    fun `a position older than the threshold is stale`() {
        val point = pointCapturedAt(now - threshold - 1)
        assertTrue(point.isStale(now))
    }

    @Test
    @DisplayName("exactly at the threshold is still usable")
    fun `boundary is inclusive`() {
        val point = pointCapturedAt(now - threshold)
        assertFalse(point.isStale(now))
    }

    @Test
    fun `custom threshold is honoured`() {
        val point = pointCapturedAt(now - 5_000L)
        assertFalse(point.isStale(now, thresholdMillis = 10_000L))
        assertTrue(point.isStale(now, thresholdMillis = 1_000L))
    }

    private fun pointCapturedAt(millis: Long) = GeoPoint(
        latitude = 17.6799,
        longitude = 75.3233,
        capturedAtEpochMillis = millis,
    )
}
