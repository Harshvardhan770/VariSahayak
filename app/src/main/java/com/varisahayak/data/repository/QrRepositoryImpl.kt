package com.varisahayak.data.repository

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.network.ConnectivityObserver
import com.varisahayak.data.local.dao.OutboxDao
import com.varisahayak.data.local.entity.OutboxEntity
import com.varisahayak.data.local.entity.OutboxOperation
import com.varisahayak.data.remote.dto.QrResolutionDto
import com.varisahayak.domain.model.QrToken
import com.varisahayak.domain.repository.QrRepository
import com.varisahayak.domain.repository.QrResolution
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val outboxDao: OutboxDao,
    private val connectivity: ConnectivityObserver,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
    private val json: Json,
) : QrRepository {

    override suspend fun resolve(token: QrToken): Outcome<QrResolution> =
        withContext(dispatchers.io) {
            // Offline is not a failure here. The volunteer is standing in front of somebody
            // who needs help; they proceed with the raw token and resolution happens later.
            if (!connectivity.isCurrentlyOnline()) {
                return@withContext Outcome.Success(QrResolution.Offline(token))
            }

            try {
                val dto = supabase.from("qr_identifiers")
                    .select {
                        filter { eq("token", token.value) }
                        limit(1)
                    }
                    .decodeSingleOrNull<QrResolutionDto>()

                val resolution = when {
                    dto == null -> QrResolution.Unknown
                    dto.revoked -> QrResolution.Unknown
                    else -> QrResolution.Resolved(
                        token = token,
                        subjectReference = dto.subjectReference.orEmpty(),
                        areaId = dto.areaId,
                        organisationId = dto.organisationId,
                        hasActiveIncident = dto.hasActiveIncident,
                    )
                }

                Outcome.Success(resolution)
            } catch (error: Exception) {
                // A lookup failure must not stop the workflow either — fall back to the
                // raw token rather than surfacing an error the volunteer cannot act on.
                Outcome.Success(QrResolution.Offline(token))
            }
        }

    override suspend fun recordResolution(
        token: QrToken,
        incidentClientId: String?,
    ): Outcome<Unit> = withContext(dispatchers.io) {
        val actorId = supabase.auth.currentUserOrNull()?.id
            ?: return@withContext Outcome.Failure(AppError.Unauthorised())

        val payload = buildJsonObject {
            put("token", token.value)
            put("incident_client_id", incidentClientId)
            put("resolved_by", actorId)
            put("resolved_at", Instant.ofEpochMilli(clock.nowEpochMillis()).toString())
        }

        // Always queued rather than sent directly. The audit trail is required whether or
        // not there is a network, and the outbox already handles replay and dedupe.
        outboxDao.enqueue(
            OutboxEntity(
                operation = OutboxOperation.QR_RESOLUTION.name,
                dedupeKey = dedupeKey(token, incidentClientId),
                payloadJson = json.encodeToString(JsonObject.serializer(), payload),
                createdAtEpochMillis = clock.nowEpochMillis(),
            ),
        )

        Outcome.Success(Unit)
    }

    /**
     * One audit row per token-and-incident pair. Re-scanning the same tag for the same
     * incident is the same event, not a new one; scanning it for a different incident is.
     */
    private fun dedupeKey(token: QrToken, incidentClientId: String?): String =
        "qr:${token.value}:${incidentClientId ?: "none"}"
}
