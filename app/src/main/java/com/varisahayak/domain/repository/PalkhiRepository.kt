package com.varisahayak.domain.repository

import com.varisahayak.domain.model.Palkhi
import kotlinx.coroutines.flow.Flow

interface PalkhiRepository {
    fun observePalkhis(): Flow<List<Palkhi>>
    suspend fun refreshPositions()
}
