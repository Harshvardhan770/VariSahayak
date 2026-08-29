package com.varisahayak.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for Plan 07.
 *
 * Note what is absent from every one of these: there is no `face_embedding` field. The
 * embedding is written and read exclusively server-side by the CV service and the matching
 * function. A client neither supplies one — it would be trivially forgeable — nor receives
 * one, since a face vector is biometric data about a child.
 */

@Serializable
data class QrLocationDto(
    @SerialName("qr_token") val token: String,
    @SerialName("location_name") val locationName: String,
    @SerialName("description") val description: String? = null,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("route_segment") val routeSegment: String? = null,
    @SerialName("route_sequence") val routeSequence: Int? = null,
    @SerialName("location_type") val locationType: String,
    @SerialName("status") val status: String,
    @SerialName("public_page_enabled") val publicPageEnabled: Boolean = true,
    @SerialName("area_id") val areaId: String? = null,
    @SerialName("organisation_id") val organisationId: String? = null,
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
)

@Serializable
data class QrScanEventDto(
    @SerialName("qr_token") val token: String,
    @SerialName("scanned_by") val scannedBy: String,
    @SerialName("scanned_at") val scannedAt: String,
    @SerialName("source") val source: String,
    @SerialName("device_latitude") val deviceLatitude: Double? = null,
    @SerialName("device_longitude") val deviceLongitude: Double? = null,
    @SerialName("incident_client_id") val incidentClientId: String? = null,
    @SerialName("report_client_id") val reportClientId: String? = null,
)

@Serializable
data class LostFoundReportDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("id") val id: String? = null,
    @SerialName("incident_client_id") val incidentClientId: String? = null,
    @SerialName("kind") val kind: String,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String = "",

    @SerialName("person_name") val personName: String? = null,
    @SerialName("approximate_age") val approximateAge: Int? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("approximate_height_cm") val approximateHeightCm: Int? = null,
    @SerialName("clothing_description") val clothingDescription: String? = null,
    @SerialName("physical_description") val physicalDescription: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("additional_notes") val additionalNotes: String? = null,

    @SerialName("guardian_name") val guardianName: String? = null,
    @SerialName("guardian_phone") val guardianPhone: String? = null,

    @SerialName("qr_location_token") val qrLocationToken: String? = null,
    @SerialName("device_latitude") val deviceLatitude: Double? = null,
    @SerialName("device_longitude") val deviceLongitude: Double? = null,
    @SerialName("last_known_latitude") val lastKnownLatitude: Double? = null,
    @SerialName("last_known_longitude") val lastKnownLongitude: Double? = null,
    @SerialName("route_segment") val routeSegment: String? = null,
    @SerialName("route_sequence") val routeSequence: Int? = null,

    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("reported_at") val reportedAt: String,

    @SerialName("photo_path") val photoPath: String? = null,
    /** Server-owned. The client reports PENDING and the server decides the rest. */
    @SerialName("face_match_status") val faceMatchStatus: String = "NOT_APPLICABLE",

    @SerialName("custodian_user_id") val custodianUserId: String? = null,
    @SerialName("custodian_name") val custodianName: String? = null,
    @SerialName("custodian_contact") val custodianContact: String? = null,

    @SerialName("status") val status: String,
    @SerialName("reported_by") val reportedBy: String,
)

@Serializable
data class CustodyDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("report_client_id") val reportClientId: String,
    @SerialName("custodian_user_id") val custodianUserId: String,
    @SerialName("custodian_name") val custodianName: String? = null,
    @SerialName("help_point_name") val helpPointName: String? = null,
    @SerialName("qr_location_token") val qrLocationToken: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("from_at") val fromAt: String,
    @SerialName("until_at") val untilAt: String? = null,
    @SerialName("handover_note") val handoverNote: String? = null,
)

@Serializable
data class LostFoundMatchDto(
    @SerialName("client_id") val clientId: String,
    @SerialName("id") val id: String? = null,
    @SerialName("lost_report_client_id") val lostReportClientId: String,
    @SerialName("found_report_client_id") val foundReportClientId: String,
    @SerialName("overall_score") val overallScore: Double,
    @SerialName("confidence") val confidence: String,
    @SerialName("signals") val signalsJson: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("review_note") val reviewNote: String? = null,
)

/**
 * Response from the `process-face` edge function.
 *
 * Carries a *status*, never a vector. [distanceTo] is the only numeric result a client
 * ever sees, and it is a ranking indicator against a specific counterpart — not something
 * from which an embedding could be reconstructed.
 */
@Serializable
data class FaceProcessingDto(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String? = null,
    @SerialName("distances") val distanceTo: Map<String, Double> = emptyMap(),
)
