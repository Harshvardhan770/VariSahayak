package com.varisahayak.domain.model

/**
 * One entry in an incident's lifecycle.
 *
 * Read from the existing `incident_events` audit trail — this adds no second log. The
 * table is append-only by design: there is no UPDATE or DELETE policy on it anywhere, so
 * what a command user reads here is what actually happened, not a summary somebody could
 * have edited afterwards.
 */
data class TimelineEvent(
    val eventId: String,
    val incidentClientId: String,
    val type: IncidentEventKind,
    /** The raw stored string, kept so an unrecognised event still renders honestly. */
    val rawType: String,
    val actorId: String? = null,
    val actorName: String? = null,
    val fromValue: String? = null,
    val toValue: String? = null,
    val note: String? = null,
    val occurredAtEpochMillis: Long,
    /** False while the event exists only on this device. */
    val synced: Boolean = true,
)

/**
 * The lifecycle stages the timeline understands.
 *
 * Deliberately a superset of the twelve values `IncidentEventType` already writes rather
 * than a replacement for them: those strings are persisted in Room and in Postgres, and
 * renaming any of them would orphan every event already recorded. New kinds are additive,
 * and [fromWire] maps both the old and new spellings onto the same stage so a timeline
 * built from mixed-vintage rows reads correctly.
 */
enum class IncidentEventKind(val severity: TimelineSeverity) {
    // --- creation ---
    INCIDENT_REPORTED(TimelineSeverity.CRITICAL),
    INCIDENT_CREATED_OFFLINE(TimelineSeverity.INFO),
    INCIDENT_SYNCED(TimelineSeverity.INFO),

    // --- triage ---
    PRIORITY_ASSIGNED(TimelineSeverity.WARNING),
    PRIORITY_UPDATED(TimelineSeverity.WARNING),
    AI_TRIAGE_COMPLETED(TimelineSeverity.INFO),
    MANUAL_PRIORITY_OVERRIDE(TimelineSeverity.WARNING),

    // --- assignment ---
    MATCHING_STARTED(TimelineSeverity.INFO),
    RESPONDER_MATCHED(TimelineSeverity.ACTIVE),
    ASSIGNMENT_CREATED(TimelineSeverity.ACTIVE),
    ASSIGNMENT_SENT(TimelineSeverity.ACTIVE),
    ASSIGNMENT_ACCEPTED(TimelineSeverity.SUCCESS),
    ASSIGNMENT_REJECTED(TimelineSeverity.WARNING),
    ASSIGNMENT_FAILED(TimelineSeverity.WARNING),
    REASSIGNMENT_REQUIRED(TimelineSeverity.WARNING),

    // --- progress ---
    RESPONDER_EN_ROUTE(TimelineSeverity.ACTIVE),
    RESPONDER_ARRIVED(TimelineSeverity.ACTIVE),
    INCIDENT_IN_PROGRESS(TimelineSeverity.ACTIVE),
    INCIDENT_ESCALATED(TimelineSeverity.CRITICAL),

    // --- resolution ---
    INCIDENT_RESOLVED(TimelineSeverity.SUCCESS),
    INCIDENT_CANCELLED(TimelineSeverity.MUTED),

    // --- other ---
    SOS_TRIGGERED(TimelineSeverity.CRITICAL),
    ADMIN_NOTE_ADDED(TimelineSeverity.MUTED),
    QR_RESOLVED(TimelineSeverity.MUTED),
    STATUS_CHANGED(TimelineSeverity.INFO),
    UNKNOWN(TimelineSeverity.MUTED),
    ;

    companion object {
        /**
         * Maps a stored type string onto a stage.
         *
         * Both vocabularies are accepted. The left-hand names are what
         * `IncidentEventType` and the database triggers have always written; the
         * right-hand ones are the richer lifecycle stages. An unrecognised string becomes
         * [UNKNOWN] rather than being dropped — an audit trail that silently hides rows it
         * does not recognise is worse than one that shows them plainly.
         */
        fun fromWire(value: String?, toValue: String? = null): IncidentEventKind {
            val key = value?.trim()?.uppercase().orEmpty()

            // A generic STATUS_CHANGED carries the real stage in to_value. Resolving it
            // here is what turns "status changed" into "responder arrived" on screen.
            if (key == "STATUS_CHANGED") {
                return statusStage(toValue) ?: STATUS_CHANGED
            }

            return when (key) {
                "CREATED", "INCIDENT_REPORTED" -> INCIDENT_REPORTED
                "INCIDENT_CREATED_OFFLINE" -> INCIDENT_CREATED_OFFLINE
                "INCIDENT_SYNCED" -> INCIDENT_SYNCED
                "PRIORITY_SET", "PRIORITY_ASSIGNED" -> PRIORITY_ASSIGNED
                "PRIORITY_UPDATED" -> PRIORITY_UPDATED
                "AI_SUGGESTION_RECORDED", "AI_TRIAGE_COMPLETED" -> AI_TRIAGE_COMPLETED
                "PRIORITY_OVERRIDDEN", "MANUAL_PRIORITY_OVERRIDE" -> MANUAL_PRIORITY_OVERRIDE
                "MATCHING_STARTED" -> MATCHING_STARTED
                "RESPONDER_MATCHED" -> RESPONDER_MATCHED
                "ASSIGNED", "ASSIGNMENT_CREATED" -> ASSIGNMENT_CREATED
                "ASSIGNMENT_SENT" -> ASSIGNMENT_SENT
                "ASSIGNMENT_ACCEPTED" -> ASSIGNMENT_ACCEPTED
                "ASSIGNMENT_REJECTED" -> ASSIGNMENT_REJECTED
                "ASSIGNMENT_FAILED" -> ASSIGNMENT_FAILED
                "REASSIGNMENT_REQUESTED", "REASSIGNMENT_REQUIRED" -> REASSIGNMENT_REQUIRED
                "RESPONDER_EN_ROUTE" -> RESPONDER_EN_ROUTE
                "RESPONDER_ARRIVED" -> RESPONDER_ARRIVED
                "INCIDENT_IN_PROGRESS" -> INCIDENT_IN_PROGRESS
                "ESCALATED", "INCIDENT_ESCALATED" -> INCIDENT_ESCALATED
                "INCIDENT_RESOLVED" -> INCIDENT_RESOLVED
                "INCIDENT_CANCELLED" -> INCIDENT_CANCELLED
                "SOS_TRIGGERED" -> SOS_TRIGGERED
                "NOTE_ADDED", "ADMIN_NOTE_ADDED" -> ADMIN_NOTE_ADDED
                "QR_RESOLVED" -> QR_RESOLVED
                else -> UNKNOWN
            }
        }

        /** The stage a status transition represents, when the target status is known. */
        private fun statusStage(toValue: String?): IncidentEventKind? =
            when (toValue?.trim()?.uppercase()) {
                "TRIAGED" -> PRIORITY_ASSIGNED
                "ASSIGNED" -> ASSIGNMENT_CREATED
                "ACCEPTED" -> ASSIGNMENT_ACCEPTED
                "IN_PROGRESS" -> INCIDENT_IN_PROGRESS
                "RESOLVED" -> INCIDENT_RESOLVED
                "CANCELLED" -> INCIDENT_CANCELLED
                "ESCALATED" -> INCIDENT_ESCALATED
                "REASSIGNMENT_REQUIRED" -> REASSIGNMENT_REQUIRED
                else -> null
            }
    }
}

/** How an event should read at a glance. Drives colour, and never colour alone. */
enum class TimelineSeverity { CRITICAL, WARNING, ACTIVE, SUCCESS, INFO, MUTED }

/**
 * The stages of the state machine, for the progress rail.
 *
 * Mirrors [IncidentStatus] rather than redefining it: the rail is a *view* of the state
 * machine and must never imply a transition the domain would reject. Terminal and
 * exception states are deliberately absent from the happy path and are surfaced separately.
 */
enum class LifecycleStage(val status: IncidentStatus) {
    REPORTED(IncidentStatus.REPORTED),
    TRIAGED(IncidentStatus.TRIAGED),
    ASSIGNED(IncidentStatus.ASSIGNED),
    ACCEPTED(IncidentStatus.ACCEPTED),
    IN_PROGRESS(IncidentStatus.IN_PROGRESS),
    RESOLVED(IncidentStatus.RESOLVED),
    ;

    companion object {
        /**
         * How far along the rail an incident has reached.
         *
         * Exception states report the furthest stage they are known to have passed, so an
         * escalated incident still shows the ground it covered rather than resetting to
         * the start. The exception itself is rendered as a separate branch.
         */
        fun reachedIndex(status: IncidentStatus): Int = when (status) {
            IncidentStatus.PENDING_SYNC, IncidentStatus.REPORTED -> 0
            IncidentStatus.TRIAGED -> 1
            IncidentStatus.ASSIGNED -> 2
            IncidentStatus.ACCEPTED -> 3
            IncidentStatus.IN_PROGRESS -> 4
            IncidentStatus.RESOLVED -> 5
            // Cancelled can happen from anywhere; claiming progress would be a lie.
            IncidentStatus.CANCELLED -> 0
            // Both are mid-flight exceptions off the assignment stage.
            IncidentStatus.ESCALATED, IncidentStatus.REASSIGNMENT_REQUIRED -> 2
        }

        /** True when the incident left the happy path and the rail needs a branch shown. */
        fun isException(status: IncidentStatus): Boolean = when (status) {
            IncidentStatus.ESCALATED,
            IncidentStatus.REASSIGNMENT_REQUIRED,
            IncidentStatus.CANCELLED,
            -> true

            else -> false
        }
    }
}
