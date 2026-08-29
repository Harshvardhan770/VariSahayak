package com.varisahayak.data.repository

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.data.local.dao.ResponderDao
import com.varisahayak.data.local.entity.ResponderEntity
import com.varisahayak.data.remote.dto.ResponderDto
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Responder
import com.varisahayak.domain.model.ResponderAvailability
import com.varisahayak.domain.model.UserRole
import com.varisahayak.domain.repository.ResponderRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponderRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val responderDao: ResponderDao,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : ResponderRepository {

    override fun observeAvailable(): Flow<List<Responder>> =
        responderDao.observeAvailable().map { entities -> entities.map { it.toDomain() } }

    override suspend fun setAvailability(availability: ResponderAvailability): Outcome<Unit> =
        withContext(dispatchers.io) {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                    ?: return@withContext Outcome.Failure(AppError.Unauthorised())

                supabase.from("responders")
                    .update({
                        "availability" to availability.wireName
                    }) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                Outcome.Success(Unit)
            } catch (e: Exception) {
                Outcome.Failure(AppError.Network(cause = e))
            }
        }

    override suspend fun reportLocation(location: GeoPoint): Outcome<Unit> =
        withContext(dispatchers.io) {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                    ?: return@withContext Outcome.Failure(AppError.Unauthorised())

                supabase.from("responders")
                    .update({
                        "last_latitude" to location.latitude
                        "last_longitude" to location.longitude
                        "last_location_at" to clock.nowEpochMillis()
                    }) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                Outcome.Success(Unit)
            } catch (e: Exception) {
                Outcome.Failure(AppError.Network(cause = e))
            }
        }

    override suspend fun refresh(areaId: String?): Outcome<Unit> =
        withContext(dispatchers.io) {
            try {
                val dtos = supabase.from("responders")
                    .select {
                        if (areaId != null) {
                            filter {
                                eq("area_id", areaId)
                            }
                        }
                    }
                    .decodeList<ResponderDto>()

                val entities = dtos.map { it.toEntity(clock.nowEpochMillis()) }
                responderDao.upsertAll(entities)
                Outcome.Success(Unit)
            } catch (e: Exception) {
                Outcome.Failure(AppError.Network(cause = e))
            }
        }
}

private fun ResponderDto.toEntity(cachedAt: Long): ResponderEntity = ResponderEntity(
    userId = userId,
    displayName = displayName,
    role = role,
    availability = availability,
    areaId = areaId,
    organisationId = organisationId,
    capabilitiesCsv = capabilities.joinToString(","),
    lastLatitude = lastLatitude,
    lastLongitude = lastLongitude,
    lastLocationAtEpochMillis = null,
    activeAssignmentCount = activeAssignmentCount,
    cachedAtEpochMillis = cachedAt,
)

private fun ResponderEntity.toDomain(): Responder = Responder(
    userId = userId,
    role = UserRole.fromWire(role) ?: UserRole.VOLUNTEER,
    availability = ResponderAvailability.fromWire(availability),
    areaId = areaId,
    organisationId = organisationId,
    capabilities = capabilitiesCsv.split(",").filter { it.isNotBlank() }.toSet(),
    lastKnownLocation = if (lastLatitude != null && lastLongitude != null) {
        GeoPoint(latitude = lastLatitude, longitude = lastLongitude)
    } else {
        null
    },
    lastLocationAtEpochMillis = lastLocationAtEpochMillis,
    activeAssignmentCount = activeAssignmentCount,
)
