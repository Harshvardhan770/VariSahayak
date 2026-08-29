package com.varisahayak.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reward_profiles")
data class RewardProfileEntity(
    @PrimaryKey val userId: String,
    val totalXp: Int,
    val level: Int,
    val incidentsResolved: Int = 0,
    val sosResponses: Int = 0,
    val peopleAssisted: Int = 0,
    val lostFoundAssisted: Int = 0,
    val updatedAtEpochMillis: Long
)

@Entity(tableName = "xp_transactions")
data class XPTransactionEntity(
    @PrimaryKey val transactionId: String,
    val userId: String,
    val amount: Int,
    val reason: String,
    val relatedEntityId: String? = null,
    val occurredAtEpochMillis: Long,
    val synced: Boolean = false
)

@Entity(tableName = "user_badges")
data class UserBadgeEntity(
    @PrimaryKey val badgeId: String,
    val userId: String,
    val badgeType: String, // e.g. "FIRST_RESPONSE"
    val unlockedAtEpochMillis: Long,
    val synced: Boolean = false
)
