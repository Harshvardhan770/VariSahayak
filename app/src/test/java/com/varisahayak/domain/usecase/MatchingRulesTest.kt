package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.ResponderAvailability
import com.varisahayak.domain.model.UserRole
import com.varisahayak.domain.usecase.MatchingRules.Candidate
import com.varisahayak.domain.usecase.MatchingRules.IncidentContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Tests the documented scoring rules from plan 06 §6.3.
 *
 * These do not test `public.match_responder` — that runs in Postgres. What they pin down
 * is the *intent*: which properties of the ranking are deliberate and must survive a
 * change to the weights. If one of these fails after someone retunes the numbers, the
 * question to ask is whether the property was meant to change, not just whether the
 * assertion needs updating.
 */
class MatchingRulesTest {

    private fun candidate(
        id: String = "responder-1",
        role: UserRole = UserRole.MEDICAL_RESPONDER,
        availability: ResponderAvailability = ResponderAvailability.AVAILABLE,
        capabilities: Set<String> = emptySet(),
        areaId: String? = null,
        organisationId: String? = null,
        distanceMetres: Double? = null,
        locationAgeMinutes: Long? = null,
        activeAssignmentCount: Int = 0,
    ) = Candidate(
        userId = id,
        role = role,
        availability = availability,
        capabilities = capabilities,
        areaId = areaId,
        organisationId = organisationId,
        distanceMetres = distanceMetres,
        locationAgeMinutes = locationAgeMinutes,
        activeAssignmentCount = activeAssignmentCount,
    )

    private val medicalIncident = IncidentContext(category = IncidentCategory.MEDICAL)

    @Nested
    @DisplayName("availability is a hard filter, not a score")
    inner class Availability {

        @ParameterizedTest
        @EnumSource(
            value = ResponderAvailability::class,
            names = ["BUSY", "OFF_SHIFT"],
        )
        fun `an unavailable responder is never eligible`(state: ResponderAvailability) {
            assertFalse(MatchingRules.isEligible(candidate(availability = state)))
        }

        @Test
        fun `a perfectly placed off-shift responder still loses to an available one`() {
            val offShift = candidate(
                id = "off-shift",
                availability = ResponderAvailability.OFF_SHIFT,
                capabilities = setOf("FIRST_AID"),
                distanceMetres = 0.0,
                locationAgeMinutes = 0,
            )
            val available = candidate(id = "available", role = UserRole.NGO_RESPONDER)

            assertEquals(
                listOf("available"),
                MatchingRules.rank(listOf(offShift, available), medicalIncident).map { it.userId },
            )
        }

        @Test
        fun `a volunteer is not a dispatch candidate`() {
            assertFalse(MatchingRules.isEligible(candidate(role = UserRole.VOLUNTEER)))
        }

        @Test
        fun `an organiser is not a dispatch candidate`() {
            assertFalse(MatchingRules.isEligible(candidate(role = UserRole.ORGANISER)))
        }

        @Test
        fun `no eligible candidate yields no match rather than a bad one`() {
            val none = listOf(candidate(availability = ResponderAvailability.OFF_SHIFT))

            assertNull(MatchingRules.best(none, medicalIncident))
        }
    }

    @Nested
    @DisplayName("role fit")
    inner class RoleFit {

        @Test
        fun `a medical incident prefers a medical responder`() {
            val medic = candidate(id = "medic", role = UserRole.MEDICAL_RESPONDER)
            val police = candidate(id = "police", role = UserRole.POLICE_RESPONDER)

            assertEquals("medic", MatchingRules.best(listOf(police, medic), medicalIncident)?.userId)
        }

        @Test
        fun `a crowd surge prefers police`() {
            val incident = IncidentContext(category = IncidentCategory.CROWD_SURGE)
            val medic = candidate(id = "medic", role = UserRole.MEDICAL_RESPONDER)
            val police = candidate(id = "police", role = UserRole.POLICE_RESPONDER)

            assertEquals("police", MatchingRules.best(listOf(medic, police), incident)?.userId)
        }

        @Test
        fun `a wrong-role responder still scores above zero`() {
            // The rule that matters: an imperfect responder must be able to win when they
            // are the only one free. A zero here would leave incidents unassigned.
            assertTrue(
                MatchingRules.roleFit(UserRole.NGO_RESPONDER, IncidentCategory.MEDICAL) > 0,
            )
        }

        @Test
        fun `the only available responder is matched even with the wrong specialism`() {
            val ngo = candidate(id = "ngo", role = UserRole.NGO_RESPONDER)

            assertEquals("ngo", MatchingRules.best(listOf(ngo), medicalIncident)?.userId)
        }
    }

    @Nested
    @DisplayName("stale location is unknown, not current")
    inner class Proximity {

        @Test
        fun `a fresh nearby fix scores proximity`() {
            val near = candidate(distanceMetres = 200.0, locationAgeMinutes = 1)

            assertTrue(MatchingRules.proximity(near, medicalIncident.copy(hasLocation = true)) > 0)
        }

        @Test
        fun `a stale fix contributes nothing`() {
            val stale = candidate(
                distanceMetres = 0.0,
                locationAgeMinutes = MatchingRules.LOCATION_STALENESS_MINUTES + 1,
            )

            assertEquals(0, MatchingRules.proximity(stale, medicalIncident.copy(hasLocation = true)))
        }

        @Test
        fun `a stale fix is not penalised below an absent one`() {
            val context = medicalIncident.copy(hasLocation = true)
            val stale = candidate(
                distanceMetres = 50_000.0,
                locationAgeMinutes = MatchingRules.LOCATION_STALENESS_MINUTES + 60,
            )
            val unknown = candidate(distanceMetres = null, locationAgeMinutes = null)

            // Both are "we do not know where they are". Neither may be worse than the
            // other, or an offline-but-present responder sinks below a distant online one.
            assertEquals(
                MatchingRules.proximity(unknown, context),
                MatchingRules.proximity(stale, context),
            )
        }

        @Test
        fun `an incident with no location scores proximity for nobody`() {
            val near = candidate(distanceMetres = 10.0, locationAgeMinutes = 0)

            assertEquals(0, MatchingRules.proximity(near, medicalIncident))
        }

        @Test
        fun `proximity never goes negative however far away`() {
            val faraway = candidate(distanceMetres = 1_000_000.0, locationAgeMinutes = 0)

            assertEquals(
                0,
                MatchingRules.proximity(faraway, medicalIncident.copy(hasLocation = true)),
            )
        }
    }

    @Nested
    @DisplayName("workload")
    inner class Workload {

        @Test
        fun `a loaded responder loses to an idle equal`() {
            val busy = candidate(id = "busy", activeAssignmentCount = 3)
            val idle = candidate(id = "idle", activeAssignmentCount = 0)

            assertEquals("idle", MatchingRules.best(listOf(busy, idle), medicalIncident)?.userId)
        }

        @Test
        fun `the workload penalty is capped`() {
            assertEquals(
                MatchingRules.MAX_WORKLOAD_PENALTY,
                MatchingRules.workloadPenalty(99),
            )
        }

        @Test
        fun `a capped-out responder can still be matched when nobody else is free`() {
            val overloaded = candidate(id = "overloaded", activeAssignmentCount = 50)

            assertEquals(
                "overloaded",
                MatchingRules.best(listOf(overloaded), medicalIncident)?.userId,
            )
        }
    }

    @Nested
    @DisplayName("determinism")
    inner class Determinism {

        @Test
        fun `identical candidates break ties by id, so an assignment is reproducible`() {
            val a = candidate(id = "aaa")
            val b = candidate(id = "bbb")

            assertEquals("aaa", MatchingRules.best(listOf(b, a), medicalIncident)?.userId)
            assertEquals("aaa", MatchingRules.best(listOf(a, b), medicalIncident)?.userId)
        }

        @Test
        fun `ranking the same input twice gives the same order`() {
            val pool = listOf(
                candidate(id = "c", activeAssignmentCount = 1),
                candidate(id = "a", role = UserRole.POLICE_RESPONDER),
                candidate(id = "b", capabilities = setOf("FIRST_AID")),
            )

            assertEquals(
                MatchingRules.rank(pool, medicalIncident).map { it.userId },
                MatchingRules.rank(pool.reversed(), medicalIncident).map { it.userId },
            )
        }
    }

    @Nested
    @DisplayName("area and organisation")
    inner class Scope {

        @Test
        fun `a responder in the incident's area outranks one outside it`() {
            val incident = IncidentContext(category = IncidentCategory.WATER, areaId = "area-1")
            val inArea = candidate(id = "in", role = UserRole.NGO_RESPONDER, areaId = "area-1")
            val outside = candidate(id = "out", role = UserRole.NGO_RESPONDER, areaId = "area-2")

            assertEquals("in", MatchingRules.best(listOf(outside, inArea), incident)?.userId)
        }

        @Test
        fun `a null incident area gives nobody an area advantage`() {
            assertEquals(0, MatchingRules.areaFit(responderAreaId = "area-1", incidentAreaId = null))
        }

        @Test
        fun `a shared organisation is worth less than the right specialism`() {
            // Deliberate ordering: who can actually help outranks who they work for.
            val incident = IncidentContext(
                category = IncidentCategory.MEDICAL,
                reporterOrganisationId = "org-1",
            )
            val sameOrgWrongRole =
                candidate(id = "same-org", role = UserRole.NGO_RESPONDER, organisationId = "org-1")
            val otherOrgRightRole =
                candidate(id = "medic", role = UserRole.MEDICAL_RESPONDER, organisationId = "org-2")

            assertEquals(
                "medic",
                MatchingRules.best(listOf(sameOrgWrongRole, otherOrgRightRole), incident)?.userId,
            )
        }
    }
}
