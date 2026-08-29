package com.varisahayak.data.repository

import android.util.Log
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.data.remote.dto.DeviceLocationDto
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.repository.LocationRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the device's position to `public.locations`.
 *
 * An append-only track rather than a single mutable row, because "where was this volunteer
 * twenty minutes ago" is a question command asks after the fact, and a row that is
 * overwritten every minute cannot answer it. The table has no UPDATE policy for that
 * reason.
 *
 * Every user writes here, not only responders — a volunteer's position is what the
 * dashboard sorts nearby incidents by. `responders.last_*` is a separate, deliberately
 * denormalised copy that only the matcher reads, and only responders have a row for it.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : LocationRepository {

    override suspend fun record(point: GeoPoint): Outcome<Unit> = withContext(dispatchers.io) {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return@withContext Outcome.Failure(AppError.Unauthorised())

        try {
            supabase.from("locations").insert(
                DeviceLocationDto(
                    userId = userId,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracyMetres = point.accuracyMeters,
                    isApproximate = point.isApproximate,
                    // recorded_at is timestamptz. Sending epoch millis makes Postgres read
                    // 1.7e12 as a year and reject the row — the same mistake that kept
                    // responder positions out of the matcher until it was fixed there.
                    recordedAt = Instant
                        .ofEpochMilli(point.capturedAtEpochMillis ?: clock.nowEpochMillis())
                        .toString(),
                ),
            )
            Outcome.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Never surfaced. A position that does not reach the server degrades matching
            // and map accuracy; it must not interrupt whatever the volunteer is doing.
            Log.w(TAG, "Location upload failed", error)
            Outcome.Failure(AppError.Network(cause = error))
        }
    }

    private companion object {
        const val TAG = "LocationRepository"
    }
}
