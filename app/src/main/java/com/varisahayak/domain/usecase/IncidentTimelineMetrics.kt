package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.IncidentEventKind
import com.varisahayak.domain.model.TimelineEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One measured interval of the response.
 *
 * [millis] is null when the interval has not happened yet, which is a normal state for a
 * live incident and not a failure. The UI renders that as "in progress" rather than as a
 * zero — reporting 0 ms for a resolution that has not occurred would quietly poison any
 * average computed over these.
 */
data class ResponseInterval(
    val stage: MetricStage,
    val millis: Long?,
    /** True when the interval is still running: the start happened, the end has not. */
    val inProgress: Boolean,
)

enum class MetricStage {
    /** Reported → priority assigned. */
    TRIAGE,

    /** Reported → responder assigned. */
    ASSIGNMENT,

    /** Assignment sent → accepted. */
    ACCEPTANCE,

    /** Accepted → responder arrived. */
    ARRIVAL,

    /** Reported → resolved. */
    RESOLUTION,
}

data class ResponseMetrics(
    val intervals: List<ResponseInterval>,
    /** Wall-clock age of the incident, for the header's elapsed counter. */
    val elapsedMillis: Long?,
    val isResolved: Boolean,
)

/**
 * Derives response timings from the lifecycle events.
 *
 * Computed from the audit trail rather than stored on the incident, deliberately: the
 * events are append-only and timestamped when they happened, so a metric recomputed a week
 * later gives the same answer. A denormalised `time_to_triage` column would drift the first
 * time a row was touched by anything.
 *
 * Pure and dependency-free, so it is unit-tested without a device or a database.
 */
@Singleton
class IncidentTimelineMetrics @Inject constructor() {

    fun calculate(events: List<TimelineEvent>, nowMillis: Long): ResponseMetrics {
        if (events.isEmpty()) {
            return ResponseMetrics(
                intervals = MetricStage.entries.map { ResponseInterval(it, null, false) },
                elapsedMillis = null,
                isResolved = false,
            )
        }

        val ordered = events.sortedBy { it.occurredAtEpochMillis }

        // The offline case, and the reason this is not simply "the first event": a report
        // filed in a dead spot carries its real creation time, and the sync that followed
        // minutes later must not be mistaken for when it happened. Both are recorded; the
        // earlier one is the truth for every metric below.
        val reported = ordered.firstTimeOf(
            IncidentEventKind.INCIDENT_REPORTED,
            IncidentEventKind.INCIDENT_CREATED_OFFLINE,
        ) ?: ordered.first().occurredAtEpochMillis

        val triaged = ordered.firstTimeOf(
            IncidentEventKind.PRIORITY_ASSIGNED,
            IncidentEventKind.AI_TRIAGE_COMPLETED,
            IncidentEventKind.MANUAL_PRIORITY_OVERRIDE,
        )
        val assigned = ordered.firstTimeOf(
            IncidentEventKind.RESPONDER_MATCHED,
            IncidentEventKind.ASSIGNMENT_CREATED,
            IncidentEventKind.ASSIGNMENT_SENT,
        )
        val accepted = ordered.firstTimeOf(IncidentEventKind.ASSIGNMENT_ACCEPTED)
        val arrived = ordered.firstTimeOf(
            IncidentEventKind.RESPONDER_ARRIVED,
            IncidentEventKind.INCIDENT_IN_PROGRESS,
        )
        val resolved = ordered.firstTimeOf(IncidentEventKind.INCIDENT_RESOLVED)
        val cancelled = ordered.firstTimeOf(IncidentEventKind.INCIDENT_CANCELLED)

        val closed = resolved ?: cancelled

        return ResponseMetrics(
            intervals = listOf(
                interval(MetricStage.TRIAGE, reported, triaged, closed),
                interval(MetricStage.ASSIGNMENT, reported, assigned, closed),
                interval(MetricStage.ACCEPTANCE, assigned, accepted, closed),
                interval(MetricStage.ARRIVAL, accepted, arrived, closed),
                interval(MetricStage.RESOLUTION, reported, resolved, closed),
            ),
            elapsedMillis = (closed ?: nowMillis) - reported,
            isResolved = resolved != null,
        )
    }

    /**
     * One interval, with "not yet" and "never happened" told apart.
     *
     * A stage whose start never occurred is absent, not in progress. A stage that started
     * and has not finished on a still-open incident is in progress. Once the incident is
     * closed, an unfinished stage is absent for good — an incident resolved without a
     * responder ever arriving has no arrival time, and pretending it is still counting
     * would leave a running clock on a closed incident.
     */
    private fun interval(
        stage: MetricStage,
        start: Long?,
        end: Long?,
        closedAt: Long?,
    ): ResponseInterval = when {
        start == null -> ResponseInterval(stage, null, inProgress = false)
        end != null -> ResponseInterval(stage, (end - start).coerceAtLeast(0), inProgress = false)
        closedAt != null -> ResponseInterval(stage, null, inProgress = false)
        else -> ResponseInterval(stage, null, inProgress = true)
    }

    private fun List<TimelineEvent>.firstTimeOf(vararg kinds: IncidentEventKind): Long? =
        firstOrNull { it.type in kinds }?.occurredAtEpochMillis
}
