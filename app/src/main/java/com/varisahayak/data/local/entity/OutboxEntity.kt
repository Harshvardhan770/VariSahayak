package com.varisahayak.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A durable queue of writes that have not yet reached the server.
 *
 * Incidents carry their own sync state on the record itself; this table covers everything
 * else a volunteer can do offline — status changes, messages, availability toggles, QR
 * resolutions — so none of it is lost to a restart.
 *
 * [dedupeKey] is what makes replay safe: the same logical action enqueued twice collapses
 * to one row, and the server-side upsert makes a retried send a no-op.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["createdAtEpochMillis"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation: String,
    val dedupeKey: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastAttemptEpochMillis: Long? = null,
    val lastError: String? = null,
)

/** The operations that can be queued offline. */
enum class OutboxOperation {
    INCIDENT_STATUS_CHANGE,
    INCIDENT_PHOTO_UPLOAD,
    ASSIGNMENT_RESPONSE,
    AVAILABILITY_CHANGE,
    MESSAGE_SEND,
    QR_RESOLUTION,
    LOST_FOUND_REPORT,
    ;

    companion object {
        fun fromName(value: String): OutboxOperation? =
            entries.firstOrNull { it.name == value }
    }
}
