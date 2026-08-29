package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic incident prioritisation.
 *
 * This class has no network dependency and no AI dependency, and that is the point: if
 * Gemini is unreachable, rate-limited, or returns nonsense, prioritisation still works
 * exactly as specified. The AI layer feeds [AiSuggestion] into [prioritise] as an optional
 * input and can only ever raise a score.
 *
 * Rule precedence, highest first:
 *  1. An explicit SOS indicator pins the incident to CRITICAL. Nothing overrides it.
 *  2. Deterministic safety rules — medical and crowd surge bypass ordinary queues.
 *  3. Category and reporter-supplied severity produce a base score.
 *  4. An AI suggestion may raise the score. It may never lower it.
 *  5. An authorised human override wins over 2–4, but still cannot clear rule 1.
 */
@Singleton
class PriorityEngine @Inject constructor() {

    fun prioritise(input: PriorityInput): PriorityDecision {
        // Rule 1 — explicit SOS. Evaluated before anything else and not revisited.
        if (input.isSos) {
            return PriorityDecision(
                priority = IncidentPriority.CRITICAL,
                score = MAX_SCORE,
                basis = Basis.SOS_INDICATOR,
                aiWasApplied = false,
                overrideApplied = false,
            )
        }

        val baseScore = baseScoreFor(input.category, input.reportedSeverity)
        val deterministicPriority = IncidentPriority.fromScore(baseScore)

        // Rule 2 — safety categories bypass ordinary queues regardless of reported severity.
        val safetyFloor = safetyFloorFor(input.category)
        val afterSafety = maxOf(deterministicPriority, safetyFloor, compareBy { it.rank })
        val scoreAfterSafety = maxOf(baseScore, minimumScoreFor(safetyFloor))

        // Rule 4 — AI may only raise.
        val aiScore = input.aiSuggestion?.let { scoreFromAi(it) } ?: 0
        val aiApplied = aiScore > scoreAfterSafety
        val scoreAfterAi = maxOf(scoreAfterSafety, aiScore)
        val afterAi = maxOf(afterSafety, IncidentPriority.fromScore(scoreAfterAi), compareBy { it.rank })

        // Rule 5 — authorised override. Cannot reach here for an SOS incident.
        val override = input.humanOverride
        if (override != null) {
            return PriorityDecision(
                priority = override,
                score = minimumScoreFor(override),
                basis = Basis.HUMAN_OVERRIDE,
                aiWasApplied = aiApplied,
                overrideApplied = true,
            )
        }

        val basis = when {
            aiApplied -> Basis.AI_ASSISTED
            safetyFloor.rank > deterministicPriority.rank -> Basis.SAFETY_RULE
            else -> Basis.CATEGORY_SEVERITY
        }

        return PriorityDecision(
            priority = afterAi,
            score = scoreAfterAi,
            basis = basis,
            aiWasApplied = aiApplied,
            overrideApplied = false,
        )
    }

    /**
     * Categories that bypass ordinary queues on their own.
     *
     * Medical is critical because a delayed response is measured in lives. Crowd surge is
     * critical because it escalates faster than any queue can drain. Lost person is high
     * rather than critical: urgent, but not usually minutes-to-harm.
     */
    private fun safetyFloorFor(category: IncidentCategory): IncidentPriority = when (category) {
        IncidentCategory.MEDICAL -> IncidentPriority.CRITICAL
        IncidentCategory.CROWD_SURGE -> IncidentPriority.CRITICAL
        IncidentCategory.LOST_PERSON -> IncidentPriority.HIGH
        IncidentCategory.BLOCKED_ROAD -> IncidentPriority.MEDIUM
        IncidentCategory.WATER -> IncidentPriority.LOW
        IncidentCategory.SANITATION -> IncidentPriority.LOW
        IncidentCategory.OTHER -> IncidentPriority.LOW
    }

    private fun baseScoreFor(category: IncidentCategory, reportedSeverity: Int?): Int {
        val categoryWeight = when (category) {
            IncidentCategory.MEDICAL -> 70
            IncidentCategory.CROWD_SURGE -> 70
            IncidentCategory.LOST_PERSON -> 55
            IncidentCategory.BLOCKED_ROAD -> 35
            IncidentCategory.WATER -> 25
            IncidentCategory.SANITATION -> 20
            IncidentCategory.OTHER -> 15
        }
        // Severity is reporter-supplied and advisory: it shifts the score within a band
        // rather than dominating it, so a miskeyed 1 cannot bury a medical emergency.
        val severityAdjustment = when (reportedSeverity) {
            5 -> 20
            4 -> 10
            3 -> 0
            2 -> -5
            1 -> -10
            else -> 0
        }
        return (categoryWeight + severityAdjustment).coerceIn(0, MAX_SCORE)
    }

    private fun scoreFromAi(suggestion: AiSuggestion): Int {
        if (!suggestion.isUsable) return 0
        val categoryWeight = baseScoreFor(suggestion.category, null)
        val severityBoost = when (suggestion.severity) {
            5 -> 20
            4 -> 10
            else -> 0
        }
        return (categoryWeight + severityBoost).coerceIn(0, AI_CEILING)
    }

    private fun minimumScoreFor(priority: IncidentPriority): Int = when (priority) {
        IncidentPriority.CRITICAL -> 90
        IncidentPriority.HIGH -> 60
        IncidentPriority.MEDIUM -> 30
        IncidentPriority.LOW -> 0
    }

    private companion object {
        const val MAX_SCORE = 100

        /**
         * The AI cannot push an incident into CRITICAL by itself. Reaching the critical
         * band requires an explicit SOS or a deterministic safety rule — a model that
         * hallucinates "cardiac arrest" from a sanitation report must not be able to
         * scramble a medical team on its own.
         */
        const val AI_CEILING = 89
    }

    enum class Basis {
        SOS_INDICATOR,
        SAFETY_RULE,
        CATEGORY_SEVERITY,
        AI_ASSISTED,
        HUMAN_OVERRIDE,
    }
}

data class PriorityInput(
    val category: IncidentCategory,
    val isSos: Boolean = false,
    val reportedSeverity: Int? = null,
    val aiSuggestion: AiSuggestion? = null,
    val humanOverride: IncidentPriority? = null,
)

/**
 * A validated classifier result. [isUsable] is false whenever the model was unavailable or
 * its output failed server-side schema validation — the engine then behaves as though no
 * suggestion existed at all.
 */
data class AiSuggestion(
    val category: IncidentCategory,
    val severity: Int,
    val rationale: String? = null,
    val isUsable: Boolean = true,
)

data class PriorityDecision(
    val priority: IncidentPriority,
    val score: Int,
    val basis: PriorityEngine.Basis,
    val aiWasApplied: Boolean,
    val overrideApplied: Boolean,
)
