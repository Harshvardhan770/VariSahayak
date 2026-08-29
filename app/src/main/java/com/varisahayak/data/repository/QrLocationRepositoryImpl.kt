package com.varisahayak.data.repository

import android.util.Log
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.data.local.dao.QrLocationDao
import com.varisahayak.data.local.entity.QrLocationEntity
import com.varisahayak.data.remote.dto.QrLocationDto
import com.varisahayak.data.remote.dto.QrScanEventDto
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.QrLocation
import com.varisahayak.domain.model.QrLocationResolution
import com.varisahayak.domain.model.QrLocationStatus
import com.varisahayak.domain.model.QrLocationType
import com.varisahayak.domain.model.QrLocationValidator
import com.varisahayak.domain.repository.QrLocationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrLocationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val qrLocationDao: QrLocationDao,
    private val connectivity: ConnectivityObserver,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : QrLocationRepository {

    override fun observeAll(): Flow<List<QrLocation>> =
        qrLocationDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun resolve(rawPayload: String): QrLocationResolution =
        withContext(dispatchers.io) {
            when (val format = QrLocationValidator.validate(rawPayload)) {
                is QrLocationValidator.Format.NotOurs -> QrLocationResolution.NotOurs
                is QrLocationValidator.Format.Malformed -> QrLocationResolution.Malformed

                // A location sign should never carry personal data. If a badly-produced
                // batch ever does, refuse it loudly rather than storing what arrived.
                is QrLocationValidator.Format.ContainsPersonalData -> {
                    Log.w(TAG, "Refused a QR payload that appears to contain personal data")
                    QrLocationResolution.Malformed
                }

                is QrLocationValidator.Format.Valid -> resolveToken(format.token)
            }
        }

    /**
     * Cache first, then network.
     *
     * A location is a fixed physical thing whose name and coordinates do not change, so a
     * cached answer is as good as a fresh one — and it is the only answer available at a
     * water point with no signal, which is precisely where a volunteer is standing when
     * they scan.
     */
    private suspend fun resolveToken(token: String): QrLocationResolution {
        qrLocationDao.getByToken(token)?.let { cached ->
            val location = cached.toDomain()
            return if (location.usableAsReference) {
                QrLocationResolution.Resolved(location)
            } else {
                // Cached but withdrawn. Never guess: say so.
                QrLocationResolution.Unknown
            }
        }

        if (!connectivity.isCurrentlyOnline()) {
            // The token is handed back so a report can still be filed against it and
            // reconciled on sync. Nothing about getting help may depend on a mast.
            return QrLocationResolution.Offline(token)
        }

        return try {
            val dto = supabase.from("qr_locations")
                .select {
                    filter {
                        eq("qr_token", token)
                        eq("status", QrLocationStatus.ACTIVE.wireName)
                    }
                }
                .decodeSingleOrNull<QrLocationDto>()
                ?: return QrLocationResolution.Unknown

            val entity = dto.toEntity(clock.nowEpochMillis())
            qrLocationDao.upsert(entity)
            QrLocationResolution.Resolved(entity.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "Location lookup failed; continuing against the raw token", error)
            QrLocationResolution.Offline(token)
        }
    }

    override suspend fun refresh(): Outcome<Unit> = withContext(dispatchers.io) {
        try {
            // The whole active route in one call. It is a few hundred rows at most, and
            // holding all of it is what makes offline scanning work anywhere on the walk.
            val locations = supabase.from("qr_locations")
                .select {
                    filter { eq("status", QrLocationStatus.ACTIVE.wireName) }
                }
                .decodeList<QrLocationDto>()

            qrLocationDao.upsertAll(locations.map { it.toEntity(clock.nowEpochMillis()) })
            Outcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Outcome.Failure(AppError.Network(cause = error))
        }
    }

    override suspend fun recordScan(
        token: String,
        deviceLocation: GeoPoint?,
        incidentClientId: String?,
        reportClientId: String?,
    ): Outcome<Unit> = withContext(dispatchers.io) {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return@withContext Outcome.Failure(AppError.Unauthorised())

        try {
            supabase.from("qr_scan_events").insert(
                QrScanEventDto(
                    token = token,
                    scannedBy = userId,
                    scannedAt = Instant.ofEpochMilli(clock.nowEpochMillis()).toString(),
                    source = SOURCE_VOLUNTEER_APP,
                    deviceLatitude = deviceLocation?.latitude,
                    deviceLongitude = deviceLocation?.longitude,
                    incidentClientId = incidentClientId,
                    reportClientId = reportClientId,
                ),
            )
            Outcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Best-effort and deliberately non-fatal. An unrecorded scan is a gap in the
            // audit trail; a failed scan would be a volunteer unable to report an
            // emergency. The former is recoverable, the latter is not.
            Log.d(TAG, "Scan audit not recorded; will not block the workflow")
            Outcome.Failure(AppError.Network(cause = error))
        }
    }

    private companion object {
        const val TAG = "QrLocationRepository"
        const val SOURCE_VOLUNTEER_APP = "VOLUNTEER_APP"
    }
}

private fun QrLocationDto.toEntity(cachedAt: Long) = QrLocationEntity(
    token = token,
    locationName = locationName,
    description = description,
    latitude = latitude,
    longitude = longitude,
    routeSegment = routeSegment,
    routeSequence = routeSequence,
    locationType = locationType,
    status = status,
    publicPageEnabled = publicPageEnabled,
    areaId = areaId,
    organisationId = organisationId,
    lastVerifiedAtEpochMillis = lastVerifiedAt
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
    cachedAtEpochMillis = cachedAt,
)

private fun QrLocationEntity.toDomain() = QrLocation(
    token = token,
    locationName = locationName,
    description = description,
    point = GeoPoint(latitude = latitude, longitude = longitude),
    routeSegment = routeSegment,
    routeSequence = routeSequence,
    locationType = QrLocationType.fromWire(locationType),
    status = QrLocationStatus.fromWire(status),
    publicPageEnabled = publicPageEnabled,
    areaId = areaId,
    organisationId = organisationId,
    lastVerifiedAtEpochMillis = lastVerifiedAtEpochMillis,
)
