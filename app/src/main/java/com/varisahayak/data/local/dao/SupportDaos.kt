package com.varisahayak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.varisahayak.data.local.entity.DocumentEntity
import com.varisahayak.data.local.entity.IncidentEventEntity
import com.varisahayak.data.local.entity.CustodyEntity
import com.varisahayak.data.local.entity.LostFoundEntity
import com.varisahayak.data.local.entity.LostFoundMatchEntity
import com.varisahayak.data.local.entity.QrLocationEntity
import com.varisahayak.data.local.entity.MessageEntity
import com.varisahayak.data.local.entity.NotificationEntity
import com.varisahayak.data.local.entity.OutboxEntity
import com.varisahayak.data.local.entity.ProfileEntity
import com.varisahayak.data.local.entity.ResponderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE userId = :userId")
    fun observe(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles LIMIT 1")
    fun observeFirst(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE userId = :userId")
    suspend fun get(userId: String): ProfileEntity?

    @Query("SELECT * FROM profiles LIMIT 1")
    suspend fun getAny(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun clear()
}

@Dao
interface OutboxDao {
    /**
     * IGNORE, not REPLACE: the unique dedupeKey means an action enqueued twice keeps its
     * original position in the queue rather than jumping to the back.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entry: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun peek(limit: Int = 50): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("UPDATE outbox SET attemptCount = attemptCount + 1, lastAttemptEpochMillis = :at, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, at: Long, error: String?)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface ResponderDao {
    @Query("SELECT * FROM responders WHERE availability = 'AVAILABLE'")
    fun observeAvailable(): Flow<List<ResponderEntity>>

    @Query("SELECT * FROM responders WHERE areaId = :areaId")
    fun observeByArea(areaId: String): Flow<List<ResponderEntity>>

    /**
     * The signed-in responder's own row.
     *
     * Read from Room rather than from the network so the availability control shows the
     * right state instantly on a cold start, and keeps showing it with no connectivity.
     */
    @Query("SELECT * FROM responders WHERE userId = :userId LIMIT 1")
    fun observeSelf(userId: String): Flow<ResponderEntity?>

    @Query("UPDATE responders SET availability = :availability WHERE userId = :userId")
    suspend fun setAvailability(userId: String, availability: String)

    @Upsert
    suspend fun upsert(responder: ResponderEntity)

    @Upsert
    suspend fun upsertAll(responders: List<ResponderEntity>)

    @Query("DELETE FROM responders")
    suspend fun clear()
}

@Dao
interface IncidentEventDao {
    @Query("SELECT * FROM incident_events WHERE incidentClientId = :clientId ORDER BY occurredAtEpochMillis ASC")
    fun observeForIncident(clientId: String): Flow<List<IncidentEventEntity>>

    @Query("SELECT * FROM incident_events WHERE synced = 0 ORDER BY occurredAtEpochMillis ASC")
    suspend fun getUnsynced(): List<IncidentEventEntity>

    @Upsert
    suspend fun upsert(event: IncidentEventEntity)

    @Upsert
    suspend fun upsertAll(events: List<IncidentEventEntity>)

    @Query("UPDATE incident_events SET synced = 1 WHERE eventId = :eventId")
    suspend fun markSynced(eventId: String)

    @Query("DELETE FROM incident_events")
    suspend fun clear()
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE languageTag = :languageTag ORDER BY title ASC")
    fun observeByLanguage(languageTag: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE documentId = :documentId AND languageTag = :languageTag")
    fun observeOne(documentId: String, languageTag: String): Flow<DocumentEntity?>

    @Query("SELECT version FROM documents WHERE documentId = :documentId AND languageTag = :languageTag")
    suspend fun getVersion(documentId: String, languageTag: String): Int?

    /**
     * Called only once a replacement has downloaded in full, so a partial download never
     * destroys the copy a volunteer is relying on.
     */
    @Upsert
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM documents")
    suspend fun clear()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY receivedAtEpochMillis DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE readAtEpochMillis IS NULL")
    fun observeUnreadCount(): Flow<Int>

    @Upsert
    suspend fun upsert(notification: NotificationEntity)

    @Query("UPDATE notifications SET readAtEpochMillis = :at WHERE notificationId = :id")
    suspend fun markRead(id: String, at: Long)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE channelId = :channelId ORDER BY sentAtEpochMillis ASC")
    fun observeChannel(channelId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun getPendingSync(): List<MessageEntity>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET serverId = :serverId, syncState = 'SYNCED' WHERE clientId = :clientId")
    suspend fun markSynced(clientId: String, serverId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface LostFoundDao {
    @Query("SELECT * FROM lost_found_items ORDER BY reportedAtEpochMillis DESC")
    fun observeAll(): Flow<List<LostFoundEntity>>

    @Query("SELECT * FROM lost_found_items WHERE kind = :kind ORDER BY reportedAtEpochMillis DESC")
    fun observeByKind(kind: String): Flow<List<LostFoundEntity>>

    /** Everything still worth matching against. Closed cases drop out of the pool. */
    @Query("SELECT * FROM lost_found_items WHERE status IN ('OPEN', 'MATCHED') ORDER BY reportedAtEpochMillis DESC")
    fun observeActive(): Flow<List<LostFoundEntity>>

    @Query("SELECT * FROM lost_found_items WHERE kind = :kind AND status IN ('OPEN', 'MATCHED')")
    suspend fun getActiveByKind(kind: String): List<LostFoundEntity>

    /**
     * Free-text search across every attribute a volunteer might remember.
     *
     * Deliberately wide: somebody searching "yellow" should find a child recorded with a
     * yellow shirt whether the volunteer typed it into clothing or into the notes.
     */
    @Query(
        """
        SELECT * FROM lost_found_items
        WHERE title LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR personName LIKE '%' || :query || '%'
           OR clothingDescription LIKE '%' || :query || '%'
           OR physicalDescription LIKE '%' || :query || '%'
           OR language LIKE '%' || :query || '%'
           OR qrLocationName LIKE '%' || :query || '%'
        ORDER BY reportedAtEpochMillis DESC
        """,
    )
    fun search(query: String): Flow<List<LostFoundEntity>>

    @Query("SELECT * FROM lost_found_items WHERE clientId = :clientId")
    fun observeByClientId(clientId: String): Flow<LostFoundEntity?>

    @Query("SELECT * FROM lost_found_items WHERE clientId = :clientId")
    suspend fun getByClientId(clientId: String): LostFoundEntity?

    @Query("SELECT * FROM lost_found_items WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): LostFoundEntity?

    @Query("SELECT * FROM lost_found_items WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun getPendingSync(): List<LostFoundEntity>

    @Query("SELECT COUNT(*) FROM lost_found_items WHERE syncState IN ('PENDING', 'FAILED')")
    fun observeUnsyncedCount(): Flow<Int>

    /** Reports whose photo still needs, or deserves another attempt at, face processing. */
    @Query("SELECT * FROM lost_found_items WHERE photoLocalPath IS NOT NULL AND faceMatchStatus IN ('PENDING', 'SERVICE_UNAVAILABLE')")
    suspend fun getAwaitingFaceProcessing(): List<LostFoundEntity>

    @Upsert
    suspend fun upsert(item: LostFoundEntity)

    @Upsert
    suspend fun upsertAll(items: List<LostFoundEntity>)

    @Query("UPDATE lost_found_items SET serverId = :serverId, syncState = 'SYNCED' WHERE clientId = :clientId")
    suspend fun markSynced(clientId: String, serverId: String)

    @Query("UPDATE lost_found_items SET syncState = :syncState WHERE clientId = :clientId")
    suspend fun setSyncState(clientId: String, syncState: String)

    @Query("UPDATE lost_found_items SET status = :status WHERE clientId = :clientId")
    suspend fun setStatus(clientId: String, status: String)

    @Query("UPDATE lost_found_items SET faceMatchStatus = :status WHERE clientId = :clientId")
    suspend fun setFaceMatchStatus(clientId: String, status: String)

    @Query("UPDATE lost_found_items SET custodianUserId = :userId, custodianName = :name, custodianContact = :contact WHERE clientId = :clientId")
    suspend fun setCustodian(clientId: String, userId: String?, name: String?, contact: String?)

    /**
     * Merges a server row without clobbering a report that has not synced yet.
     *
     * Same rule as incidents: until the server has accepted it, the local copy is the
     * newer truth.
     */
    @Transaction
    suspend fun reconcileFromServer(remote: LostFoundEntity) {
        val local = remote.serverId?.let { getByServerId(it) } ?: getByClientId(remote.clientId)

        if (local == null) {
            upsert(remote)
            return
        }

        if (local.syncState == "PENDING" || local.syncState == "SYNCING") return

        upsert(
            local.copy(
                serverId = remote.serverId ?: local.serverId,
                status = remote.status,
                // Face processing is server-side, so the server always wins on it.
                faceMatchStatus = remote.faceMatchStatus,
                photoRemotePath = remote.photoRemotePath ?: local.photoRemotePath,
                custodianUserId = remote.custodianUserId ?: local.custodianUserId,
                custodianName = remote.custodianName ?: local.custodianName,
                custodianContact = remote.custodianContact ?: local.custodianContact,
                syncState = "SYNCED",
            ),
        )
    }

    @Query("DELETE FROM lost_found_items")
    suspend fun clear()
}

/**
 * Custody chain for found people.
 */
@Dao
interface CustodyDao {
    @Query("SELECT * FROM lost_found_custody WHERE reportClientId = :reportClientId ORDER BY fromEpochMillis DESC")
    fun observeForReport(reportClientId: String): Flow<List<CustodyEntity>>

    @Query("SELECT * FROM lost_found_custody WHERE reportClientId = :reportClientId AND untilEpochMillis IS NULL LIMIT 1")
    suspend fun getCurrent(reportClientId: String): CustodyEntity?

    @Query("SELECT * FROM lost_found_custody WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun getPendingSync(): List<CustodyEntity>

    @Query("UPDATE lost_found_custody SET untilEpochMillis = :until WHERE reportClientId = :reportClientId AND untilEpochMillis IS NULL")
    suspend fun closeOpenSpans(reportClientId: String, until: Long)

    @Upsert
    suspend fun upsert(record: CustodyEntity)

    /**
     * Closes the outgoing custodian and opens the incoming one atomically.
     *
     * A transaction because a handover that half-applies would leave a found child with
     * either two custodians or none, and "who has this person right now" is the question
     * the whole record exists to answer.
     */
    @Transaction
    suspend fun handOver(record: CustodyEntity) {
        closeOpenSpans(record.reportClientId, record.fromEpochMillis)
        upsert(record)
    }

    @Query("UPDATE lost_found_custody SET syncState = 'SYNCED' WHERE clientId = :clientId")
    suspend fun markSynced(clientId: String)

    @Query("DELETE FROM lost_found_custody")
    suspend fun clear()
}

/**
 * Candidate match records.
 */
@Dao
interface LostFoundMatchDao {
    @Query("SELECT * FROM lost_found_matches WHERE status = 'CANDIDATE' ORDER BY overallScore DESC")
    fun observeCandidates(): Flow<List<LostFoundMatchEntity>>

    @Query("SELECT * FROM lost_found_matches WHERE lostReportClientId = :reportClientId OR foundReportClientId = :reportClientId ORDER BY overallScore DESC")
    fun observeForReport(reportClientId: String): Flow<List<LostFoundMatchEntity>>

    @Query("SELECT * FROM lost_found_matches WHERE clientId = :clientId")
    fun observeByClientId(clientId: String): Flow<LostFoundMatchEntity?>

    @Query("SELECT * FROM lost_found_matches WHERE clientId = :clientId")
    suspend fun getByClientId(clientId: String): LostFoundMatchEntity?

    /**
     * Any prior verdict on this pair, whatever its status.
     *
     * Used to suppress re-surfacing: once a volunteer has rejected a pairing, the engine
     * must not keep proposing it every time it runs.
     */
    @Query("SELECT * FROM lost_found_matches WHERE lostReportClientId = :lostId AND foundReportClientId = :foundId LIMIT 1")
    suspend fun findPair(lostId: String, foundId: String): LostFoundMatchEntity?

    @Query("SELECT * FROM lost_found_matches WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun getPendingSync(): List<LostFoundMatchEntity>

    /**
     * Whether a report still has any candidate awaiting review.
     *
     * Used after a rejection to decide whether the report returns to OPEN. Must count
     * *all* candidates, not just unsynced ones — a report whose other candidate had
     * already synced would otherwise be reopened while a volunteer was still reviewing it.
     */
    @Query(
        """
        SELECT COUNT(*) FROM lost_found_matches
        WHERE status = 'CANDIDATE'
          AND (lostReportClientId = :reportClientId OR foundReportClientId = :reportClientId)
        """,
    )
    suspend fun countCandidatesFor(reportClientId: String): Int

    @Query("SELECT COUNT(*) FROM lost_found_matches WHERE status = 'CANDIDATE'")
    fun observeCandidateCount(): Flow<Int>

    @Upsert
    suspend fun upsert(match: LostFoundMatchEntity)

    @Upsert
    suspend fun upsertAll(matches: List<LostFoundMatchEntity>)

    @Query("UPDATE lost_found_matches SET serverId = :serverId, syncState = 'SYNCED' WHERE clientId = :clientId")
    suspend fun markSynced(clientId: String, serverId: String)

    @Query("DELETE FROM lost_found_matches")
    suspend fun clear()
}

/**
 * Cached QR locations, so a scan resolves in a dead spot.
 */
@Dao
interface QrLocationDao {
    @Query("SELECT * FROM qr_locations WHERE token = :token")
    suspend fun getByToken(token: String): QrLocationEntity?

    @Query("SELECT * FROM qr_locations ORDER BY routeSequence ASC")
    fun observeAll(): Flow<List<QrLocationEntity>>

    @Upsert
    suspend fun upsert(location: QrLocationEntity)

    @Upsert
    suspend fun upsertAll(locations: List<QrLocationEntity>)

    @Query("DELETE FROM qr_locations")
    suspend fun clear()
}

