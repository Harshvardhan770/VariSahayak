package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.IncidentEventKind
import com.varisahayak.domain.model.TimelineEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Response timings, derived from the audit trail.
 *
 * These are the numbers a command centre judges its own performance by, so the cases that
 * matter most are the incomplete ones: a stage that has not happened must never read as
 * zero, and a stage that never will must never read as still running.
 */
class IncidentTimelineMetricsTest {

    private val metrics = IncidentTimelineMetrics()

    private val start = 1_700_000_000_000L
    private fun at(seconds: Long) = start + seconds * 1_000

    private fun event(kind: IncidentEventKind, seconds: Long) = TimelineEvent(
        eventId = "$kind-$seconds",
        incidentClientId = "incident-1",
        type = kind,
        rawType = kind.name,
        occurredAtEpochMillis = at(seconds),
    )

    private fun ResponseMetrics.of(stage: MetricStage) = intervals.first { it.stage == stage }

    // --- the complete story ---------------------------------------------------------------

    @Test
    fun `a full lifecycle measures every stage`() {
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_REPORTED, 0),
                event(IncidentEventKind.PRIORITY_ASSIGNED, 12),
                event(IncidentEventKind.MATCHING_STARTED, 20),
                event(IncidentEventKind.RESPONDER_MATCHED, 48),
                event(IncidentEventKind.ASSIGNMENT_ACCEPTED, 83),
                event(IncidentEventKind.RESPONDER_ARRIVED, 443),
                event(IncidentEventKind.INCIDENT_RESOLVED, 1_080),
            ),
            nowMillis = at(2_000),
        )

        assertEquals(12_000L, result.of(MetricStage.TRIAGE).millis)
        assertEquals(48_000L, result.of(MetricStage.ASSIGNMENT).millis)
        // Assignment sent -> accepted, not reported -> accepted.
        assertEquals(35_000L, result.of(MetricStage.ACCEPTANCE).millis)
        assertEquals(360_000L, result.of(MetricStage.ARRIVAL).millis)
        assertEquals(1_080_000L, result.of(MetricStage.RESOLUTION).millis)
        assertTrue(result.isResolved)
    }

    @Test
    fun `elapsed time stops at resolution rather than running on`() {
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_REPORTED, 0),
                event(IncidentEventKind.INCIDENT_RESOLVED, 600),
            ),
            nowMillis = at(9_999),
        )

        assertEquals(600_000L, result.elapsedMillis)
    }

    // --- incomplete incidents ---------------------------------------------------------------

    @Test
    fun `an unresolved stage reads as in progress and never as zero`() {
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_REPORTED, 0),
                event(IncidentEventKind.PRIORITY_ASSIGNED, 12),
            ),
            nowMillis = at(300),
        )

        val resolution = result.of(MetricStage.RESOLUTION)
        assertNull(resolution.millis)
        assertTrue(resolution.inProgress)
        assertFalse(result.isResolved)
    }

    @Test
    fun `a stage whose start never happened is absent, not in progress`() {
        // Nothing was ever assigned, so there is no acceptance clock to be running.
        val result = metrics.calculate(
            listOf(event(IncidentEventKind.INCIDENT_REPORTED, 0)),
            nowMillis = at(300),
        )

        val acceptance = result.of(MetricStage.ACCEPTANCE)
        assertNull(acceptance.millis)
        assertFalse(acceptance.inProgress)
    }

    @Test
    fun `a closed incident stops its unfinished clocks`() {
        // Resolved without a responder ever arriving. Arrival did not happen and never
        // will; leaving it "in progress" would put a running clock on a closed incident.
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_REPORTED, 0),
                event(IncidentEventKind.RESPONDER_MATCHED, 30),
                event(IncidentEventKind.ASSIGNMENT_ACCEPTED, 60),
                event(IncidentEventKind.INCIDENT_RESOLVED, 200),
            ),
            nowMillis = at(9_999),
        )

        val arrival = result.of(MetricStage.ARRIVAL)
        assertNull(arrival.millis)
        assertFalse(arrival.inProgress)
    }

    @Test
    fun `a cancelled incident closes its clocks without counting as resolved`() {
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_REPORTED, 0),
                event(IncidentEventKind.INCIDENT_CANCELLED, 90),
            ),
            nowMillis = at(9_999),
        )

        assertFalse(result.isResolved)
        assertFalse(result.of(MetricStage.RESOLUTION).inProgress)
        assertEquals(90_000L, result.elapsedMillis)
    }

    @Test
    fun `an empty trail measures nothing rather than guessing`() {
        val result = metrics.calculate(emptyList(), nowMillis = at(500))

        assertNull(result.elapsedMillis)
        assertTrue(result.intervals.all { it.millis == null && !it.inProgress })
    }

    // --- offline reporting -------------------------------------------------------------------

    @Test
    fun `metrics measure from when the incident happened, not when it synced`() {
        // The whole point of recording both. An incident filed in a dead spot at 12:04 and
        // synced at 12:07 took three minutes to reach the server — but the response is
        // measured from 12:04, or every offline report would flatter the numbers.
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_CREATED_OFFLINE, 0),
                event(IncidentEventKind.INCIDENT_SYNCED, 180),
                event(IncidentEventKind.PRIORITY_ASSIGNED, 185),
                event(IncidentEventKind.INCIDENT_RESOLVED, 600),
            ),
            nowMillis = at(9_999),
        )

        assertEquals(185_000L, result.of(MetricStage.TRIAGE).millis)
        assertEquals(600_000L, result.of(MetricStage.RESOLUTION).millis)
    }

    @Test
    fun `an out-of-order trail is measured chronologically`() {
        // Events arrive from two sources — this device and the server's triggers — and
        // nothing guarantees the order they land in Room.
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_RESOLVED, 600),
                event(IncidentEventKind.INCIDENT_REPORTED, 0),
                event(IncidentEventKind.PRIORITY_ASSIGNED, 12),
            ),
            nowMillis = at(9_999),
        )

        assertEquals(12_000L, result.of(MetricStage.TRIAGE).millis)
        assertEquals(600_000L, result.of(MetricStage.RESOLUTION).millis)
    }

    @Test
    fun `a negative interval is clamped rather than reported`() {
        // Device clocks drift, and a report timestamped after its own triage would
        // otherwise produce a negative duration on a command screen.
        val result = metrics.calculate(
            listOf(
                event(IncidentEventKind.INCIDENT_REPORTED, 100),
                event(IncidentEventKind.PRIORITY_ASSIGNED, 40),
            ),
            nowMillis = at(9_999),
        )

        assertEquals(0L, result.of(MetricStage.TRIAGE).millis)
    }

    // --- event mapping -----------------------------------------------------------------------

    @Test
    fun `legacy event names map onto lifecycle stages`() {
        // The twelve names already persisted in Room and Postgres must keep working; the
        // timeline is an extension of that trail, not a replacement for it.
        assertEquals(IncidentEventKind.INCIDENT_REPORTED, IncidentEventKind.fromWire("CREATED"))
        assertEquals(IncidentEventKind.PRIORITY_ASSIGNED, IncidentEventKind.fromWire("PRIORITY_SET"))
        assertEquals(
            IncidentEventKind.MANUAL_PRIORITY_OVERRIDE,
            IncidentEventKind.fromWire("PRIORITY_OVERRIDDEN"),
        )
        assertEquals(IncidentEventKind.ASSIGNMENT_CREATED, IncidentEventKind.fromWire("ASSIGNED"))
        assertEquals(IncidentEventKind.INCIDENT_ESCALATED, IncidentEventKind.fromWire("ESCALATED"))
        assertEquals(IncidentEventKind.ADMIN_NOTE_ADDED, IncidentEventKind.fromWire("NOTE_ADDED"))
    }

    @Test
    fun `a generic status change resolves to the stage it reached`() {
        assertEquals(
            IncidentEventKind.ASSIGNMENT_ACCEPTED,
            IncidentEventKind.fromWire("STATUS_CHANGED", toValue = "ACCEPTED"),
        )
        assertEquals(
            IncidentEventKind.INCIDENT_RESOLVED,
            IncidentEventKind.fromWire("STATUS_CHANGED", toValue = "RESOLVED"),
        )
    }

    @Test
    fun `a status change with no target stays generic rather than guessing`() {
        assertEquals(
            IncidentEventKind.STATUS_CHANGED,
            IncidentEventKind.fromWire("STATUS_CHANGED", toValue = null),
        )
    }

    @Test
    fun `an unrecognised event is kept rather than dropped`() {
        // An audit trail that silently hides rows it does not understand is not an audit
        // trail. UNKNOWN still renders, using its raw type as the title.
        assertEquals(IncidentEventKind.UNKNOWN, IncidentEventKind.fromWire("SOMETHING_NEW"))
        assertEquals(IncidentEventKind.UNKNOWN, IncidentEventKind.fromWire(null))
    }
}
