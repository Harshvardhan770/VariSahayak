package com.varisahayak.domain.usecase

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.QrToken
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.QrRepository
import javax.inject.Inject

/**
 * Raises a help request for a Varkari who has no smartphone.
 *
 * Three properties make this correct rather than merely working:
 *
 * 1. **It reuses the ordinary incident pipeline.** The record goes through the same
 *    offline-first path and the same prioritisation as any other report. There is no
 *    parallel SOS Bridge pipeline to drift out of sync with the main one.
 * 2. **It never blocks on the network.** The incident is created locally first; the audit
 *    record is queued afterwards and its failure cannot undo the request. Somebody
 *    standing in front of a volunteer needing help must not depend on a working mast.
 * 3. **It carries no personal data.** Only the opaque token is stored. Whatever the token
 *    refers to is resolved server-side for whoever is authorised to see it.
 */
class CreateSosBridgeIncidentUseCase @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val qrRepository: QrRepository,
) {

    suspend operator fun invoke(
        token: QrToken,
        description: String,
        location: GeoPoint?,
        category: IncidentCategory = IncidentCategory.OTHER,
    ): Outcome<Incident> {
        if (description.isBlank()) {
            return Outcome.Failure(
                AppError.Validation(field = "description", message = "Describe what is needed"),
            )
        }

        val created = incidentRepository.createIncident(
            category = category,
            description = description,
            location = location,
            photoLocalPath = null,
            // Deliberately null. An SOS Bridge request carries the token and nothing that
            // identifies the person it is raised for.
            affectedPersonNote = null,
            // Marks this as an explicit SOS, which pins it to the critical band in
            // PriorityEngine regardless of category or any AI suggestion.
            isSos = true,
            sosBridgeToken = token.value,
        )

        // The audit row is best-effort and intentionally cannot fail the request. It is
        // queued in the outbox, so a failure here means "not yet recorded", not "lost".
        if (created is Outcome.Success) {
            qrRepository.recordResolution(token, created.data.clientId)
        }

        return created
    }
}
