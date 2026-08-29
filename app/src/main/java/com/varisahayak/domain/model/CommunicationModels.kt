package com.varisahayak.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A communication channel for role-based or zone-based coordination.
 */
@Serializable
data class CommunicationChannel(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int? = null,
    val unreadCount: Int = 0,
    val hasSos: Boolean = false,
    val isOnline: Boolean = true,
    val onlineCount: Int = 0,
)

/**
 * A single message in a channel.
 */
@Serializable
data class CommunicationMessage(
    val id: String,
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val content: String,
    val timestamp: Instant,
    val isSos: Boolean = false,
    val isFromMe: Boolean = false,
)

/**
 * UI state for the broadcasting mode.
 */
data class BroadcastingState(
    val isEnabled: Boolean = false,
    val selectedChannelIds: Set<String> = emptySet(),
)
