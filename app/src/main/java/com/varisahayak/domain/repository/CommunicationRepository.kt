package com.varisahayak.domain.repository

import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.CommunicationChannel
import com.varisahayak.domain.model.CommunicationMessage
import kotlinx.coroutines.flow.Flow

/**
 * Interface for the multi-channel communication system.
 */
interface CommunicationRepository {
    
    /** Observes the list of available channels for the user. */
    fun observeChannels(): Flow<List<CommunicationChannel>>
    
    /** Observes messages for a specific channel. */
    fun observeMessages(channelId: String): Flow<List<CommunicationMessage>>
    
    /** 
     * Sends a message to one or more channels.
     * If multiple channels are provided, it acts as a broadcast.
     */
    suspend fun sendMessage(
        channelIds: List<String>,
        content: String,
        isSos: Boolean = false
    ): Outcome<Unit>
    
    /** Marks messages in a channel as read. */
    suspend fun markAsRead(channelId: String): Outcome<Unit>
}
