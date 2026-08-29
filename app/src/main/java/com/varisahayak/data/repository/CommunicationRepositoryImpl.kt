package com.varisahayak.data.repository

import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.CommunicationChannel
import com.varisahayak.domain.model.CommunicationMessage
import com.varisahayak.domain.repository.CommunicationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunicationRepositoryImpl @Inject constructor() : CommunicationRepository {

    private val _channels = MutableStateFlow(initialChannels())
    private val _messages = MutableStateFlow(initialMessages())

    override fun observeChannels(): Flow<List<CommunicationChannel>> = _channels

    override fun observeMessages(channelId: String): Flow<List<CommunicationMessage>> {
        return _messages.map { allMessages ->
            allMessages.filter { it.channelId == channelId }
                .sortedBy { it.timestamp }
        }
    }

    override suspend fun sendMessage(
        channelIds: List<String>,
        content: String,
        isSos: Boolean
    ): Outcome<Unit> {
        delay(500.milliseconds) // Simulate network
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        
        channelIds.forEach { channelId ->
            val newMessage = CommunicationMessage(
                id = "msg_${now.toEpochMilliseconds()}_$channelId",
                channelId = channelId,
                senderId = "me",
                senderName = "You",
                senderRole = "Admin",
                content = content,
                timestamp = now,
                isSos = isSos,
                isFromMe = true
            )
            _messages.update { it + newMessage }
        }
        
        return Outcome.Success(Unit)
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
            onlineCount = 9812,
            unreadCount = 3
        ),
        CommunicationChannel(
            id = "medical",
            name = "Medical Team",
            description = "Doctors, Nurses",
            onlineCount = 145,
            unreadCount = 1
        ),
        CommunicationChannel(
            id = "police",
            name = "Police",
            description = "Security, Patrol",
            onlineCount = 82
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

    private fun initialMessages() = listOf(
        CommunicationMessage(
            id = "m1",
            channelId = "all_hands",
            senderId = "s1",
            senderName = "Suresh Kale",
            senderRole = "Pune Zone",
            content = "Crowd is building up near Swargate. Requesting 2 more volunteers.",
            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()).minus(15.minutes)
        ),
        CommunicationMessage(
            id = "m2",
            channelId = "all_hands",
            senderId = "me",
            senderName = "You",
            senderRole = "Admin",
            content = "Assigning Batch 7 - 3 volunteers dispatched to Swargate junction.",
            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()).minus(14.minutes),
            isFromMe = true
        ),
        CommunicationMessage(
            id = "m3",
            channelId = "all_hands",
            senderId = "system",
            senderName = "SOS",
            senderRole = "System",
            content = "SOS ALERT: Medical emergency at Lonand Bridge. VS-0087 activated SOS.",
            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()).minus(6.minutes),
            isSos = true
        ),
        CommunicationMessage(
            id = "m4",
            channelId = "all_hands",
            senderId = "s2",
            senderName = "Dr. Anita More",
            senderRole = "Medical",
            content = "Medical team dispatched. ETA 4 minutes.",
            timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()).minus(2.minutes)
        )
    )
}
