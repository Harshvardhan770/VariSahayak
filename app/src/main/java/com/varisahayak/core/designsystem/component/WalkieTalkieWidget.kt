package com.varisahayak.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.walkie.WAVEFORM_BAR_COUNT
import com.varisahayak.core.walkie.WalkieConnection
import com.varisahayak.core.walkie.WalkieFloor
import com.varisahayak.core.walkie.WalkieUiState

/**
 * The push-to-talk widget.
 *
 * Sized and placed for a thumb: it lives at the bottom-left so a right-handed user's thumb
 * reaches it without crossing the map, and the PTT target is 64dp — larger than the 48dp
 * floor, because keying a radio is something people do while looking somewhere else.
 *
 * Transmission state is carried by colour, by the waveform, *and* by the button label
 * changing from "Hold to talk" to "On air". A radio indicator that only changes hue is one
 * a user in sunlight cannot read, and being wrong about whether your mic is open is not a
 * recoverable error.
 */
@Composable
fun WalkieTalkieWidget(
    state: WalkieUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onStartTransmit: () -> Unit,
    onStopTransmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channel = state.channel ?: return
    val colors = VariTheme.colors

    GlassSurface(modifier = modifier) {
        Column(modifier = Modifier.padding(Dimens.SpaceSm)) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onToggleExpanded)
                    .padding(Dimens.SpaceXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Podcasts,
                    contentDescription = null,
                    tint = if (state.isActive) colors.critical else colors.textSecondary,
                    modifier = Modifier.size(Dimens.IconMd),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = channelSubtitle(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = stringResource(
                        if (expanded) R.string.walkie_collapse else R.string.walkie_expand,
                    ),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(Dimens.IconMd),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(Dimens.SpaceSm))

                    VoiceActivityWaveform(
                        levels = state.levels,
                        active = state.isActive,
                        color = when {
                            state.isTransmitting -> colors.critical
                            state.isReceiving -> colors.info
                            else -> colors.textMuted
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.WaveformHeight),
                    )

                    Spacer(Modifier.height(Dimens.SpaceSm))

                    PushToTalkButton(
                        state = state,
                        onStartTransmit = onStartTransmit,
                        onStopTransmit = onStopTransmit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun channelSubtitle(state: WalkieUiState): String {
    val channel = state.channel ?: return ""
    return when {
        // Named first, because "who is talking" outranks everything else on a radio.
        state.floor is WalkieFloor.Receiving ->
            stringResource(R.string.walkie_speaking, state.floor.speakerName)

        state.isTransmitting -> stringResource(R.string.walkie_on_air)

        state.connection == WalkieConnection.Disconnected ->
            stringResource(R.string.walkie_disconnected)

        state.connection == WalkieConnection.Connecting ->
            stringResource(R.string.walkie_connecting)

        // The honest label. No transport is attached, and the widget says so rather than
        // letting a volunteer believe a keyed mic was heard.
        state.isSimulated -> stringResource(R.string.walkie_no_transport)

        else -> stringResource(R.string.walkie_members, channel.memberCount)
    }
}

/**
 * Voice activity, drawn as a mirrored bar waveform.
 *
 * Bars rather than a continuous trace: at this width a polyline turns to mush, and a bar
 * that is either tall or short survives being glanced at. Idle state is a flat row of stubs
 * rather than an empty box, so the control keeps its height and the layout never jumps when
 * someone keys the mic.
 */
@Composable
private fun VoiceActivityWaveform(
    levels: List<Float>,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val idleAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.45f,
        animationSpec = tween(200),
        label = "waveformAlpha",
    )

    val description = stringResource(
        if (active) R.string.cd_walkie_active else R.string.cd_walkie_idle,
    )

    Canvas(
        modifier = modifier.semantics { contentDescription = description },
    ) {
        val barCount = WAVEFORM_BAR_COUNT
        val gap = size.width / (barCount * 2.2f)
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val centreY = size.height / 2f
        val maxHalf = size.height / 2f

        repeat(barCount) { index ->
            // Newest sample on the right: the waveform scrolls the way a timeline reads.
            val level = levels.getOrNull(levels.size - barCount + index) ?: 0f
            val half = (maxHalf * level).coerceAtLeast(barWidth / 2f)
            val x = index * (barWidth + gap) + barWidth / 2f

            drawLine(
                color = color.copy(alpha = idleAlpha),
                start = Offset(x, centreY - half),
                end = Offset(x, centreY + half),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Press and hold to talk.
 *
 * `detectTapGestures(onPress)` rather than a `Button`, because a button fires on release
 * and a radio has to open the mic on the way down. `awaitRelease` guarantees the mic closes
 * on both a normal release and a cancelled gesture — a scroll that steals the pointer must
 * not leave the channel keyed open.
 *
 * TalkBack cannot express press-and-hold, so the control also carries a plain
 * contentDescription and an explicit label; assistive users get the state readout even
 * though the gesture itself is not reachable. Wiring a latching toggle for that case is
 * tracked with the real transport work.
 */
@Composable
private fun PushToTalkButton(
    state: WalkieUiState,
    onStartTransmit: () -> Unit,
    onStopTransmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val enabled = state.canTransmit

    val container by animateColorAsState(
        targetValue = when {
            state.isTransmitting -> colors.critical
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            else -> colors.brandSolid
        },
        animationSpec = tween(120),
        label = "pttContainer",
    )
    val content = when {
        state.isTransmitting -> colors.onCritical
        !enabled -> colors.textMuted
        else -> colors.onBrandSolid
    }

    // A 2% swell on key-down. Enough to confirm the press through a glove, small enough
    // that it cannot be mistaken for the layout moving.
    val scale by animateFloatAsState(
        targetValue = if (state.isTransmitting) 1.02f else 1f,
        animationSpec = tween(120),
        label = "pttScale",
    )

    val label = when {
        state.isTransmitting -> stringResource(R.string.walkie_on_air)
        state.isReceiving -> stringResource(R.string.walkie_busy)
        !enabled -> stringResource(R.string.walkie_unavailable)
        else -> stringResource(R.string.walkie_hold_to_talk)
    }

    Box(
        modifier = modifier
            .height(Dimens.PttButtonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(container)
            .semantics { contentDescription = label }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onStartTransmit()
                        // Returns on release *or* cancellation; either way the mic closes.
                        tryAwaitRelease()
                        onStopTransmit()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Icon(
                imageVector = if (state.isReceiving) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Mic,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(Dimens.IconMd),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}
