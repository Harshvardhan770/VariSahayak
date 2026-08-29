package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * The safety rules, tested.
 *
 * This is the file that has to hold when everything else is on fire. Prioritisation is the
 * one part of the product that decides whether somebody is reached in two minutes or
 * twenty, and it is deliberately built so that a network outage, an absent Gemini key, or
 * a model returning confident nonsense cannot change its answer.
 *
 * Covers the checklist items from plan 06 §6.1 and plan 09 §9.4.
 */
class PriorityEngineTest {

    private val engine = PriorityEngine()

    @Nested
    @DisplayName("rule 1: an explicit SOS is critical, unconditionally")
    inner class SosRule {

        @ParameterizedTest
        @EnumSource(IncidentCategory::class)
        fun `every category goes critical when flagged as SOS`(category: IncidentCategory) {
            val decision = engine.prioritise(PriorityInput(category = category, isSos = true))

            assertEquals(IncidentPriority.CRITICAL, decision.priority)
            assertEquals(PriorityEngine.Basis.SOS_INDICATOR, decision.basis)
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 2, 3, 4, 5])
        fun `a low reported severity cannot pull an SOS down`(severity: Int) {
            val decision = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.SANITATION,
                    isSos = true,
                    reportedSeverity = severity,
                ),
            )

            assertEquals(IncidentPriority.CRITICAL, decision.priority)
        }

        @Test
        fun `an AI suggestion of the mildest possible kind cannot pull an SOS down`() {
            // Plan 09's headline test: the model says this is a minor sanitation issue,
            // the volunteer pressed the SOS button. The button wins.
            val decision = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.OTHER,
                    isSos = true,
                    aiSuggestion = AiSuggestion(
                        category = IncidentCategory.SANITATION,
                        severity = 1,
                    ),
                ),
            )

            assertEquals(IncidentPriority.CRITICAL, decision.priority)
            assertFalse(decision.aiWasApplied)
        }

        @ParameterizedTest
        @EnumSource(IncidentPriority::class)
        fun `not even an authorised override can declassify an SOS`(override: IncidentPriority) {
            val decision = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.OTHER,
                    isSos = true,
                    humanOverride = override,
                ),
            )

            assertEquals(IncidentPriority.CRITICAL, decision.priority)
            assertEquals(PriorityEngine.Basis.SOS_INDICATOR, decision.basis)
        }

        @Test
        fun `an SOS is decided with no AI involvement at all`() {
            // The property that makes offline SOS work: nothing in this path consults a
            // suggestion, so it produces the same answer in airplane mode.
            val decision = engine.prioritise(
                PriorityInput(category = IncidentCategory.MEDICAL, isSos = true),
            )

            assertFalse(decision.aiWasApplied)
            assertFalse(decision.overrideApplied)
        }
    }

    @Nested
    @DisplayName("rule 2: safety categories bypass ordinary queues")
    inner class SafetyRules {

        @Test
        fun `medical is critical without an SOS flag`() {
            val decision = engine.prioritise(PriorityInput(category = IncidentCategory.MEDICAL))

            assertEquals(IncidentPriority.CRITICAL, decision.priority)
        }

        @Test
        fun `crowd surge is critical`() {
            val decision = engine.prioritise(PriorityInput(category = IncidentCategory.CROWD_SURGE))

            assertEquals(IncidentPriority.CRITICAL, decision.priority)
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 2])
        fun `a miskeyed low severity cannot bury a medical emergency`(severity: Int) {
            val decision = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.MEDICAL,
                    reportedSeverity = severity,
                ),
            )

            assertEquals(IncidentPriority.CRITICAL, decision.priority)
        }

        @Test
        fun `a lost person is high, not critical`() {
            // Urgent, but not minutes-to-harm. Putting it in the same band as a cardiac
            // arrest would make the critical band meaningless.
            val decision = engine.prioritise(PriorityInput(category = IncidentCategory.LOST_PERSON))

            assertEquals(IncidentPriority.HIGH, decision.priority)
        }

        @Test
        fun `a sanitation report does not reach the top bands`() {
            val decision = engine.prioritise(PriorityInput(category = IncidentCategory.SANITATION))

            assertTrue(decision.priority.rank < IncidentPriority.HIGH.rank)
        }
    }

    @Nested
    @DisplayName("rule 4: an AI suggestion may raise, never lower")
    inner class AiSafetyLayer {

        @ParameterizedTest
        @EnumSource(IncidentCategory::class)
        fun `no suggestion can lower any category's deterministic result`(
            category: IncidentCategory,
        ) {
            val deterministic = engine.prioritise(PriorityInput(category = category))

            // The mildest thing the model could possibly say.
            val withMildSuggestion = engine.prioritise(
                PriorityInput(
                    category = category,
                    aiSuggestion = AiSuggestion(
                        category = IncidentCategory.OTHER,
                        severity = 1,
                    ),
                ),
            )

            assertTrue(
                withMildSuggestion.priority.rank >= deterministic.priority.rank,
                "AI lowered $category from ${deterministic.priority} to " +
                    "${withMildSuggestion.priority}",
            )
        }

        @Test
        fun `a suggestion can raise a quiet report`() {
            val without = engine.prioritise(PriorityInput(category = IncidentCategory.OTHER))
            val with = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.OTHER,
                    aiSuggestion = AiSuggestion(
                        category = IncidentCategory.MEDICAL,
                        severity = 5,
                    ),
                ),
            )

            assertTrue(with.priority.rank > without.priority.rank)
            assertTrue(with.aiWasApplied)
            assertEquals(PriorityEngine.Basis.AI_ASSISTED, with.basis)
        }

        @Test
        fun `a suggestion alone cannot reach the critical band`() {
            // The hallucination guard. Reaching CRITICAL requires an explicit SOS or a
            // deterministic safety rule — never a model's opinion on its own.
            val decision = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.SANITATION,
                    aiSuggestion = AiSuggestion(
                        category = IncidentCategory.MEDICAL,
                        severity = 5,
                    ),
                ),
            )

            assertTrue(decision.priority.rank < IncidentPriority.CRITICAL.rank)
        }

        @Test
        fun `an unusable suggestion is treated as no suggestion`() {
            // isUsable = false is what the client sets when the edge function returned
            // available:false — Gemini down, key missing, or schema validation rejected it.
            val withUnusable = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.WATER,
                    aiSuggestion = AiSuggestion(
                        category = IncidentCategory.MEDICAL,
                        severity = 5,
                        isUsable = false,
                    ),
                ),
            )
            val without = engine.prioritise(PriorityInput(category = IncidentCategory.WATER))

            assertEquals(without.priority, withUnusable.priority)
            assertEquals(without.basis, withUnusable.basis)
            assertFalse(withUnusable.aiWasApplied)
        }

        @Test
        fun `the workflow produces the same answer with Gemini entirely absent`() {
            // Plan 09's core requirement, as a test: enrichment removed changes nothing
            // about a deterministic result.
            IncidentCategory.entries.forEach { category ->
                val offline = engine.prioritise(PriorityInput(category = category))
                val online = engine.prioritise(
                    PriorityInput(
                        category = category,
                        aiSuggestion = AiSuggestion(category, severity = 3, isUsable = false),
                    ),
                )

                assertEquals(offline.priority, online.priority, "differed for $category")
            }
        }
    }

    @Nested
    @DisplayName("rule 5: an authorised override is applied and recorded")
    inner class HumanOverride {

        @Test
        fun `an override sets the priority and is flagged`() {
            val decision = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.WATER,
                    humanOverride = IncidentPriority.HIGH,
                ),
            )

            assertEquals(IncidentPriority.HIGH, decision.priority)
            assertTrue(decision.overrideApplied)
            assertEquals(PriorityEngine.Basis.HUMAN_OVERRIDE, decision.basis)
        }

        @Test
        fun `an override is distinguishable from the same priority reached by rule`() {
            // Both land on CRITICAL; the audit trail has to be able to say which was which.
            val byRule = engine.prioritise(PriorityInput(category = IncidentCategory.MEDICAL))
            val byOverride = engine.prioritise(
                PriorityInput(
                    category = IncidentCategory.WATER,
                    humanOverride = IncidentPriority.CRITICAL,
                ),
            )

            assertEquals(byRule.priority, byOverride.priority)
            assertFalse(byRule.overrideApplied)
            assertTrue(byOverride.overrideApplied)
        }
    }

    @Nested
    @DisplayName("determinism")
    inner class Determinism {

        @ParameterizedTest
        @EnumSource(IncidentCategory::class)
        fun `the same input always produces the same decision`(category: IncidentCategory) {
            val input = PriorityInput(category = category, reportedSeverity = 3)

            assertEquals(engine.prioritise(input), engine.prioritise(input))
        }

        @Test
        fun `an out-of-range reported severity does not throw`() {
            // reportedSeverity comes from a client and is advisory. Garbage must degrade to
            // "no adjustment", not to an exception in the middle of filing an incident.
            listOf(-5, 0, 6, 99, null).forEach { severity ->
                val decision = engine.prioritise(
                    PriorityInput(category = IncidentCategory.WATER, reportedSeverity = severity),
                )
                assertTrue(decision.score in 0..100)
            }
        }
    }
}
