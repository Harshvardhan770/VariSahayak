package com.varisahayak.domain.model

import com.varisahayak.domain.model.IncidentStatus.ACCEPTED
import com.varisahayak.domain.model.IncidentStatus.ASSIGNED
import com.varisahayak.domain.model.IncidentStatus.CANCELLED
import com.varisahayak.domain.model.IncidentStatus.ESCALATED
import com.varisahayak.domain.model.IncidentStatus.IN_PROGRESS
import com.varisahayak.domain.model.IncidentStatus.PENDING_SYNC
import com.varisahayak.domain.model.IncidentStatus.REASSIGNMENT_REQUIRED
import com.varisahayak.domain.model.IncidentStatus.REPORTED
import com.varisahayak.domain.model.IncidentStatus.RESOLVED
import com.varisahayak.domain.model.IncidentStatus.TRIAGED

/**
 * The single authority on which incident status transitions are legal.
 *
 * Every status change in the app goes through [transition]. An illegal transition is
 * rejected rather than quietly applied — a "resolved" incident that jumps back to
 * "reported" because of a stale realtime frame is a safety problem, not a UI glitch.
 */
object IncidentStateMachine {

    private val allowed: Map<IncidentStatus, Set<IncidentStatus>> = mapOf(
        // A locally captured incident becomes REPORTED once the server accepts it.
        PENDING_SYNC to setOf(REPORTED, CANCELLED),

        REPORTED to setOf(TRIAGED, ASSIGNED, ESCALATED, CANCELLED),

        // Triage may assign directly, or escalate if no suitable responder exists.
        TRIAGED to setOf(ASSIGNED, ESCALATED, CANCELLED),

        // A responder may accept, or decline into reassignment.
        ASSIGNED to setOf(ACCEPTED, REASSIGNMENT_REQUIRED, ESCALATED, CANCELLED),

        // Accepted work may start, or be handed back if the responder is blocked.
        ACCEPTED to setOf(IN_PROGRESS, REASSIGNMENT_REQUIRED, ESCALATED, CANCELLED),

        IN_PROGRESS to setOf(RESOLVED, REASSIGNMENT_REQUIRED, ESCALATED, CANCELLED),

        REASSIGNMENT_REQUIRED to setOf(ASSIGNED, ESCALATED, CANCELLED),

        // Escalation does not end an incident; command routes it onward.
        ESCALATED to setOf(ASSIGNED, IN_PROGRESS, RESOLVED, CANCELLED),

        RESOLVED to emptySet(),
        CANCELLED to emptySet(),
    )

    fun canTransition(from: IncidentStatus, to: IncidentStatus): Boolean =
        to in allowed.getValue(from)

    fun allowedTransitions(from: IncidentStatus): Set<IncidentStatus> = allowed.getValue(from)

    /**
     * Applies a transition, or explains why it was refused.
     *
     * Callers persist the result and write an audit row. A [TransitionResult.Rejected] is
     * a normal outcome to surface in the UI, not an exception to swallow.
     */
    fun transition(from: IncidentStatus, to: IncidentStatus): TransitionResult = when {
        from == to -> TransitionResult.Rejected(from, to, Reason.NO_CHANGE)
        from.isTerminal -> TransitionResult.Rejected(from, to, Reason.SOURCE_TERMINAL)
        !canTransition(from, to) -> TransitionResult.Rejected(from, to, Reason.NOT_PERMITTED)
        else -> TransitionResult.Accepted(to)
    }

    enum class Reason {
        /** Source and target are the same state. */
        NO_CHANGE,

        /** The incident is already RESOLVED or CANCELLED and cannot move again. */
        SOURCE_TERMINAL,

        /** The transition is not part of the lifecycle. */
        NOT_PERMITTED,
    }

    sealed interface TransitionResult {
        data class Accepted(val status: IncidentStatus) : TransitionResult

        data class Rejected(
            val from: IncidentStatus,
            val to: IncidentStatus,
            val reason: Reason,
        ) : TransitionResult
    }
}
