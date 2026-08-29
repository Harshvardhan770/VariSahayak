package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.Capabilities
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.SyncState
import com.varisahayak.domain.model.UserRole
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The client half of the authorisation story.
 *
 * Every rule asserted here is enforced again by RLS, so a failure is not a security hole —
 * it is the UI offering a button that Postgres will refuse, which teaches a volunteer in
 * the field that the app is unreliable. These tests exist to keep the two in step.
 */
class IncidentActionPolicyTest {

    private val volunteer = Capabilities.of(UserRole.VOLUNTEER)
    private val responder = Capabilities.of(UserRole.MEDICAL_RESPONDER)
    private val command = Capabilities.of(UserRole.ORGANISER)

    private val reporterId = "user-reporter"
    private val assigneeId = "user-assignee"
    private val strangerId = "user-stranger"

    private fun incident(
        status: IncidentStatus,
        assignee: String? = assigneeId,
    ) = Incident(
        clientId = "incident-1",
        category = IncidentCategory.MEDICAL,
        description = "Collapsed pilgrim near the water point",
        reporterId = reporterId,
        reportedAtEpochMillis = 1_700_000_000_000,
        status = status,
        priority = IncidentPriority.HIGH,
        syncState = SyncState.SYNCED,
        assigneeId = assignee,
    )

    private fun actions(
        status: IncidentStatus,
        capabilities: Capabilities,
        userId: String?,
        assignee: String? = assigneeId,
    ) = IncidentActionPolicy.allowedActions(incident(status, assignee), capabilities, userId)

    @Nested
    @DisplayName("a volunteer is offered dispatch actions on nobody's incident")
    inner class VolunteerScope {

        @ParameterizedTest
        @EnumSource(IncidentStatus::class)
        fun `never escalates`(status: IncidentStatus) {
            assertFalse(IncidentStatus.ESCALATED in actions(status, volunteer, reporterId))
        }

        @ParameterizedTest
        @EnumSource(IncidentStatus::class)
        fun `never assigns`(status: IncidentStatus) {
            assertFalse(IncidentStatus.ASSIGNED in actions(status, volunteer, reporterId))
        }

        @Test
        fun `cannot accept an assignment even on an incident they reported`() {
            val available = actions(IncidentStatus.ASSIGNED, volunteer, reporterId)

            assertFalse(IncidentStatus.ACCEPTED in available)
        }

        @Test
        fun `may cancel their own report while it is still unclaimed`() {
            val available = actions(IncidentStatus.REPORTED, volunteer, reporterId, assignee = null)

            assertTrue(IncidentStatus.CANCELLED in available)
        }

        @Test
        fun `may not cancel somebody else's report`() {
            val available = actions(IncidentStatus.REPORTED, volunteer, strangerId, assignee = null)

            assertFalse(IncidentStatus.CANCELLED in available)
        }
    }

    @Nested
    @DisplayName("a responder acts on their own assignment and nobody else's")
    inner class ResponderScope {

        @Test
        fun `accepts an assignment addressed to them`() {
            assertTrue(
                IncidentStatus.ACCEPTED in actions(IncidentStatus.ASSIGNED, responder, assigneeId),
            )
        }

        @Test
        fun `is not offered accept on an assignment addressed to somebody else`() {
            // The rule that is easiest to miss: without it, every responder in the area
            // sees Accept on every open incident and the write is refused by
            // "Assignees update their incidents".
            assertFalse(
                IncidentStatus.ACCEPTED in actions(IncidentStatus.ASSIGNED, responder, strangerId),
            )
        }

        @Test
        fun `progresses work they have accepted`() {
            assertTrue(
                IncidentStatus.IN_PROGRESS in
                    actions(IncidentStatus.ACCEPTED, responder, assigneeId),
            )
        }

        @Test
        fun `cannot progress somebody else's accepted work`() {
            assertFalse(
                IncidentStatus.IN_PROGRESS in
                    actions(IncidentStatus.ACCEPTED, responder, strangerId),
            )
        }

        @Test
        fun `may hand their own assignment back`() {
            assertTrue(
                IncidentStatus.REASSIGNMENT_REQUIRED in
                    actions(IncidentStatus.ACCEPTED, responder, assigneeId),
            )
        }

        @ParameterizedTest
        @EnumSource(IncidentStatus::class)
        fun `never escalates`(status: IncidentStatus) {
            assertFalse(IncidentStatus.ESCALATED in actions(status, responder, assigneeId))
        }
    }

    @Nested
    @DisplayName("command dispatches without being the assignee")
    inner class CommandScope {

        @Test
        fun `escalates an open incident`() {
            assertTrue(
                IncidentStatus.ESCALATED in actions(IncidentStatus.REPORTED, command, strangerId),
            )
        }

        @Test
        fun `assigns an incident it did not report`() {
            assertTrue(
                IncidentStatus.ASSIGNED in
                    actions(IncidentStatus.REPORTED, command, strangerId, assignee = null),
            )
        }

        @Test
        fun `forces reassignment when a responder has gone silent`() {
            assertTrue(
                IncidentStatus.REASSIGNMENT_REQUIRED in
                    actions(IncidentStatus.ACCEPTED, command, strangerId),
            )
        }

        @Test
        fun `is not offered accept, because dispatching is not attending`() {
            assertFalse(
                IncidentStatus.ACCEPTED in actions(IncidentStatus.ASSIGNED, command, strangerId),
            )
        }
    }

    @Nested
    @DisplayName("rules that hold for every role")
    inner class Invariants {

        @ParameterizedTest
        @EnumSource(UserRole::class)
        fun `a resolved incident offers nothing to anyone`(role: UserRole) {
            val available = actions(IncidentStatus.RESOLVED, Capabilities.of(role), assigneeId)

            assertTrue(available.isEmpty(), "RESOLVED is terminal but offered $available to $role")
        }

        @ParameterizedTest
        @EnumSource(UserRole::class)
        fun `a cancelled incident offers nothing to anyone`(role: UserRole) {
            val available = actions(IncidentStatus.CANCELLED, Capabilities.of(role), assigneeId)

            assertTrue(available.isEmpty(), "CANCELLED is terminal but offered $available to $role")
        }

        @ParameterizedTest
        @EnumSource(UserRole::class)
        fun `nobody is offered PENDING_SYNC, which the sync layer owns`(role: UserRole) {
            IncidentStatus.entries.forEach { status ->
                assertFalse(
                    IncidentStatus.PENDING_SYNC in
                        actions(status, Capabilities.of(role), assigneeId),
                )
            }
        }

        @ParameterizedTest
        @EnumSource(UserRole::class)
        fun `nobody is offered REPORTED, which the server sets on acceptance`(role: UserRole) {
            IncidentStatus.entries.forEach { status ->
                assertFalse(
                    IncidentStatus.REPORTED in actions(status, Capabilities.of(role), assigneeId),
                )
            }
        }

        @Test
        fun `an unresolved profile is offered nothing at all`() {
            // Guards the cold-start flash: no cached profile must never briefly render
            // command actions before the real role arrives.
            IncidentStatus.entries.forEach { status ->
                assertTrue(actions(status, Capabilities.NONE, assigneeId).isEmpty())
            }
        }

        @Test
        fun `a signed-out user is offered nothing`() {
            assertTrue(actions(IncidentStatus.ASSIGNED, responder, userId = null).isEmpty())
        }
    }
}
