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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOwnAvailability(): Flow<ResponderAvailability?> {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return flowOf(null)

        return responderDao.observeSelf(userId)
            .flatMapLatest { entity ->
                flowOf(entity?.availability?.let { ResponderAvailability.fromWire(it) })
            }
    }

    override suspend fun setAvailability(availability: ResponderAvailability): Outcome<Unit> =
        withContext(dispatchers.io) {
            try {
                val userId = supabase.auth.currentUserOrNull()?.id
                    ?: return@withContext Outcome.Failure(AppError.Unauthorised())

                // Local first, so the control moves the instant it is tapped and stays
                // moved. A responder toggling to OFF_SHIFT in a dead spot has still gone
                // off shift as far as this device is concerned; the push catches up.
                responderDao.setAvailability(userId, availability.wireName)

                supabase.from("responders")
                    .update({
                        set("availability", availability.wireName)
                        set("updated_at", Instant.ofEpochMilli(clock.nowEpochMillis()).toString())
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

                // last_location_at is timestamptz. Sending epoch milliseconds made
                // Postgres read 1.7e12 as a year and reject the write, which is why no
                // responder position ever reached the matcher.
                supabase.from("responders")
                    .update({
                        set("last_latitude", location.latitude)
                        set("last_longitude", location.longitude)
                        set(
                            "last_location_at",
                            Instant.ofEpochMilli(clock.nowEpochMillis()).toString(),
                        )
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
                // responder_directory, not responders: display_name, role, area, and
                // organisation live on profiles/roles. The view joins them and is
                // security_invoker, so the caller's RLS still applies.
                val dtos = supabase.from("responder_directory")
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
    // Parsed defensively: one unreadable timestamp must not abort the whole roster
    // refresh. A null here means "position unknown", which the matcher already handles.
    lastLocationAtEpochMillis = lastLocationAt
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
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
