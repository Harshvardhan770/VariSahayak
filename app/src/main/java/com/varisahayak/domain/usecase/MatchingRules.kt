package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.ResponderAvailability
import com.varisahayak.domain.model.UserRole

/**
 * **Documentation, not the matcher.**
 *
 * The live matching engine is `public.match_responder(uuid)` in migration
 * `20260829190000_responders_and_matching.sql`. It runs in Postgres because scoring must
 * read across every responder — precisely the data RLS hides from any individual client.
 *
 * Nothing in the app calls this file. Plan 06 §6.3 is explicit: *"do not maintain two live
 * implementations."* What this does is state the scoring rules in a form that can be
 * unit-tested and reviewed, so that the intent behind the SQL is written down somewhere a
 * Kotlin developer will actually read, and so a change to the weights has a visible
 * counterpart in a diff.
 *
 * If you change the SQL, change this and its tests in the same commit. If they disagree,
 * **the SQL is right** and this is stale.
 *
 * The weights themselves are a proposed implementation decision, not a PRD requirement.
 * The PRD names the six criteria; it does not assign them numbers.
 */
object MatchingRules {

    /** A responder's position is worthless past this age — unknown, not current. */
    const val LOCATION_STALENESS_MINUTES = 15L

    /** Each active assignment costs this much score, up to [MAX_WORKLOAD_PENALTY]. */
    const val WORKLOAD_PENALTY_PER_ASSIGNMENT = 5

    const val MAX_WORKLOAD_PENALTY = 20

    /** Metres of distance that cost one point of proximity score. */
    const val METRES_PER_PROXIMITY_POINT = 200

    const val MAX_PROXIMITY_SCORE = 15

    /** The score any responder gets for being a responder at all. */
    const val BASELINE_ROLE_FIT = 10

    /**
     * Candidate state, reduced to what scoring actually looks at.
     */
    data class Candidate(
        val userId: String,
        val role: UserRole,
        val availability: ResponderAvailability,
        val capabilities: Set<String> = emptySet(),
        val areaId: String? = null,
        val organisationId: String? = null,
        val distanceMetres: Double? = null,
        /** Null when there is no fix, or the fix is older than [LOCATION_STALENESS_MINUTES]. */
        val locationAgeMinutes: Long? = null,
        val activeAssignmentCount: Int = 0,
    )

    data class IncidentContext(
        val category: IncidentCategory,
        val areaId: String? = null,
        val reporterOrganisationId: String? = null,
        val hasLocation: Boolean = false,
    )

    /**
     * Availability is a hard filter, not a term in the sum.
     *
     * Dispatching to somebody who is off shift is not a worse match — it is not a match.
     * Scoring it would let a well-placed off-shift responder outrank an available one.
     */
    fun isEligible(candidate: Candidate): Boolean =
        candidate.availability == ResponderAvailability.AVAILABLE && candidate.role.isResponder

    /** Mirrors the `order by` expression in `public.match_responder`. */
    fun score(candidate: Candidate, incident: IncidentContext): Int =
        roleFit(candidate.role, incident.category) +
            capabilityFit(candidate.capabilities, incident.category) +
            areaFit(candidate.areaId, incident.areaId) +
            organisationFit(candidate.organisationId, incident.reporterOrganisationId) +
            proximity(candidate, incident) -
            workloadPenalty(candidate.activeAssignmentCount)

    fun roleFit(role: UserRole, category: IncidentCategory): Int = when {
        category == IncidentCategory.MEDICAL && role == UserRole.MEDICAL_RESPONDER -> 40
        category == IncidentCategory.CROWD_SURGE && role == UserRole.POLICE_RESPONDER -> 40
        category == IncidentCategory.LOST_PERSON && role == UserRole.POLICE_RESPONDER -> 35
        category in setOf(IncidentCategory.WATER, IncidentCategory.SANITATION) &&
            role == UserRole.NGO_RESPONDER -> 35
        category == IncidentCategory.BLOCKED_ROAD && role == UserRole.POLICE_RESPONDER -> 30
        // Deliberately not zero. A medical case attended late by a police responder beats
        // one nobody was sent to, so an imperfect role must still be able to win when it
        // is the only candidate.
        else -> BASELINE_ROLE_FIT
    }

    fun capabilityFit(capabilities: Set<String>, category: IncidentCategory): Int = when {
        category == IncidentCategory.MEDICAL && "FIRST_AID" in capabilities -> 15
        category == IncidentCategory.LOST_PERSON && "CHILD_SAFEGUARDING" in capabilities -> 15
        else -> 0
    }

    fun areaFit(responderAreaId: String?, incidentAreaId: String?): Int =
        if (incidentAreaId != null && responderAreaId == incidentAreaId) 15 else 0

    fun organisationFit(responderOrgId: String?, reporterOrgId: String?): Int =
        if (reporterOrgId != null && responderOrgId == reporterOrgId) 10 else 0

    /**
     * Zero for an unknown or stale position — never a penalty.
     *
     * A responder whose phone has not reported in twenty minutes has not moved to the
     * wrong place; we simply do not know where they are. Penalising that would push
     * offline-but-present responders below distant online ones.
     */
    fun proximity(candidate: Candidate, incident: IncidentContext): Int {
        if (!incident.hasLocation) return 0
        val age = candidate.locationAgeMinutes ?: return 0
        if (age > LOCATION_STALENESS_MINUTES) return 0
        val distance = candidate.distanceMetres ?: return 0

        return (MAX_PROXIMITY_SCORE - (distance / METRES_PER_PROXIMITY_POINT).toInt())
            .coerceAtLeast(0)
    }

    fun workloadPenalty(activeAssignments: Int): Int =
        (activeAssignments * WORKLOAD_PENALTY_PER_ASSIGNMENT).coerceAtMost(MAX_WORKLOAD_PENALTY)

    /**
     * The whole ranking, mirroring the SQL's `order by score desc, workload asc, id asc`.
     *
     * The tie-breaks matter: the same inputs must always pick the same responder, or an
     * assignment cannot be reproduced when it is audited afterwards.
     */
    fun rank(candidates: List<Candidate>, incident: IncidentContext): List<Candidate> =
        candidates
            .filter(::isEligible)
            .sortedWith(
                compareByDescending<Candidate> { score(it, incident) }
                    .thenBy { it.activeAssignmentCount }
                    .thenBy { it.userId },
            )

    fun best(candidates: List<Candidate>, incident: IncidentContext): Candidate? =
        rank(candidates, incident).firstOrNull()
}
