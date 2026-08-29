package com.varisahayak.data.repository

import android.util.Log
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.CommunicationChannel
import com.varisahayak.domain.model.CommunicationMessage
import com.varisahayak.domain.repository.CommunicationRepository
import com.varisahayak.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunicationRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val profileRepository: ProfileRepository,
) : CommunicationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _channels = MutableStateFlow(initialChannels())
    
    private val activeChannels = mutableMapOf<String, RealtimeChannel>()

    override fun observeChannels(): Flow<List<CommunicationChannel>> = _channels

    override fun observeMessages(channelId: String): Flow<CommunicationMessage> {
        val channel = getOrCreateChannel(channelId)
        return channel.broadcastFlow<CommunicationMessage>(event = "message")
    }

    private fun getOrCreateChannel(channelId: String): RealtimeChannel {
        return activeChannels.getOrPut(channelId) {
            supabase.channel("comms:$channelId").also {
                scope.launch {
                    try {
                        it.subscribe()
                    } catch (e: Exception) {
                        Log.e("CommsRepo", "Failed to subscribe to channel $channelId", e)
                    }
                }
            }
        }
    }

    override suspend fun sendMessage(
        channelIds: List<String>,
        content: String,
        isSos: Boolean
    ): Outcome<List<CommunicationMessage>> {
        val profile = profileRepository.observeCurrentProfile().first() 
            ?: return Outcome.Failure(AppError.ProfileUnavailable())
        
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val sentMessages = mutableListOf<CommunicationMessage>()
        
        channelIds.forEach { channelId ->
            val message = CommunicationMessage(
                id = "msg_${System.nanoTime()}",
                channelId = channelId,
                senderId = profile.userId,
                senderName = profile.displayName,
                senderRole = profile.role.name,
                content = content,
                timestamp = now,
                isSos = isSos,
                isFromMe = false // Relative to others
            )

            try {
                val realtimeChannel = getOrCreateChannel(channelId)
                realtimeChannel.broadcast("message", message)
                // Add the local version (with isFromMe = true) to the return list
                sentMessages.add(message.copy(isFromMe = true))
            } catch (e: Exception) {
                Log.e("CommsRepo", "Failed to broadcast message to $channelId", e)
            }
        }
        
        return Outcome.Success(sentMessages)
    }

    override suspend fun markAsRead(channelId: String): Outcome<Unit> {
        _channels.update { channels ->
            channels.map { 
                if (it.id == channelId) it.copy(unreadCount = 0) else it
            }
        }
        return Outcome.Success(Unit)
    }

    private fun initialChannels() = listOf(
        CommunicationChannel(
            id = "all_hands",
            name = "All Hands",
            description = "Broadcast to all",
            onlineCount = 0,
            unreadCount = 0
        ),
        CommunicationChannel(
            id = "medical",
            name = "Medical Team",
            description = "Doctors, Nurses",
            onlineCount = 0
        ),
        CommunicationChannel(
            id = "police",
            name = "Police",
            description = "Security, Patrol",
            onlineCount = 0
        ),
        CommunicationChannel(
            id = "pandharpur_zone",
            name = "Pandharpur Zone",
            description = "Entry & Temple teams",
            hasSos = true
        ),
        CommunicationChannel(
            id = "lonand_zone",
            name = "Lonand Zone",
            description = "km 140-155"
        )
    )
}
