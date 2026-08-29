package com.varisahayak.domain.model

/**
 * An incident as the application reasons about it.
 *
 * [clientId] is generated on the device before anything touches the network and never
 * changes. It is what makes offline sync idempotent: the server upserts on it, so a
 * retried upload updates the same row instead of creating a second one. [serverId] is
 * null until the server has accepted the record.
 */
data class Incident(
    val clientId: String,
    val serverId: String? = null,
    val category: IncidentCategory,
    val description: String,
    val location: GeoPoint? = null,
    val reporterId: String,
    val reportedAtEpochMillis: Long,
    val photoLocalPath: String? = null,
    val photoRemotePath: String? = null,
    val affectedPersonNote: String? = null,
    val status: IncidentStatus,
    val priority: IncidentPriority,
    val syncState: SyncState,
    val isSos: Boolean = false,
    val sosBridgeToken: String? = null,
    val assigneeId: String? = null,
    val areaId: String? = null,
    val organisationId: String? = null,
    val lastSyncAttemptEpochMillis: Long? = null,
    val syncAttemptCount: Int = 0,
    val updatedAtEpochMillis: Long = reportedAtEpochMillis,
) {
    /** True when the record originated from an SOS Bridge QR scan. */
    val isSosBridge: Boolean get() = sosBridgeToken != null

    val hasLocation: Boolean get() = location != null

    val needsSync: Boolean get() = syncState.needsSync
}
