package com.varisahayak.core.walkie

/**
 * Walkie-talkie channel state.
 *
 * This models a push-to-talk radio bridge, not a chat. The distinction matters to the UI:
 * a radio has exactly one talker at a time, no history, and no delivery receipts, so there
 * is nothing to show but who is on the channel and who currently holds the floor.
 */

/** A channel a responder can be joined to. */
data class WalkieChannel(
    val id: String,
    val name: String,
    val memberCount: Int,
    /**
     * Emergency channels are monitored by command and cannot be left from the widget —
     * dropping off the emergency net has to be a deliberate act, not a mis-tap.
     */
    val isEmergency: Boolean = false,
)

/** Whether the transport is usable right now. */
enum class WalkieConnection {
    /** No transport is wired up. The widget is inert and says so. */
    NotConfigured,
    Connecting,
    Connected,

    /** Transport exists but the network dropped. Local PTT is disabled. */
    Disconnected,
}

/** Who holds the floor. */
sealed interface WalkieFloor {
    /** Nobody is talking. */
    data object Idle : WalkieFloor

    /** This device is transmitting. */
    data object Transmitting : WalkieFloor

    /** Somebody else is transmitting. */
    data class Receiving(val speakerName: String) : WalkieFloor
}

/**
 * Everything the widget renders.
 *
 * @param levels normalised 0..1 amplitude samples, oldest first, for the activity
 *   waveform. Empty when nothing is being transmitted or received.
 * @param isSimulated true when no real audio transport is attached. Surfaced in the UI
 *   rather than hidden — a radio widget that looks live but carries nothing is the single
 *   most dangerous thing this screen could do.
 */
data class WalkieUiState(
    val channel: WalkieChannel? = null,
    val connection: WalkieConnection = WalkieConnection.NotConfigured,
    val floor: WalkieFloor = WalkieFloor.Idle,
    val levels: List<Float> = emptyList(),
    val isSimulated: Boolean = true,
) {
    val isTransmitting: Boolean get() = floor is WalkieFloor.Transmitting

    val isReceiving: Boolean get() = floor is WalkieFloor.Receiving

    /** Push-to-talk is only meaningful on a live channel that nobody else is holding. */
    val canTransmit: Boolean
        get() = connection == WalkieConnection.Connected && floor !is WalkieFloor.Receiving

    val isActive: Boolean get() = isTransmitting || isReceiving
}

/** Number of bars the activity waveform draws. */
const val WAVEFORM_BAR_COUNT = 28
