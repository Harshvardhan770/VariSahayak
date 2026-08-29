package com.varisahayak.feature.communication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.BroadcastingState
import com.varisahayak.domain.model.CommunicationChannel
import com.varisahayak.domain.model.CommunicationMessage
import com.varisahayak.domain.repository.CommunicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val repository: CommunicationRepository
) : ViewModel() {

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId = _selectedChannelId.asStateFlow()

    private val _broadcastingState = MutableStateFlow(BroadcastingState())
    val broadcastingState = _broadcastingState.asStateFlow()

    val channels = repository.observeChannels().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val messages = _selectedChannelId.flatMapLatest { channelId ->
        if (channelId == null) {
            MutableStateFlow(emptyList<CommunicationMessage>())
        } else {
            repository.observeMessages(channelId)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Default to the first channel
        viewModelScope.launch {
            repository.observeChannels().collect { channels ->
                if (_selectedChannelId.value == null && channels.isNotEmpty()) {
                    _selectedChannelId.value = channels.first().id
                }
            }
        }
    }

    fun selectChannel(channelId: String) {
        _selectedChannelId.value = channelId
        _broadcastingState.update { it.copy(isEnabled = false) }
        viewModelScope.launch {
            repository.markAsRead(channelId)
        }
    }

    fun toggleBroadcastingMode() {
        _broadcastingState.update { 
            val newState = !it.isEnabled
            it.copy(
                isEnabled = newState,
                selectedChannelIds = if (newState) setOf(_selectedChannelId.value).filterNotNull().toSet() else emptySet()
            )
        }
    }

    fun toggleChannelSelection(channelId: String) {
        _broadcastingState.update { state ->
            val newSelection = if (state.selectedChannelIds.contains(channelId)) {
                state.selectedChannelIds - channelId
            } else {
                state.selectedChannelIds + channelId
            }
            state.copy(selectedChannelIds = newSelection)
        }
    }

    fun selectAllChannels() {
        _broadcastingState.update { state ->
            state.copy(selectedChannelIds = channels.value.map { it.id }.toSet())
        }
    }

    fun deselectAllChannels() {
        _broadcastingState.update { state ->
            state.copy(selectedChannelIds = emptySet())
        }
    }

    fun sendMessage(content: String) {
        val state = _broadcastingState.value
        val targets = if (state.isEnabled) {
            state.selectedChannelIds.toList()
        } else {
            listOfNotNull(_selectedChannelId.value)
        }

        if (targets.isEmpty() || content.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(targets, content)
        }
    }

    fun sendSos() {
        // Quick action: send SOS to all hands
        viewModelScope.launch {
            repository.sendMessage(listOf("all_hands"), "SOS EMERGENCY TRIGGERED", isSos = true)
        }
    }
}
