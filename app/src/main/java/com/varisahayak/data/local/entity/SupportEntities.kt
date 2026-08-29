package com.varisahayak.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached responder roster, used to render assignment and availability views offline.
 * Authoritative responder state lives on the server; this is a read-through cache.
 */
@Entity(
    tableName = "responders",
    indices = [Index(value = ["areaId"]), Index(value = ["availability"])],
)
data class ResponderEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val role: String,
    val availability: String,
    val areaId: String? = null,
    val organisationId: String? = null,
    val capabilitiesCsv: String = "",
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationAtEpochMillis: Long? = null,
    val activeAssignmentCount: Int = 0,
    val cachedAtEpochMillis: Long,
)

/**
 * Append-only audit trail mirrored from the server, plus locally generated entries that
 * have not yet synced. Every status change, assignment, escalation, override, and QR
 * resolution lands here — it is what makes an operational decision reconstructable after
 * the fact.
 */
@Entity(
    tableName = "incident_events",
    indices = [Index(value = ["incidentClientId"]), Index(value = ["occurredAtEpochMillis"])],
)
data class IncidentEventEntity(
    @PrimaryKey val eventId: String,
    val incidentClientId: String,
    val incidentServerId: String? = null,
    val type: String,
    val actorId: String?,
    val fromValue: String? = null,
    val toValue: String? = null,
    val note: String? = null,
    val occurredAtEpochMillis: Long,
    val synced: Boolean = false,
)

enum class IncidentEventType {
    CREATED,
    STATUS_CHANGED,
    PRIORITY_SET,
    PRIORITY_OVERRIDDEN,
    AI_SUGGESTION_RECORDED,
    ASSIGNED,
    ASSIGNMENT_ACCEPTED,
    ASSIGNMENT_REJECTED,
    REASSIGNMENT_REQUESTED,
    ESCALATED,
    QR_RESOLVED,
    NOTE_ADDED,
}

/**
 * Route and procedure documentation, cached in full so it is readable with no network.
 * [version] drives replacement: a new copy is only swapped in once it has fully
 * downloaded, so a partial download never destroys a usable local copy.
 */
@Entity(tableName = "documents", indices = [Index(value = ["languageTag"])])
data class DocumentEntity(
    @PrimaryKey val documentId: String,
    val title: String,
    val bodyMarkdown: String,
    val languageTag: String,
    val version: Int,
    val areaId: String? = null,
    val updatedAtEpochMillis: Long,
    val cachedAtEpochMillis: Long,
)

/**
 * In-app notification record. Push delivery is best-effort; this table is the
 * authoritative list, so a dismissed or undelivered notification is still recoverable.
 */
@Entity(tableName = "notifications", indices = [Index(value = ["receivedAtEpochMillis"])])
data class NotificationEntity(
    @PrimaryKey val notificationId: String,
    val type: String,
    val title: String,
    val body: String,
    val incidentServerId: String? = null,
    val receivedAtEpochMillis: Long,
    val readAtEpochMillis: Long? = null,
)

/**
 * Locally cached messages, including ones composed offline and not yet sent (see
 * [OutboxEntity] for the send queue).
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["channelId"]), Index(value = ["sentAtEpochMillis"])],
)
data class MessageEntity(
    @PrimaryKey val clientId: String,
    val serverId: String? = null,
    val channelId: String,
    val senderId: String,
    val senderName: String? = null,
    val body: String,
    val sentAtEpochMillis: Long,
    val syncState: String,
)

/**
 * Lost & Found reports. A lost-person report is also an incident of category
 * LOST_PERSON — this row is the searchable projection over it, not a parallel system.
 */
@Entity(tableName = "lost_found_items", indices = [Index(value = ["status"])])
data class LostFoundEntity(
    @PrimaryKey val clientId: String,
    val serverId: String? = null,
    val incidentClientId: String? = null,
    /** LOST or FOUND. Both sides are first-class and independent. */
    val kind: String,
    /** PERSON or ITEM. */
    val subjectType: String,
    val title: String,
    val description: String = "",

    // Person attributes. All nullable: a parent with no photograph and half a description
    // must still be able to file a complete, matchable report.
    val personName: String? = null,
    val approximateAge: Int? = null,
    val gender: String? = null,
    val approximateHeightCm: Int? = null,
    val clothingDescription: String? = null,
    val physicalDescription: String? = null,
    val language: String? = null,
    val condition: String? = null,
    val additionalNotes: String? = null,

    val guardianName: String? = null,
    val guardianPhone: String? = null,

    // Three distinct locations, never conflated: the fixed sign, the device's fix, and
    // the best current belief a volunteer may correct by hand.
    val qrLocationToken: String? = null,
    val qrLocationName: String? = null,
    val deviceLatitude: Double? = null,
    val deviceLongitude: Double? = null,
    val lastKnownLatitude: Double? = null,
    val lastKnownLongitude: Double? = null,
    val routeSegment: String? = null,
    val routeSequence: Int? = null,

    val occurredAtEpochMillis: Long? = null,
    val reportedAtEpochMillis: Long,

    val photoLocalPath: String? = null,
    val photoRemotePath: String? = null,
    /** Result of server-side face processing. Never blocks the report. */
    val faceMatchStatus: String = "NOT_APPLICABLE",

    val custodianUserId: String? = null,
    val custodianName: String? = null,
    val custodianContact: String? = null,

    val status: String,
    val reportedBy: String,
    val syncState: String,
)

/**
 * Who is holding a found person, and where — kept as a chain rather than one mutable
 * field so a handover at a shift change does not erase who had the child before.
 */
@Entity(
    tableName = "lost_found_custody",
    indices = [Index(value = ["reportClientId"]), Index(value = ["custodianUserId"])],
)
data class CustodyEntity(
    @PrimaryKey val clientId: String,
    val reportClientId: String,
    val custodianUserId: String,
    val custodianName: String? = null,
    val helpPointName: String? = null,
    val qrLocationToken: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fromEpochMillis: Long,
    /** Null while this is the current custodian. */
    val untilEpochMillis: Long? = null,
    val handoverNote: String? = null,
    val syncState: String,
)

/**
 * A proposed Lost <-> Found pairing awaiting human review.
 *
 * The score and its explanation are cached locally so a volunteer can review a candidate
 * on the route with no connectivity.
 */
@Entity(
    tableName = "lost_found_matches",
    indices = [
        Index(value = ["lostReportClientId"]),
        Index(value = ["foundReportClientId"]),
        Index(value = ["status"]),
        Index(value = ["lostReportClientId", "foundReportClientId"], unique = true),
    ],
)
data class LostFoundMatchEntity(
    @PrimaryKey val clientId: String,
    val serverId: String? = null,
    val lostReportClientId: String,
    val foundReportClientId: String,
    val overallScore: Double,
    val confidence: String,
    /** Signals serialised as JSON so the explanation survives offline. */
    val signalsJson: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val reviewedBy: String? = null,
    val reviewedAtEpochMillis: Long? = null,
    val reviewNote: String? = null,
    val syncState: String,
)

/**
 * A cached QR location.
 *
 * Cached deliberately: a volunteer scanning a sign in a dead spot still needs the
 * location's name and coordinates to file a report against it.
 */
@Entity(tableName = "qr_locations", indices = [Index(value = ["routeSequence"])])
data class QrLocationEntity(
    @PrimaryKey val token: String,
    val locationName: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val routeSegment: String? = null,
    val routeSequence: Int? = null,
    val locationType: String,
    val status: String,
    val publicPageEnabled: Boolean = true,
    val areaId: String? = null,
    val organisationId: String? = null,
    val lastVerifiedAtEpochMillis: Long? = null,
    val cachedAtEpochMillis: Long,
)
