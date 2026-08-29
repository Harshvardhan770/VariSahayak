package com.varisahayak.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.SyncState

/**
 * The on-device incident record.
 *
 * [clientId] is the primary key, not the server id. The device generates it before the
 * network is ever involved, which is what lets an incident exist — and be displayed,
 * edited, and referenced — while it is still unsynced. [serverId] is filled in on
 * reconciliation.
 */
@Entity(
    tableName = "incidents",
    indices = [
        Index(value = ["serverId"], unique = true),
        Index(value = ["syncState"]),
        Index(value = ["status"]),
        Index(value = ["assigneeId"]),
        Index(value = ["reportedAtEpochMillis"]),
    ],
)
data class IncidentEntity(
    @PrimaryKey val clientId: String,
    val serverId: String? = null,
    val category: String,
    val description: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val locationIsApproximate: Boolean = false,
    val reporterId: String,
    val reportedAtEpochMillis: Long,
    val photoLocalPath: String? = null,
    val photoRemotePath: String? = null,
    val affectedPersonNote: String? = null,
    val status: String,
    val priority: String,
    val syncState: String,
    val isSos: Boolean = false,
    val sosBridgeToken: String? = null,
    val assigneeId: String? = null,
    val areaId: String? = null,
    val organisationId: String? = null,
    val lastSyncAttemptEpochMillis: Long? = null,
    val syncAttemptCount: Int = 0,
    val updatedAtEpochMillis: Long,
)

fun IncidentEntity.toDomain(): Incident = Incident(
    clientId = clientId,
    serverId = serverId,
    category = IncidentCategory.fromWire(category),
    description = description,
    location = if (latitude != null && longitude != null) {
        GeoPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = locationAccuracyMeters,
            isApproximate = locationIsApproximate,
        )
    } else {
        null
    },
    reporterId = reporterId,
    reportedAtEpochMillis = reportedAtEpochMillis,
    photoLocalPath = photoLocalPath,
    photoRemotePath = photoRemotePath,
    affectedPersonNote = affectedPersonNote,
    status = IncidentStatus.fromWire(status),
    priority = IncidentPriority.fromWire(priority),
    syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING),
    isSos = isSos,
    sosBridgeToken = sosBridgeToken,
    assigneeId = assigneeId,
    areaId = areaId,
    organisationId = organisationId,
    lastSyncAttemptEpochMillis = lastSyncAttemptEpochMillis,
    syncAttemptCount = syncAttemptCount,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun Incident.toEntity(): IncidentEntity = IncidentEntity(
    clientId = clientId,
    serverId = serverId,
    category = category.wireName,
    description = description,
    latitude = location?.latitude,
    longitude = location?.longitude,
    locationAccuracyMeters = location?.accuracyMeters,
    locationIsApproximate = location?.isApproximate ?: false,
    reporterId = reporterId,
    reportedAtEpochMillis = reportedAtEpochMillis,
    photoLocalPath = photoLocalPath,
    photoRemotePath = photoRemotePath,
    affectedPersonNote = affectedPersonNote,
    status = status.wireName,
    priority = priority.wireName,
    syncState = syncState.name,
    isSos = isSos,
    sosBridgeToken = sosBridgeToken,
    assigneeId = assigneeId,
    areaId = areaId,
    organisationId = organisationId,
    lastSyncAttemptEpochMillis = lastSyncAttemptEpochMillis,
    syncAttemptCount = syncAttemptCount,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
