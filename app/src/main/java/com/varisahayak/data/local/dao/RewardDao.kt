package com.varisahayak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.varisahayak.data.local.entity.RewardProfileEntity
import com.varisahayak.data.local.entity.UserBadgeEntity
import com.varisahayak.data.local.entity.XPTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {

    @Query("SELECT * FROM reward_profiles WHERE userId = :userId")
    fun observeProfile(userId: String): Flow<RewardProfileEntity?>

    @Query("SELECT * FROM reward_profiles WHERE userId = :userId")
    suspend fun getProfile(userId: String): RewardProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: RewardProfileEntity)

    @Query("SELECT * FROM user_badges WHERE userId = :userId")
    fun observeBadges(userId: String): Flow<List<UserBadgeEntity>>

    @Query("SELECT * FROM user_badges WHERE userId = :userId")
    suspend fun getBadges(userId: String): List<UserBadgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: UserBadgeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: XPTransactionEntity)

    @Query("SELECT * FROM xp_transactions WHERE userId = :userId ORDER BY occurredAtEpochMillis DESC")
    fun observeTransactions(userId: String): Flow<List<XPTransactionEntity>>

    @Query("SELECT COUNT(*) FROM xp_transactions WHERE relatedEntityId = :entityId")
    suspend fun countTransactionsForEntity(entityId: String): Int

    @Transaction
    suspend fun awardXp(
        userId: String,
        amount: Int,
        reason: String,
        entityId: String?,
        at: Long,
        levelCalculator: (Int) -> Int
    ) {
        // Idempotency check: if XP was already awarded for this entity (e.g. incident), skip.
        if (entityId != null && countTransactionsForEntity(entityId) > 0) return

        val transaction = XPTransactionEntity(
            transactionId = java.util.UUID.randomUUID().toString(),
            userId = userId,
            amount = amount,
            reason = reason,
            relatedEntityId = entityId,
            occurredAtEpochMillis = at
        )
        insertTransaction(transaction)

        val current = getProfile(userId) ?: RewardProfileEntity(
            userId = userId,
            totalXp = 0,
            level = 1,
            updatedAtEpochMillis = at
        )

        val newTotalXp = current.totalXp + amount
        val newLevel = levelCalculator(newTotalXp)

        upsertProfile(
            current.copy(
                totalXp = newTotalXp,
                level = newLevel,
                updatedAtEpochMillis = at
            )
        )
    }

    @Query("UPDATE reward_profiles SET incidentsResolved = incidentsResolved + 1 WHERE userId = :userId")
    suspend fun incrementResolvedCount(userId: String)

    @Query("UPDATE reward_profiles SET sosResponses = sosResponses + 1 WHERE userId = :userId")
    suspend fun incrementSosCount(userId: String)

    @Query("UPDATE reward_profiles SET peopleAssisted = peopleAssisted + :count WHERE userId = :userId")
    suspend fun incrementAssistedCount(userId: String, count: Int = 1)
}
