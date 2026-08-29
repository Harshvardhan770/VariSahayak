package com.varisahayak.feature.dashboard

import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.SyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Calendar

/**
 * The dashboard counters are what a coordinator dispatches from, so they are tested rather
 * than eyeballed on a screen.
 *
 * `nowMillis` is always passed explicitly and derived from a fixed local midnight, so
 * "today" is deterministic and these do not fail when CI runs at 23:59.
 */
class DashboardMetricsTest {

    private val today = localMidnight()
    private val now = today + 12 * HOUR
    private val yesterday = today - 12 * HOUR

    // --- today boundaries -----------------------------------------------------

    @Test
    @DisplayName("SOS raised before local midnight does not count as today")
    fun `sosToday excludes yesterday`() {
        val incidents = listOf(
            incident("a", isSos = true, reportedAt = now - HOUR),
            incident("b", isSos = true, reportedAt = yesterday),
            incident("c", isSos = false, reportedAt = now),
        )

        assertEquals(1, DashboardMetrics.sosToday(incidents, now))
    }

    @Test
    @DisplayName("reportedToday filters by category when one is given")
    fun `reportedToday filters by category`() {
        val incidents = listOf(
            incident("a", category = IncidentCategory.MEDICAL, reportedAt = now),
            incident("b", category = IncidentCategory.WATER, reportedAt = now),
            incident("c", category = IncidentCategory.MEDICAL, reportedAt = yesterday),
        )

        assertEquals(2, DashboardMetrics.reportedToday(incidents, now))
        assertEquals(1, DashboardMetrics.reportedToday(incidents, now, IncidentCategory.MEDICAL))
    }

    // --- responder workload ---------------------------------------------------

    @Test
    @DisplayName("myTasks counts only this responder, and counts resolved as completed")
    fun `myTasks splits by status`() {
        val incidents = listOf(
            incident("a", assignee = ME, status = IncidentStatus.ASSIGNED),
            incident("b", assignee = ME, status = IncidentStatus.ACCEPTED),
            incident("c", assignee = ME, status = IncidentStatus.IN_PROGRESS),
            incident("d", assignee = ME, status = IncidentStatus.RESOLVED),
            incident("e", assignee = "someone-else", status = IncidentStatus.ASSIGNED),
            incident("f", assignee = null, status = IncidentStatus.REPORTED),
        )

        val tasks = DashboardMetrics.myTasks(incidents, ME)

        assertEquals(1, tasks.newAssigned)
        // ACCEPTED and IN_PROGRESS are both "I am working on it" to the person doing it.
        assertEquals(2, tasks.inProgress)
        assertEquals(1, tasks.completed)
    }

    @Test
    @DisplayName("a signed-out user has no tasks rather than everyone's tasks")
    fun `myTasks with null user is empty`() {
        val incidents = listOf(incident("a", assignee = ME, status = IncidentStatus.ASSIGNED))
        val tasks = DashboardMetrics.myTasks(incidents, null)

        assertEquals(0, tasks.newAssigned)
        assertEquals(0, tasks.inProgress)
        assertEquals(0, tasks.completed)
    }

    @Test
    @DisplayName("work already underway outranks a newer, higher-priority assignment")
    fun `activeAssignment prefers in-progress`() {
        val incidents = listOf(
            incident(
                "critical-but-untouched",
                assignee = ME,
                status = IncidentStatus.ASSIGNED,
                priority = IncidentPriority.CRITICAL,
            ),
            incident(
                "already-moving",
                assignee = ME,
                status = IncidentStatus.IN_PROGRESS,
                priority = IncidentPriority.LOW,
            ),
        )

        assertEquals(
            "already-moving",
            DashboardMetrics.activeAssignment(incidents, ME)?.clientId,
        )
    }

    @Test
    @DisplayName("a resolved assignment is not surfaced as active work")
    fun `activeAssignment ignores terminal states`() {
        val incidents = listOf(
            incident("done", assignee = ME, status = IncidentStatus.RESOLVED),
            incident("cancelled", assignee = ME, status = IncidentStatus.CANCELLED),
        )

        assertNull(DashboardMetrics.activeAssignment(incidents, ME))
    }

    @Test
    @DisplayName("response rate is null, not zero, before a first assignment")
    fun `responseRate is null when never assigned`() {
        val incidents = listOf(incident("a", assignee = "other"))

        assertNull(DashboardMetrics.responseRate(incidents, ME))
    }

    @Test
    fun `responseRate is resolved over assigned`() {
        val incidents = listOf(
            incident("a", assignee = ME, status = IncidentStatus.RESOLVED),
            incident("b", assignee = ME, status = IncidentStatus.RESOLVED),
            incident("c", assignee = ME, status = IncidentStatus.IN_PROGRESS),
            incident("d", assignee = ME, status = IncidentStatus.ASSIGNED),
        )

        assertEquals(50, DashboardMetrics.responseRate(incidents, ME))
    }

    // --- command counters -----------------------------------------------------

    @Test
    @DisplayName("an SOS counts as critical even when its priority band says otherwise")
    fun `criticalCount includes sos`() {
        val incidents = listOf(
            incident("a", priority = IncidentPriority.CRITICAL),
            incident("b", priority = IncidentPriority.LOW, isSos = true),
            incident("c", priority = IncidentPriority.HIGH),
        )

        assertEquals(2, DashboardMetrics.criticalCount(incidents))
    }

    @Test
    @DisplayName("a resolved incident with no assignee is not unassigned work")
    fun `unassignedCount only counts open work`() {
        val incidents = listOf(
            incident("open", assignee = null, status = IncidentStatus.REPORTED),
            incident("closed", assignee = null, status = IncidentStatus.RESOLVED),
            incident("taken", assignee = ME, status = IncidentStatus.ACCEPTED),
        )

        assertEquals(1, DashboardMetrics.unassignedCount(incidents))
    }

    @Test
    fun `failedSyncCount counts only failures`() {
        val incidents = listOf(
            incident("a", syncState = SyncState.FAILED),
            incident("b", syncState = SyncState.PENDING),
            incident("c", syncState = SyncState.SYNCED),
        )

        assertEquals(1, DashboardMetrics.failedSyncCount(incidents))
    }

    // --- charts ---------------------------------------------------------------

    @Test
    @DisplayName("trend buckets are one per day, newest last, sized to the window")
    fun `dailyCategoryTrend buckets by day`() {
        val incidents = listOf(
            incident("a", category = IncidentCategory.MEDICAL, reportedAt = now),
            incident("b", category = IncidentCategory.MEDICAL, reportedAt = now - HOUR),
            incident("c", category = IncidentCategory.MEDICAL, reportedAt = today - 2 * DAY + HOUR),
            incident("d", category = IncidentCategory.WATER, reportedAt = now),
        )

        val trend = DashboardMetrics.dailyCategoryTrend(incidents, now, days = 7)
        val medical = trend.getValue(IncidentCategory.MEDICAL)

        assertEquals(7, medical.size)
        // Today is the last bucket.
        assertEquals(2, medical.last())
        // Two days back is index 4 in a seven-day window.
        assertEquals(1, medical[4])
        assertEquals(1, trend.getValue(IncidentCategory.WATER).last())
    }

    @Test
    @DisplayName("incidents older than the window are excluded, not clamped into bucket zero")
    fun `dailyCategoryTrend drops out-of-window incidents`() {
        val incidents = listOf(
            incident("ancient", category = IncidentCategory.MEDICAL, reportedAt = today - 30 * DAY),
        )

        val trend = DashboardMetrics.dailyCategoryTrend(incidents, now, days = 7)

        // The category had nothing inside the window, so it produces no line at all rather
        // than a flat zero series that would add a legend entry for nothing.
        assertTrue(trend.isEmpty())
    }

    @Test
    fun `trendBucketStarts returns one ascending start per day`() {
        val starts = DashboardMetrics.trendBucketStarts(now, days = 7)

        assertEquals(7, starts.size)
        assertEquals(today, starts.last())
        assertTrue(starts.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `categoryBreakdown is ordered by count descending`() {
        val incidents = listOf(
            incident("a", category = IncidentCategory.WATER),
            incident("b", category = IncidentCategory.MEDICAL),
            incident("c", category = IncidentCategory.MEDICAL),
            incident("d", category = IncidentCategory.MEDICAL),
            incident("e", category = IncidentCategory.WATER),
        )

        val breakdown = DashboardMetrics.categoryBreakdown(incidents)

        assertEquals(IncidentCategory.MEDICAL to 3, breakdown.first())
        assertEquals(2, breakdown.size)
    }

    // --- fixtures -------------------------------------------------------------

    private fun incident(
        clientId: String,
        category: IncidentCategory = IncidentCategory.OTHER,
        status: IncidentStatus = IncidentStatus.REPORTED,
        priority: IncidentPriority = IncidentPriority.MEDIUM,
        assignee: String? = null,
        isSos: Boolean = false,
        syncState: SyncState = SyncState.SYNCED,
        reportedAt: Long = now,
    ) = Incident(
        clientId = clientId,
        category = category,
        description = clientId,
        reporterId = "reporter",
        reportedAtEpochMillis = reportedAt,
        status = status,
        priority = priority,
        syncState = syncState,
        isSos = isSos,
        assigneeId = assignee,
    )

    private companion object {
        const val ME = "me"
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        /** Local midnight today, so bucket arithmetic matches the production zone. */
        fun localMidnight(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
