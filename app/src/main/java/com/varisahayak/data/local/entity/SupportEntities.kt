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
    val kind: String,
    val title: String,
    val description: String,
    val lastSeenLatitude: Double? = null,
    val lastSeenLongitude: Double? = null,
    val lastSeenAtEpochMillis: Long? = null,
    val qrToken: String? = null,
    val photoLocalPath: String? = null,
    val status: String,
    val reportedBy: String,
    val reportedAtEpochMillis: Long,
    val syncState: String,
)
