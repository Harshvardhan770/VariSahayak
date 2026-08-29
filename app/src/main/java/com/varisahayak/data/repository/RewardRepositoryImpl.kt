package com.varisahayak.data.repository

import com.varisahayak.core.common.Clock
import com.varisahayak.core.common.DispatcherProvider
import com.varisahayak.data.local.dao.RewardDao
import com.varisahayak.data.local.entity.RewardProfileEntity
import com.varisahayak.data.local.entity.XPTransactionEntity
import com.varisahayak.data.local.entity.UserBadgeEntity
import com.varisahayak.domain.model.*
import com.varisahayak.domain.repository.RewardRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val rewardDao: RewardDao,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider
) : RewardRepository {

    override fun observeRewardProfile(): Flow<RewardProfile?> {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return flowOf(null)
        
        return combine(
            rewardDao.observeProfile(userId),
            rewardDao.observeBadges(userId)
        ) { entity, badgeEntities ->
            val profileEntity = entity ?: RewardProfileEntity(
                userId = userId,
                totalXp = 0,
                level = 1,
                updatedAtEpochMillis = clock.nowEpochMillis()
            )
            profileEntity.toDomain(badgeEntities.map { it.toBadge() })
        }
    }

    override fun observeTransactions(): Flow<List<XPTransaction>> {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return flowOf(emptyList())
        return rewardDao.observeTransactions(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun awardXp(amount: Int, reason: String, relatedEntityId: String?) = withContext(dispatchers.io) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
        val now = clock.nowEpochMillis()
        
        rewardDao.awardXp(
            userId = userId,
            amount = amount,
            reason = reason,
            entityId = relatedEntityId,
            at = now,
            levelCalculator = { xp -> RewardEngine.calculateLevel(xp) }
        )
        
        checkBadgeUnlocks(userId)
    }

    override suspend fun recordImpact(
        incidentsResolved: Int,
        sosResponses: Int,
        peopleAssisted: Int,
        lostFoundAssisted: Int
    ) = withContext(dispatchers.io) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
        
        if (incidentsResolved > 0) rewardDao.incrementResolvedCount(userId)
        if (sosResponses > 0) rewardDao.incrementSosCount(userId)
        if (peopleAssisted > 0) rewardDao.incrementAssistedCount(userId, peopleAssisted)
        
        checkBadgeUnlocks(userId)
    }

    override suspend fun refresh() {
        // Mock sync from server if we had a backend
    }

    private suspend fun checkBadgeUnlocks(userId: String) {
        val profile = rewardDao.getProfile(userId) ?: return
        val currentBadges = rewardDao.getBadges(userId).map { it.badgeType }.toSet()
        val now = clock.nowEpochMillis()

        // Badge: First Response
        if ("FIRST_RESPONSE" !in currentBadges && profile.incidentsResolved >= 1) {
            unlockBadge(userId, "FIRST_RESPONSE", now)
        }

        // Badge: SOS Guardian
        if ("SOS_GUARDIAN" !in currentBadges && profile.sosResponses >= 5) {
            unlockBadge(userId, "SOS_GUARDIAN", now)
        }

        // Badge: Community Champion
        if ("COMMUNITY_CHAMPION" !in currentBadges && profile.totalXp >= 1000) {
            unlockBadge(userId, "COMMUNITY_CHAMPION", now)
        }
    }

    private suspend fun unlockBadge(userId: String, type: String, at: Long) {
        rewardDao.insertBadge(
            UserBadgeEntity(
                badgeId = java.util.UUID.randomUUID().toString(),
                userId = userId,
                badgeType = type,
                unlockedAtEpochMillis = at
            )
        )
    }

    private fun RewardProfileEntity.toDomain(badges: List<Badge>): RewardProfile {
        return RewardProfile(
            userId = userId,
            totalXp = totalXp,
            level = level,
            badges = badges,
            impact = ImpactSummary(
                incidentsResolved = incidentsResolved,
                sosResponses = sosResponses,
                peopleAssisted = peopleAssisted,
                lostFoundAssisted = lostFoundAssisted
            )
        )
    }

    private fun UserBadgeEntity.toBadge(): Badge {
        val (name, description) = when (badgeType) {
            "FIRST_RESPONSE" -> "First Response" to "Resolved your first incident"
            "SOS_GUARDIAN" -> "SOS Guardian" to "Successfully handled 5 SOS incidents"
            "COMMUNITY_CHAMPION" -> "Community Champion" to "Reached 1,000 XP in service"
            "QUICK_RESPONDER" -> "Quick Responder" to "Consistently fast response times"
            "LOST_FOUND_HELPER" -> "Lost & Found Helper" to "Assisted in reuniting families"
            else -> badgeType.replace("_", " ").lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() } to "Earned through your contributions"
        }
        
        return Badge(
            id = badgeId,
            name = name,
            description = description,
            iconResId = 0,
            unlockedAtEpochMillis = unlockedAtEpochMillis
        )
    }

    private fun XPTransactionEntity.toDomain(): XPTransaction {
        return XPTransaction(
            id = transactionId,
            userId = userId,
            amount = amount,
            reason = reason,
            relatedEntityId = relatedEntityId,
            occurredAtEpochMillis = occurredAtEpochMillis
        )
    }
}
