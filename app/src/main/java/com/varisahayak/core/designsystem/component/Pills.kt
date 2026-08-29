package com.varisahayak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme

/**
 * Pills: compact, fully-rounded state readouts.
 *
 * A pill is never a button dressed down. It reports a fact — connectivity, language,
 * pending work — and only becomes tappable when acting on that fact is the obvious next
 * move (switch language, retry sync now).
 */

@Composable
fun StatusPill(
    text: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingDot: Boolean = false,
    pulsingDot: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(Dimens.CornerPill)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val highlightColor by animateColorAsState(
        targetValue = if (isPressed) tone.content.copy(alpha = 0.15f) else Color.Transparent,
        label = "pill_highlight"
    )

    val clickModifier = if (onClick != null) {
        // The pill itself stays visually compact; the touch target does not.
        Modifier
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .defaultMinSize(minHeight = Dimens.MinTouchTarget)
    } else {
        Modifier
    }

    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickModifier)
            .then(semantics)
            .background(highlightColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(Dimens.PillHeight)
                .clip(shape)
                .background(tone.container)
                .border(
                    BorderStroke(
                        if (isPressed) 1.dp else Dimens.Hairline,
                        if (isPressed) tone.content else tone.border
                    ),
                    shape
                )
                .padding(horizontal = Dimens.PillPaddingH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            if (leadingDot) {
                StatusDot(tone = tone, pulsing = pulsingDot)
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.content,
                    modifier = Modifier.size(Dimens.IconSm),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = tone.content,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A 6dp state dot.
 *
 * It pulses only while something is genuinely in flight. A permanently animating indicator
 * teaches people to ignore it, which is the opposite of what a sync state is for.
 */
@Composable
private fun StatusDot(
    tone: BadgeTone,
    pulsing: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "dotPulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotAlpha",
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(6.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(tone.content),
    )
}

/**
 * Connectivity, phrased the way the field needs to hear it.
 *
 * Offline is not an error on this route — it is the normal condition between towns. The
 * offline copy therefore says the local queue is ready, not that something failed.
 */
@Composable
fun NetworkStatusPill(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    StatusPill(
        text = if (isOnline) {
            stringResource(R.string.network_online)
        } else {
            stringResource(R.string.network_local_sync_ready)
        },
        tone = if (isOnline) colors.successTone() else colors.warningTone(),
        leadingDot = true,
        modifier = modifier,
    )
}

/**
 * Unsynced local reports.
 *
 * Deliberately not a banner: it must not push the map or the queue down by a row every
 * time connectivity flickers. It floats, it is tappable to force a retry, and it
 * disappears at zero.
 */
@Composable
fun OfflineQueuePill(
    unsyncedCount: Int,
    isOnline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (unsyncedCount <= 0) return

    val colors = VariTheme.colors
    val label = pluralStringResource(
        R.plurals.sync_waiting_count,
        unsyncedCount,
        unsyncedCount,
    )

    StatusPill(
        text = label,
        // Online means WorkManager is already retrying; offline means it is parked. Same
        // fact, two different reasons to look at it.
        tone = if (isOnline) colors.infoTone() else colors.warningTone(),
        icon = if (isOnline) Icons.Filled.CloudQueue else Icons.Filled.CloudOff,
        leadingDot = true,
        pulsingDot = isOnline,
        onClick = onRetry,
        contentDescription = stringResource(R.string.cd_sync_retry, label),
        modifier = modifier,
    )
}

/** The manual "retry now" affordance, for lists that have room for a full control. */
@Composable
fun RetrySyncPill(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    StatusPill(
        text = stringResource(R.string.sync_retry_now),
        tone = colors.infoTone(),
        icon = Icons.Filled.Refresh,
        onClick = onRetry,
        modifier = modifier,
    )
}
