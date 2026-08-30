package com.varisahayak.core.walkie

/**
 * Walkie-talkie channel state.
 *
 * This models a push-to-talk radio bridge, not a chat. The distinction matters to the UI:
 * there is no history and no delivery receipts, so there is nothing to show but who is on
 * the channel and who is currently talking.
 *
 * Unlike a half-duplex radio, this one has no floor control. Mics are closed by default and
 * a press opens yours; if three people key up you hear all three. That is deliberate — the
 * app is used outdoors in a walking crowd, where an always-open mic is unusable, but
 * refusing a second speaker during an emergency is worse than letting two voices overlap.
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

/**
 * What the channel is doing, as one value for the widget to switch on.
 *
 * Derived from [WalkieUiState.isMicOpen] and [WalkieUiState.speakers] rather than stored,
 * because those two are independent under overlap and a stored enum would have to encode a
 * precedence that then drifts from whatever the controller believes.
 */
sealed interface WalkieFloor {
    /** Nobody is talking. */
    data object Idle : WalkieFloor

    /** This device's mic is open. Takes precedence in the UI even if others are talking
     *  too: being wrong about whether your own mic is live is the costlier mistake. */
    data object Transmitting : WalkieFloor

    /**
     * Somebody else is transmitting, and this device is not.
     *
     * A list, not a name. With overlap, "who am I hearing" stops being decoration and
     * becomes the primary thing the widget has to answer — three voices at once is
     * intelligible only if the screen names them.
     */
    data class Receiving(val speakers: List<String>) : WalkieFloor
}

/**
 * Everything the widget renders.
 *
 * @param isMicOpen this device's microphone is unmuted and publishing.
 * @param speakers display names of the *other* participants currently audible, loudest
 *   first. Never includes this device.
 * @param levels normalised 0..1 amplitude samples, oldest first, for the activity
 *   waveform. Empty when nothing is being transmitted or received.
 * @param isSimulated true when no real audio transport is attached. Surfaced in the UI
 *   rather than hidden — a radio widget that looks live but carries nothing is the single
 *   most dangerous thing this screen could do.
 */
data class WalkieUiState(
    val channel: WalkieChannel? = null,
    /**
     * Every channel this device may join.
     *
     * Carried in the state rather than read from the controller, because the widget is a
     * design-system component that must not know which implementation is bound. Empty
     * means "no choice to offer", which is what a single-channel transport would report.
     */
    val availableChannels: List<WalkieChannel> = emptyList(),
    val connection: WalkieConnection = WalkieConnection.NotConfigured,
    val isMicOpen: Boolean = false,
    val speakers: List<String> = emptyList(),
    val levels: List<Float> = emptyList(),
    val isSimulated: Boolean = true,
) {
    val floor: WalkieFloor
        get() = when {
            isMicOpen -> WalkieFloor.Transmitting
            speakers.isNotEmpty() -> WalkieFloor.Receiving(speakers)
            else -> WalkieFloor.Idle
        }

    val isTransmitting: Boolean get() = isMicOpen

    val isReceiving: Boolean get() = !isMicOpen && speakers.isNotEmpty()

    /**
     * Push-to-talk is meaningful on any live channel.
     *
     * Hearing somebody does *not* disable it. An earlier version required the floor to be
     * clear, which would have meant a volunteer standing next to a collapsed pilgrim
     * could not interrupt a routine transmission.
     */
    val canTransmit: Boolean
        get() = connection == WalkieConnection.Connected

    val isActive: Boolean get() = isMicOpen || speakers.isNotEmpty()
}

/** Number of bars the activity waveform draws. */
const val WAVEFORM_BAR_COUNT = 28
