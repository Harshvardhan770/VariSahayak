package com.varisahayak.feature.dashboard

import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.SyncState

/**
 * Every number a dashboard shows, derived from the incident list.
 *
 * Pure functions over plain data, deliberately: these are the figures a coordinator makes
 * dispatch decisions from, so they are unit-testable without a device, an emulator or a
 * ViewModel. Nothing here reads a clock or a repository — the caller passes `nowMillis` in,
 * which is also what makes "today" testable at all.
 *
 * The rule for adding anything here: if it cannot be computed from records this device
 * actually holds, it does not belong in this file. The dashboards render a
 * "not connected" panel for those rather than a plausible number.
 */
object DashboardMetrics {

    private const val DAY_MILLIS = 86_400_000L

    // --- shared ---------------------------------------------------------------

    /** Incidents raised as SOS that are still open. */
    fun activeSosCount(incidents: List<Incident>): Int =
        incidents.count { it.isSos && it.status.isOpen }

    /** SOS raised since local midnight. */
    fun sosToday(incidents: List<Incident>, nowMillis: Long): Int =
        incidents.count { it.isSos && it.reportedAtEpochMillis >= startOfDay(nowMillis) }

    fun reportedToday(
        incidents: List<Incident>,
        nowMillis: Long,
        category: IncidentCategory? = null,
    ): Int = incidents.count {
        it.reportedAtEpochMillis >= startOfDay(nowMillis) &&
            (category == null || it.category == category)
    }

    fun countByStatus(
        incidents: List<Incident>,
        status: IncidentStatus,
        category: IncidentCategory? = null,
    ): Int = incidents.count {
        it.status == status && (category == null || it.category == category)
    }

    fun countByCategory(incidents: List<Incident>, vararg categories: IncidentCategory): Int =
        incidents.count { it.category in categories }

    fun failedSyncCount(incidents: List<Incident>): Int =
        incidents.count { it.syncState == SyncState.FAILED }

    fun criticalCount(incidents: List<Incident>): Int =
        incidents.count { it.priority == IncidentPriority.CRITICAL || it.isSos }

    fun unassignedCount(incidents: List<Incident>): Int =
        incidents.count { it.assigneeId == null && it.status.isOpen }

    // --- responder workload ---------------------------------------------------

    /**
     * The three counters on a responder's "my tasks" card.
     *
     * Resolved is drawn from the *whole* incident list, not the open one: a responder wants
     * credit for work they finished, and finished work is by definition not open.
     */
    fun myTasks(all: List<Incident>, userId: String?): MyTasks {
        if (userId == null) return MyTasks(0, 0, 0)
        val mine = all.filter { it.assigneeId == userId }
        return MyTasks(
            newAssigned = mine.count { it.status == IncidentStatus.ASSIGNED },
            inProgress = mine.count {
                it.status == IncidentStatus.ACCEPTED || it.status == IncidentStatus.IN_PROGRESS
            },
            completed = mine.count { it.status == IncidentStatus.RESOLVED },
        )
    }

    /**
     * The single job to surface as "active assignment".
     *
     * In-progress work outranks a fresh assignment: someone already on their way to an
     * incident should not have the card under their thumb swapped for a newer one.
     */
    fun activeAssignment(all: List<Incident>, userId: String?): Incident? {
        if (userId == null) return null
        val mine = all.filter { it.assigneeId == userId && it.status.isOpen }
        return mine
            .sortedWith(
                compareByDescending<Incident> { it.status == IncidentStatus.IN_PROGRESS }
                    .thenByDescending { it.status == IncidentStatus.ACCEPTED }
                    .thenByDescending { it.priority.rank }
                    .thenBy { it.reportedAtEpochMillis },
            )
            .firstOrNull()
    }

    /**
     * Share of this responder's incidents that reached RESOLVED.
     *
     * Null rather than 0% when they have never been assigned anything — "0%" reads as
     * failure, and someone on their first shift has not failed at anything.
     */
    fun responseRate(all: List<Incident>, userId: String?): Int? {
        if (userId == null) return null
        val mine = all.filter { it.assigneeId == userId }
        if (mine.isEmpty()) return null
        val resolved = mine.count { it.status == IncidentStatus.RESOLVED }
        return (resolved * 100.0 / mine.size).toInt()
    }

    // --- charts ---------------------------------------------------------------

    /**
     * Daily counts per category over the last [days] days, oldest first.
     *
     * Buckets are local calendar days derived from `nowMillis`, so "today" is the last
     * bucket regardless of when the function runs. Categories with no incidents in the
     * whole window are dropped — a flat zero line adds a legend entry and no information.
     */
    fun dailyCategoryTrend(
        incidents: List<Incident>,
        nowMillis: Long,
        days: Int = 7,
    ): Map<IncidentCategory, List<Int>> {
        val firstBucketStart = startOfDay(nowMillis) - (days - 1) * DAY_MILLIS

        val buckets = LinkedHashMap<IncidentCategory, MutableList<Int>>()
        incidents.forEach { incident ->
            if (incident.reportedAtEpochMillis < firstBucketStart) return@forEach
            val index = ((incident.reportedAtEpochMillis - firstBucketStart) / DAY_MILLIS).toInt()
            if (index !in 0 until days) return@forEach

            buckets.getOrPut(incident.category) { MutableList(days) { 0 } }[index]++
        }

        return buckets.filterValues { series -> series.any { it > 0 } }
    }

    /** Epoch millis at the start of each bucket in [dailyCategoryTrend], oldest first. */
    fun trendBucketStarts(nowMillis: Long, days: Int = 7): List<Long> {
        val firstBucketStart = startOfDay(nowMillis) - (days - 1) * DAY_MILLIS
        return (0 until days).map { firstBucketStart + it * DAY_MILLIS }
    }

    /** Category composition of a list, largest first, zero-count categories omitted. */
    fun categoryBreakdown(incidents: List<Incident>): List<Pair<IncidentCategory, Int>> =
        incidents.groupingBy { it.category }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /**
     * Local midnight for the day containing [epochMillis].
     *
     * Uses the JVM default zone via the millis-per-day arithmetic below rather than
     * java.time, because this file is on the minSdk 23 path where java.time needs
     * desugaring, and the offset lookup is the only thing actually required.
     */
    private fun startOfDay(epochMillis: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = epochMillis
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

/** The three counters on a responder's task card. */
data class MyTasks(
    val newAssigned: Int,
    val inProgress: Int,
    val completed: Int,
)
