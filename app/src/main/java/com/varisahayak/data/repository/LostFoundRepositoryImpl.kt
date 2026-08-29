package com.varisahayak.data.repository

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.data.local.dao.LostFoundDao
import com.varisahayak.data.local.entity.LostFoundEntity
import com.varisahayak.data.remote.dto.LostFoundDto
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.LostFoundItem
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.model.LostFoundStatus
import com.varisahayak.domain.model.SyncState
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.LostFoundRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LostFoundRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val lostFoundDao: LostFoundDao,
    private val incidentRepository: IncidentRepository,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : LostFoundRepository {

    override fun observeAll(): Flow<List<LostFoundItem>> =
        lostFoundDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun search(query: String): Flow<List<LostFoundItem>> =
        lostFoundDao.search(query).map { entities -> entities.map { it.toDomain() } }

    override suspend fun report(
        kind: LostFoundKind,
        title: String,
        description: String,
        lastSeenLocation: GeoPoint?,
        qrToken: String?,
        photoLocalPath: String?,
    ): Outcome<LostFoundItem> = withContext(dispatchers.io) {
        if (title.isBlank()) {
            return@withContext Outcome.Failure(
                AppError.Validation(field = "title", message = "Add a short title"),
            )
        }

        val reporterId = supabase.auth.currentUserOrNull()?.id
            ?: return@withContext Outcome.Failure(AppError.Unauthorised())

        val now = clock.nowEpochMillis()

        // A missing person is an emergency, not a filing task: raise the incident first so
        // it enters matching and notification immediately, then attach the searchable
        // record to it. If the incident fails, the report is still saved locally.
        val incidentClientId = if (kind == LostFoundKind.PERSON) {
            incidentRepository.createIncident(
                category = IncidentCategory.LOST_PERSON,
                description = "$title — $description",
                location = lastSeenLocation,
                photoLocalPath = photoLocalPath,
                affectedPersonNote = null,
                isSos = false,
                sosBridgeToken = qrToken,
            ).let { outcome ->
                (outcome as? Outcome.Success)?.data?.clientId
            }
        } else {
            null
        }

        val entity = LostFoundEntity(
            clientId = UUID.randomUUID().toString(),
            incidentClientId = incidentClientId,
            kind = kind.wireName,
            title = title,
            description = description,
            lastSeenLatitude = lastSeenLocation?.latitude,
            lastSeenLongitude = lastSeenLocation?.longitude,
            lastSeenAtEpochMillis = now,
            qrToken = qrToken,
            photoLocalPath = photoLocalPath,
            status = LostFoundStatus.OPEN.wireName,
            reportedBy = reporterId,
            reportedAtEpochMillis = now,
            syncState = SyncState.PENDING.name,
        )

        lostFoundDao.upsert(entity)
        Outcome.Success(entity.toDomain())
    }

    override suspend fun syncPending(): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            lostFoundDao.getPendingSync().forEach { entity ->
                val dto = LostFoundDto(
                    clientId = entity.clientId,
                    incidentClientId = entity.incidentClientId,
                    kind = entity.kind,
                    title = entity.title,
                    description = entity.description,
                    lastSeenLatitude = entity.lastSeenLatitude,
                    lastSeenLongitude = entity.lastSeenLongitude,
                    lastSeenAt = entity.lastSeenAtEpochMillis
                        ?.let { Instant.ofEpochMilli(it).toString() },
                    qrToken = entity.qrToken,
                    status = entity.status,
                    reportedBy = entity.reportedBy,
                    reportedAt = Instant.ofEpochMilli(entity.reportedAtEpochMillis).toString(),
                )

                // Upsert on client_id, never insert: a retried send updates the same row
                // instead of filing a second report for the same missing person.
                val saved = supabase.from("lost_found_items")
                    .upsert(dto) {
                        onConflict = "client_id"
                        select()
                    }
                    .decodeSingle<LostFoundDto>()

                saved.id?.let { lostFoundDao.markSynced(entity.clientId, it) }
            }
            Outcome.Success(Unit)
        } catch (error: Exception) {
            // Records stay PENDING and are retried. Nothing is discarded.
            Outcome.Failure(AppError.Network(cause = error))
        }
    }
}

private fun LostFoundEntity.toDomain(): LostFoundItem = LostFoundItem(
    clientId = clientId,
    serverId = serverId,
    incidentClientId = incidentClientId,
    kind = LostFoundKind.fromWire(kind),
    title = title,
    description = description,
    lastSeenLocation = if (lastSeenLatitude != null && lastSeenLongitude != null) {
        GeoPoint(latitude = lastSeenLatitude, longitude = lastSeenLongitude)
    } else {
        null
    },
    lastSeenAtEpochMillis = lastSeenAtEpochMillis,
    qrToken = qrToken,
    photoLocalPath = photoLocalPath,
    status = LostFoundStatus.fromWire(status),
    reportedBy = reportedBy,
    reportedAtEpochMillis = reportedAtEpochMillis,
    syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING),
)
