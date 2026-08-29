package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.Capabilities
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentStateMachine
import com.varisahayak.domain.model.IncidentStatus

/**
 * Which status transitions this user may actually perform on this incident.
 *
 * Three independent filters, all of which must pass:
 *
 *  1. **The state machine** — is the transition legal at all? A resolved incident cannot
 *     go back to reported no matter who is asking.
 *  2. **The role's capabilities** — may this kind of user do this kind of thing? Only
 *     command escalates; only a responder accepts.
 *  3. **The relationship to this specific incident** — am I its reporter, its assignee,
 *     or neither? A responder may accept *their* assignment, not somebody else's.
 *
 * Filter 3 is the one that is easy to forget and the one that matters most: without it,
 * every responder in the area would be offered an Accept button on every open incident,
 * and the resulting write would be refused by the `"Assignees update their incidents"`
 * policy. This mirrors that policy so the button is never offered in the first place.
 *
 * Pure and dependency-free, so it is unit-testable without Android, Room, or a network.
 */
object IncidentActionPolicy {

    /** How the acting user relates to the incident in front of them. */
    enum class Relationship { REPORTER, ASSIGNEE, BYSTANDER }

    fun relationshipOf(incident: Incident, userId: String?): Relationship = when {
        userId == null -> Relationship.BYSTANDER
        incident.assigneeId == userId -> Relationship.ASSIGNEE
        incident.reporterId == userId -> Relationship.REPORTER
        else -> Relationship.BYSTANDER
    }

    /**
     * Returns the transitions to offer, in the state machine's own order so the primary
     * action stays in a stable position between renders.
     */
    fun allowedActions(
        incident: Incident,
        capabilities: Capabilities,
        userId: String?,
    ): List<IncidentStatus> {
        val relationship = relationshipOf(incident, userId)

        return IncidentStateMachine.allowedTransitions(incident.status)
            .filter { target -> permits(target, capabilities, relationship) }
    }

    private fun permits(
        target: IncidentStatus,
        capabilities: Capabilities,
        relationship: Relationship,
    ): Boolean = when (target) {
        // Accepting is answering an assignment addressed to you. Nobody else's to accept.
        IncidentStatus.ACCEPTED ->
            capabilities.canAcceptAssignment && relationship == Relationship.ASSIGNEE

        // Handing work back. The assignee asks for reassignment; command can also force
        // it when a responder has gone silent.
        IncidentStatus.REASSIGNMENT_REQUIRED ->
            (capabilities.canAcceptAssignment && relationship == Relationship.ASSIGNEE) ||
                capabilities.canAssignToOthers

        // Doing the work and finishing it: the assignee's, or command's on their behalf.
        IncidentStatus.IN_PROGRESS,
        IncidentStatus.RESOLVED,
        ->
            (capabilities.canProgressOwnIncident && relationship == Relationship.ASSIGNEE) ||
                capabilities.canAssignToOthers

        // Triage and dispatch are command decisions.
        IncidentStatus.TRIAGED,
        IncidentStatus.ASSIGNED,
        -> capabilities.canAssignToOthers

        IncidentStatus.ESCALATED -> capabilities.canEscalate

        // A reporter may withdraw what they filed while nobody has picked it up; command
        // may cancel anything. Withdrawing after a responder is en route is a status the
        // responder owns, not the reporter.
        IncidentStatus.CANCELLED ->
            capabilities.canAssignToOthers ||
                (relationship == Relationship.REPORTER && capabilities.canProgressOwnIncident)

        // Not user-initiated: the server sets REPORTED on acceptance, and PENDING_SYNC is
        // a local-only marker the sync layer owns.
        IncidentStatus.REPORTED,
        IncidentStatus.PENDING_SYNC,
        -> false
    }
}
