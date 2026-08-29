package com.varisahayak.core.walkie

import com.varisahayak.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The seam a real radio bridge plugs into.
 *
 * Kept deliberately small. Everything a PTT transport actually needs is: join a channel,
 * leave it, key the mic, unkey it, and publish what is happening. Anything richer — call
 * history, presence lists, transcripts — belongs to a different feature, and putting it
 * here would make the interface expensive to implement for the one thing it exists for.
 *
 * Implementations are responsible for requesting `RECORD_AUDIO` themselves; the UI never
 * assumes the permission has been granted and renders from [state] alone.
 */
interface WalkieController {

    val state: StateFlow<WalkieUiState>

    /** Join [channelId]. Idempotent — joining the current channel is a no-op. */
    fun join(channelId: String)

    fun leave()

    /** Key the mic. Ignored unless [WalkieUiState.canTransmit]. */
    fun startTransmit()

    /** Unkey. Always safe to call, including when not transmitting. */
    fun stopTransmit()
}

/**
 * The stub that ships today.
 *
 * There is no audio transport in VARI Sahayak yet — no capture, no codec, no signalling.
 * This exists so the widget, its states, and its accessibility behaviour are all real and
 * testable, and so that swapping in LiveKit or a Supabase Realtime broadcast is a matter of
 * providing a different binding rather than rewriting a screen.
 *
 * It reports [WalkieUiState.isSimulated] `true`, and the widget renders that fact rather
 * than concealing it. A radio control that looks live but carries nothing would be worse
 * than no radio control at all — a volunteer would key it in an emergency and believe they
 * had been heard.
 */
@Singleton
class SimulatedWalkieController @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
) : WalkieController {

    private val _state = MutableStateFlow(
        WalkieUiState(
            channel = DEFAULT_CHANNEL,
            connection = WalkieConnection.Connected,
            isSimulated = true,
        ),
    )
    override val state: StateFlow<WalkieUiState> = _state.asStateFlow()

    private var levelJob: Job? = null
    private var tick = 0

    override fun join(channelId: String) {
        if (_state.value.channel?.id == channelId) return
        _state.update {
            it.copy(
                channel = CHANNELS.firstOrNull { channel -> channel.id == channelId }
                    ?: DEFAULT_CHANNEL,
                floor = WalkieFloor.Idle,
                levels = emptyList(),
            )
        }
    }

    override fun leave() {
        stopTransmit()
        _state.update {
            it.copy(
                channel = null,
                connection = WalkieConnection.NotConfigured,
                floor = WalkieFloor.Idle,
                levels = emptyList(),
            )
        }
    }

    override fun startTransmit() {
        if (!_state.value.canTransmit) return
        _state.update { it.copy(floor = WalkieFloor.Transmitting) }
        startLevelFeed()
    }

    override fun stopTransmit() {
        levelJob?.cancel()
        levelJob = null
        _state.update { it.copy(floor = WalkieFloor.Idle, levels = emptyList()) }
    }

    /**
     * Drives the waveform while the mic is keyed.
     *
     * A real implementation replaces this with RMS from the capture buffer. The shape here
     * is a slow carrier plus jitter, which reads as speech rather than as a sine wave —
     * a perfectly periodic waveform is the tell that an indicator is decorative.
     */
    private fun startLevelFeed() {
        levelJob?.cancel()
        levelJob = scope.launch {
            val bars = ArrayDeque<Float>()
            while (true) {
                tick++
                val carrier = abs(sin(tick * 0.31)).toFloat()
                val jitter = Random.nextFloat() * 0.45f
                val level = (carrier * 0.55f + jitter).coerceIn(0.08f, 1f)

                bars.addLast(level)
                while (bars.size > WAVEFORM_BAR_COUNT) bars.removeFirst()

                _state.update { it.copy(levels = bars.toList()) }
                delay(LEVEL_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val LEVEL_INTERVAL_MS = 60L

        val DEFAULT_CHANNEL = WalkieChannel(
            id = "route-main",
            name = "Route Net 1",
            memberCount = 12,
        )

        val CHANNELS = listOf(
            DEFAULT_CHANNEL,
            WalkieChannel(id = "medical", name = "Medical", memberCount = 6),
            WalkieChannel(id = "emergency", name = "Emergency", memberCount = 23, isEmergency = true),
        )
    }
}
