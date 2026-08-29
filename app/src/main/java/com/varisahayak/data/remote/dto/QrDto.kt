package com.varisahayak.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server response for a token lookup.
 *
 * The shape is deliberately narrow. There is no field here for a name, a phone number, or
 * a medical note, so a future change that starts returning them would be a visible schema
 * change rather than something that quietly arrives in an existing field.
 */
@Serializable
data class QrResolutionDto(
    @SerialName("token") val token: String,
    @SerialName("subject_reference") val subjectReference: String? = null,
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("organisation_id") val organisationId: String? = null,
    @SerialName("has_active_incident") val hasActiveIncident: Boolean = false,
    @SerialName("revoked") val revoked: Boolean = false,
)

@Serializable
data class QrResolutionEventDto(
    @SerialName("token") val token: String,
    @SerialName("incident_client_id") val incidentClientId: String? = null,
    @SerialName("resolved_by") val resolvedBy: String,
    @SerialName("resolved_at") val resolvedAt: String,
)

@Serializable
data class LostFoundDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("id") val id: String? = null,
    @SerialName("incident_client_id") val incidentClientId: String? = null,
    @SerialName("kind") val kind: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("last_seen_latitude") val lastSeenLatitude: Double? = null,
    @SerialName("last_seen_longitude") val lastSeenLongitude: Double? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("qr_token") val qrToken: String? = null,
    @SerialName("status") val status: String,
    @SerialName("reported_by") val reportedBy: String,
    @SerialName("reported_at") val reportedAt: String,
)
