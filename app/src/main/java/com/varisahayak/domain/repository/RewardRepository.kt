package com.varisahayak.domain.repository

import com.varisahayak.domain.model.RewardProfile
import com.varisahayak.domain.model.XPTransaction
import kotlinx.coroutines.flow.Flow

interface RewardRepository {
    fun observeRewardProfile(): Flow<RewardProfile?>
    fun observeTransactions(): Flow<List<XPTransaction>>
    
    suspend fun awardXp(amount: Int, reason: String, relatedEntityId: String? = null)
    suspend fun recordImpact(
        incidentsResolved: Int = 0,
        sosResponses: Int = 0,
        peopleAssisted: Int = 0,
        lostFoundAssisted: Int = 0
    )
    
    suspend fun refresh()
}
