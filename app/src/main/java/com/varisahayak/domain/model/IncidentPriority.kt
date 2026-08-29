package com.varisahayak.domain.model

/**
 * Priority bands.
 *
 * [CRITICAL] is reserved for explicit SOS and deterministic safety rules. Nothing —
 * including the AI classifier — may move an incident out of it. See
 * [com.varisahayak.domain.usecase.PriorityEngine].
 */
enum class IncidentPriority(val wireName: String, val rank: Int) {
    CRITICAL("CRITICAL", 3),
    HIGH("HIGH", 2),
    MEDIUM("MEDIUM", 1),
    LOW("LOW", 0),
    ;

    companion object {
        fun fromWire(value: String?): IncidentPriority =
            entries.firstOrNull { it.wireName == value } ?: MEDIUM

        /** Scores map onto bands here and nowhere else. */
        fun fromScore(score: Int): IncidentPriority = when {
            score >= 90 -> CRITICAL
            score >= 60 -> HIGH
            score >= 30 -> MEDIUM
            else -> LOW
        }
    }
}
