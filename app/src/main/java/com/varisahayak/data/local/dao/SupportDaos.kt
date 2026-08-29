package com.varisahayak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.varisahayak.data.local.entity.DocumentEntity
import com.varisahayak.data.local.entity.IncidentEventEntity
import com.varisahayak.data.local.entity.LostFoundEntity
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

    @Query("SELECT * FROM lost_found_items WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY reportedAtEpochMillis DESC")
    fun search(query: String): Flow<List<LostFoundEntity>>

    @Query("SELECT * FROM lost_found_items WHERE syncState IN ('PENDING', 'FAILED')")
    suspend fun getPendingSync(): List<LostFoundEntity>

    @Upsert
    suspend fun upsert(item: LostFoundEntity)

    @Query("UPDATE lost_found_items SET serverId = :serverId, syncState = 'SYNCED' WHERE clientId = :clientId")
    suspend fun markSynced(clientId: String, serverId: String)

    @Query("DELETE FROM lost_found_items")
    suspend fun clear()
}
