package com.varisahayak.domain.repository

import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.usecase.AiSuggestion

/**
 * Optional AI assistance for classifying a report.
 *
 * Enrichment only. Everything downstream — prioritisation, matching, notification — runs
 * to completion whether or not this ever returns anything, which is why the single method
 * returns null instead of a Result: there is no failure for a caller to handle, only the
 * absence of an opinion.
 */
interface ClassificationRepository {

    /**
     * Asks the server-side classifier for a category and severity.
     *
     * Returns null whenever there is no usable suggestion: offline, the function is not
     * deployed, no Gemini key is configured, the model was unavailable, or its output
     * failed validation. Callers must treat all of those identically.
     *
     * [incidentClientId] is passed when the incident already exists, so the suggestion can
     * be recorded against it in `incident_events` as an audit row distinct from the
     * deterministic result.
     */
    suspend fun suggest(
        description: String,
        selectedCategory: IncidentCategory? = null,
        incidentClientId: String? = null,
    ): AiSuggestion?
}
