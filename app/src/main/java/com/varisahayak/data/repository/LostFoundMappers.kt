package com.varisahayak.data.repository

import com.varisahayak.data.local.entity.CustodyEntity
import com.varisahayak.data.local.entity.LostFoundEntity
import com.varisahayak.data.local.entity.LostFoundMatchEntity
import com.varisahayak.data.remote.dto.CustodyDto
import com.varisahayak.data.remote.dto.LostFoundMatchDto
import com.varisahayak.data.remote.dto.LostFoundReportDto
import com.varisahayak.domain.model.CustodyRecord
import com.varisahayak.domain.model.FaceMatchStatus
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.model.LostFoundMatch
import com.varisahayak.domain.model.LostFoundReport
import com.varisahayak.domain.model.LostFoundStatus
import com.varisahayak.domain.model.LostFoundSubjectType
import com.varisahayak.domain.model.MatchConfidence
import com.varisahayak.domain.model.MatchScore
import com.varisahayak.domain.model.MatchSignal
import com.varisahayak.domain.model.MatchStatus
import com.varisahayak.domain.model.SignalKind
import com.varisahayak.domain.model.SignalStrength
import com.varisahayak.domain.model.SyncState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Conversions between the three representations of a Lost & Found record: the domain
 * model, the Room row, and the wire shape.
 *
 * Kept in one file rather than scattered across the repositories so a field added to the
 * domain model has exactly one place where its absence shows up as a compile error.
 */

/** Lenient, because a server that grows a field must not break an older client. */
internal val lostFoundJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// --- reports -------------------------------------------------------------------------------

fun LostFoundEntity.toDomain(): LostFoundReport = LostFoundReport(
    clientId = clientId,
    serverId = serverId,
    incidentClientId = incidentClientId,
    kind = LostFoundKind.fromWire(kind),
    subjectType = LostFoundSubjectType.fromWire(subjectType),
    title = title,
    description = description,
    personName = personName,
    approximateAge = approximateAge,
    gender = gender,
    approximateHeightCm = approximateHeightCm,
    clothingDescription = clothingDescription,
    physicalDescription = physicalDescription,
    language = language,
    condition = condition,
    additionalNotes = additionalNotes,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    qrLocationToken = qrLocationToken,
    qrLocationName = qrLocationName,
    deviceLocation = pointOrNull(deviceLatitude, deviceLongitude),
    lastKnownLocation = pointOrNull(lastKnownLatitude, lastKnownLongitude),
    routeSegment = routeSegment,
    routeSequence = routeSequence,
    occurredAtEpochMillis = occurredAtEpochMillis,
    reportedAtEpochMillis = reportedAtEpochMillis,
    photoLocalPath = photoLocalPath,
    photoRemotePath = photoRemotePath,
    faceMatchStatus = FaceMatchStatus.fromWire(faceMatchStatus),
    custodianUserId = custodianUserId,
    custodianName = custodianName,
    custodianContact = custodianContact,
    status = LostFoundStatus.fromWire(status),
    reportedBy = reportedBy,
    syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING),
)

fun LostFoundReport.toEntity(): LostFoundEntity = LostFoundEntity(
    clientId = clientId,
    serverId = serverId,
    incidentClientId = incidentClientId,
    kind = kind.wireName,
    subjectType = subjectType.wireName,
    title = title,
    description = description,
    personName = personName,
    approximateAge = approximateAge,
    gender = gender,
    approximateHeightCm = approximateHeightCm,
    clothingDescription = clothingDescription,
    physicalDescription = physicalDescription,
    language = language,
    condition = condition,
    additionalNotes = additionalNotes,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    qrLocationToken = qrLocationToken,
    qrLocationName = qrLocationName,
    deviceLatitude = deviceLocation?.latitude,
    deviceLongitude = deviceLocation?.longitude,
    lastKnownLatitude = lastKnownLocation?.latitude,
    lastKnownLongitude = lastKnownLocation?.longitude,
    routeSegment = routeSegment,
    routeSequence = routeSequence,
    occurredAtEpochMillis = occurredAtEpochMillis,
    reportedAtEpochMillis = reportedAtEpochMillis,
    photoLocalPath = photoLocalPath,
    photoRemotePath = photoRemotePath,
    faceMatchStatus = faceMatchStatus.wireName,
    custodianUserId = custodianUserId,
    custodianName = custodianName,
    custodianContact = custodianContact,
    status = status.wireName,
    reportedBy = reportedBy,
    syncState = syncState.name,
)

/**
 * Upload shape.
 *
 * `face_match_status` is sent as PENDING when a photo exists and is otherwise left alone:
 * the server owns that field, because only the server has run the CV service. A client
 * claiming READY would be claiming an embedding it cannot produce.
 */
fun LostFoundEntity.toUploadDto(): LostFoundReportDto = LostFoundReportDto(
    clientId = clientId,
    incidentClientId = incidentClientId,
    kind = kind,
    subjectType = subjectType,
    title = title,
    description = description,
    personName = personName,
    approximateAge = approximateAge,
    gender = gender,
    approximateHeightCm = approximateHeightCm,
    clothingDescription = clothingDescription,
    physicalDescription = physicalDescription,
    language = language,
    condition = condition,
    additionalNotes = additionalNotes,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    qrLocationToken = qrLocationToken,
    deviceLatitude = deviceLatitude,
    deviceLongitude = deviceLongitude,
    lastKnownLatitude = lastKnownLatitude,
    lastKnownLongitude = lastKnownLongitude,
    routeSegment = routeSegment,
    routeSequence = routeSequence,
    occurredAt = occurredAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() },
    reportedAt = Instant.ofEpochMilli(reportedAtEpochMillis).toString(),
    photoPath = photoRemotePath,
    faceMatchStatus = if (photoRemotePath != null || photoLocalPath != null) {
        FaceMatchStatus.PENDING.wireName
    } else {
        FaceMatchStatus.NOT_APPLICABLE.wireName
    },
    custodianUserId = custodianUserId,
    custodianName = custodianName,
    custodianContact = custodianContact,
    status = status,
    reportedBy = reportedBy,
)

fun LostFoundReportDto.toEntity(fetchedAt: Long): LostFoundEntity = LostFoundEntity(
    clientId = clientId,
    serverId = id,
    incidentClientId = incidentClientId,
    kind = kind,
    subjectType = subjectType,
    title = title,
    description = description,
    personName = personName,
    approximateAge = approximateAge,
    gender = gender,
    approximateHeightCm = approximateHeightCm,
    clothingDescription = clothingDescription,
    physicalDescription = physicalDescription,
    language = language,
    condition = condition,
    additionalNotes = additionalNotes,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    qrLocationToken = qrLocationToken,
    qrLocationName = null,
    deviceLatitude = deviceLatitude,
    deviceLongitude = deviceLongitude,
    lastKnownLatitude = lastKnownLatitude,
    lastKnownLongitude = lastKnownLongitude,
    routeSegment = routeSegment,
    routeSequence = routeSequence,
    occurredAtEpochMillis = occurredAt?.toEpochMillisOrNull(),
    reportedAtEpochMillis = reportedAt.toEpochMillisOrNull() ?: fetchedAt,
    photoLocalPath = null,
    photoRemotePath = photoPath,
    faceMatchStatus = faceMatchStatus,
    custodianUserId = custodianUserId,
    custodianName = custodianName,
    custodianContact = custodianContact,
    status = status,
    reportedBy = reportedBy,
    syncState = SyncState.SYNCED.name,
)

// --- custody -------------------------------------------------------------------------------

fun CustodyEntity.toDomain(): CustodyRecord = CustodyRecord(
    clientId = clientId,
    reportClientId = reportClientId,
    custodianUserId = custodianUserId,
    custodianName = custodianName,
    helpPointName = helpPointName,
    qrLocationToken = qrLocationToken,
    location = pointOrNull(latitude, longitude),
    fromEpochMillis = fromEpochMillis,
    untilEpochMillis = untilEpochMillis,
    handoverNote = handoverNote,
    syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING),
)

fun CustodyEntity.toDto(): CustodyDto = CustodyDto(
    clientId = clientId,
    reportClientId = reportClientId,
    custodianUserId = custodianUserId,
    custodianName = custodianName,
    helpPointName = helpPointName,
    qrLocationToken = qrLocationToken,
    latitude = latitude,
    longitude = longitude,
    fromAt = Instant.ofEpochMilli(fromEpochMillis).toString(),
    untilAt = untilEpochMillis?.let { Instant.ofEpochMilli(it).toString() },
    handoverNote = handoverNote,
)

// --- matches -------------------------------------------------------------------------------

/**
 * Persisted form of a match signal.
 *
 * Serialised rather than recomputed so the explanation a volunteer reviews is exactly the
 * one that was generated when the candidate was raised. Recomputing on open would let the
 * displayed reasoning drift from the reasoning that triggered the notification.
 */
@Serializable
private data class StoredSignal(
    @SerialName("kind") val kind: String,
    @SerialName("strength") val strength: String,
    @SerialName("value") val value: Double,
    @SerialName("explanation") val explanation: String,
)

fun List<MatchSignal>.toJson(): String = lostFoundJson.encodeToString(
    map { StoredSignal(it.kind.name, it.strength.name, it.value, it.explanation) },
)

fun String.toMatchSignals(): List<MatchSignal> = runCatching {
    lostFoundJson.decodeFromString<List<StoredSignal>>(this).map { stored ->
        MatchSignal(
            kind = SignalKind.entries.firstOrNull { it.name == stored.kind } ?: SignalKind.NAME,
            strength = SignalStrength.entries.firstOrNull { it.name == stored.strength }
                ?: SignalStrength.NO_SIGNAL,
            value = stored.value,
            explanation = stored.explanation,
        )
    }
    // A match whose explanation cannot be parsed is still a match worth reviewing; it just
    // shows no reasoning. Losing the candidate entirely would be far worse.
}.getOrDefault(emptyList())

fun LostFoundMatchEntity.toDomain(): LostFoundMatch = LostFoundMatch(
    clientId = clientId,
    serverId = serverId,
    lostReportClientId = lostReportClientId,
    foundReportClientId = foundReportClientId,
    score = MatchScore(
        overall = overallScore,
        confidence = runCatching { MatchConfidence.valueOf(confidence) }
            .getOrDefault(MatchConfidence.LOW),
        signals = signalsJson.toMatchSignals(),
    ),
    status = MatchStatus.fromWire(status),
    createdAtEpochMillis = createdAtEpochMillis,
    reviewedBy = reviewedBy,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    reviewNote = reviewNote,
    syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING),
)

fun LostFoundMatchEntity.toDto(): LostFoundMatchDto = LostFoundMatchDto(
    clientId = clientId,
    lostReportClientId = lostReportClientId,
    foundReportClientId = foundReportClientId,
    overallScore = overallScore,
    confidence = confidence,
    signalsJson = signalsJson,
    status = status,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis).toString(),
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() },
    reviewNote = reviewNote,
)

fun LostFoundMatchDto.toEntity(fetchedAt: Long): LostFoundMatchEntity = LostFoundMatchEntity(
    clientId = clientId,
    serverId = id,
    lostReportClientId = lostReportClientId,
    foundReportClientId = foundReportClientId,
    overallScore = overallScore,
    confidence = confidence,
    signalsJson = signalsJson,
    status = status,
    createdAtEpochMillis = createdAt?.toEpochMillisOrNull() ?: fetchedAt,
    reviewedBy = reviewedBy,
    reviewedAtEpochMillis = reviewedAt?.toEpochMillisOrNull(),
    reviewNote = reviewNote,
    syncState = SyncState.SYNCED.name,
)

// --- shared helpers -------------------------------------------------------------------------

private fun pointOrNull(latitude: Double?, longitude: Double?): GeoPoint? =
    if (latitude != null && longitude != null) {
        GeoPoint(latitude = latitude, longitude = longitude)
    } else {
        null
    }

/** Defensive: one unparseable timestamp must not abort a whole refresh. */
private fun String.toEpochMillisOrNull(): Long? =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
