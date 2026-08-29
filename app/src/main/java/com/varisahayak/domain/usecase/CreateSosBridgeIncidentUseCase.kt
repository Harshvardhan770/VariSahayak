package com.varisahayak.domain.usecase

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.QrLocation
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.QrLocationRepository
import javax.inject.Inject

/**
 * Raises an emergency against a fixed QR location on the route.
 *
 * The QR establishes *where*, never *who*. A volunteer scans the sign at a water point and
 * reports that somebody near it needs help; the sign contributes an exact, known
 * coordinate that a phone fix in a crowd cannot match.
 *
 * Three properties make this correct rather than merely working:
 *
 * 1. **It reuses the ordinary incident pipeline.** The record goes through the same
 *    offline-first path and the same prioritisation as any other report. There is no
 *    parallel SOS pipeline to drift out of step with the main one.
 * 2. **It never blocks on the network.** The incident is created locally first; the audit
 *    record is queued afterwards and its failure cannot undo the request.
 * 3. **It keeps the two locations apart.** The sign's coordinate and the device's fix are
 *    different facts — the person needing help is *near* the sign, not standing on it —
 *    and conflating them would send a responder to the wrong side of a crowd.
 */
class CreateSosBridgeIncidentUseCase @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val qrLocationRepository: QrLocationRepository,
) {

    suspend operator fun invoke(
        location: QrLocation?,
        rawToken: String?,
        description: String,
        deviceLocation: GeoPoint?,
        category: IncidentCategory = IncidentCategory.OTHER,
    ): Outcome<Incident> {
        if (description.isBlank()) {
            return Outcome.Failure(
                AppError.Validation(field = "description", message = "Describe what is needed"),
            )
        }

        val token = location?.token ?: rawToken

        val created = incidentRepository.createIncident(
            category = category,
            // The device's own fix is preferred when there is one: the sign is a fallback
            // reference, and a responder wants to walk to the person, not to the post.
            location = deviceLocation ?: location?.point,
            description = buildDescription(description, location),
            photoLocalPath = null,
            affectedPersonNote = null,
            // Marks this as an explicit SOS, which pins it to the critical band in
            // PriorityEngine regardless of category or any AI suggestion.
            isSos = true,
            sosBridgeToken = token,
        )

        // Best-effort audit, and deliberately unable to fail the request. A missing audit
        // row is a gap in the trail; a blocked report is somebody not getting help.
        if (created is Outcome.Success && token != null) {
            qrLocationRepository.recordScan(
                token = token,
                deviceLocation = deviceLocation,
                incidentClientId = created.data.clientId,
            )
        }

        return created
    }

    /**
     * Names the sign in the description so a responder reading the incident knows which
     * physical point to head for, even before the map loads.
     */
    private fun buildDescription(description: String, location: QrLocation?): String =
        if (location == null) {
            description.trim()
        } else {
            "${description.trim()} (near ${location.locationName})"
        }
}
