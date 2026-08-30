package com.varisahayak.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.AccentTone
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.accentTone
import com.varisahayak.domain.model.UserRole

/**
 * The dashboard header.
 *
 * Identity on the left, actions on the right, live status underneath. It is not an app bar
 * — it scrolls away with the content, because on a dashboard the header is the least
 * urgent thing on screen and pinning it would cost a row of the queue on every phone.
 *
 * The SOS control lives here rather than at the foot of the list for the opposite reason:
 * it is reachable in the first frame without scrolling, from every role's home screen, in
 * the same place every time.
 */
@Composable
fun DashboardHeader(
    displayName: String,
    role: UserRole,
    subtitle: String?,
    onNotifications: (() -> Unit)?,
    onWalkie: () -> Unit,
    onSos: () -> Unit,
    modifier: Modifier = Modifier,
    unreadNotifications: Int = 0,
    walkieActive: Boolean = false,
) {
    val colors = VariTheme.colors
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -20 }
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            RoleAvatar(displayName = displayName, role = role, size = 64.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_greeting, displayName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(stringResource(role.labelRes()), subtitle)
                        .joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
            ) {
                if (onNotifications != null) {
                    HeaderIconButton(
                        icon = Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.notifications_title),
                        onClick = onNotifications,
                        badgeCount = unreadNotifications,
                    )
                }

                HeaderIconButton(
                    icon = Icons.Filled.Podcasts,
                    contentDescription = stringResource(
                        if (walkieActive) R.string.walkie_hide else R.string.walkie_show,
                    ),
                    onClick = onWalkie,
                    tint = if (walkieActive) colors.onBrandSubtle else colors.textSecondary,
                    container = if (walkieActive) colors.brandSubtle else colors.cardSurface,
                )

                SosHeaderButton(onClick = onSos)
            }
        }
    }
}

/**
 * Initials on a role-tinted plate.
 *
 * The mockups show photographs. There is no avatar storage in this app and no way to get
 * one, and a stock illustration standing in for a real responder is worse than initials —
 * it invites someone to look for a face that is not theirs.
 */
@Composable
fun RoleAvatar(
    displayName: String,
    role: UserRole,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 52.dp,
) {
    val tone = role.accentTone()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.container)
            .border(BorderStroke(Dimens.Hairline, tone.accent.copy(alpha = 0.25f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayName.initials(),
            style = MaterialTheme.typography.titleMedium,
            color = tone.accent,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Up to two initials from a display name.
 *
 * Takes the first letter of the first and last words, so "Dr. Saurabh Patil" gives SP
 * rather than DS. Falls back to the first two characters for a single-word name, and to a
 * dash when there is nothing to work with — never to an empty circle, which reads as a
 * failed image load.
 */
internal fun String.initials(): String {
    val words = trim()
        .split(' ', '\t')
        .filter { it.isNotBlank() && it.trimEnd('.').length > 1 }

    return when {
        words.isEmpty() -> trim().take(2).uppercase().ifBlank { "—" }
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    tint: androidx.compose.ui.graphics.Color? = null,
    container: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = VariTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val highlightColor by animateColorAsState(
        targetValue = if (isPressed) (tint ?: colors.brandSolid).copy(alpha = 0.2f) else Color.Transparent,
        label = "icon_highlight"
    )

    Box(
        modifier = modifier
            .size(Dimens.MinTouchTarget)
            .background(highlightColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(container ?: colors.cardSurface)
                .border(BorderStroke(Dimens.Hairline, colors.cardBorder), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint ?: colors.textSecondary,
                modifier = Modifier.size(Dimens.IconMd),
            )
        }

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    // Overlaps the plate's top-right rather than sitting outside it, so the
                    // badge cannot push the row's height and shift the whole header.
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.critical),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onCritical,
                )
            }
        }
    }
}

/**
 * The header SOS control.
 *
 * A filled circle with the word underneath, not an icon button: SOS is the one action here
 * that must be identifiable without knowing the app, and a glyph alone is a guess.
 */
@Composable
private fun SosHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val label = stringResource(R.string.dashboard_raise_sos)

    Column(
        modifier = modifier
            .widthIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = Dimens.SpaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.critical),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.badge_sos),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onCritical,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The live-status row under the header: connectivity, then the radio channel.
 *
 * Scrolls horizontally so a long channel name never wraps the row onto a second line and
 * shifts everything below it.
 */
@Composable
fun DashboardStatusRow(
    isOnline: Boolean,
    channelName: String?,
    modifier: Modifier = Modifier,
    channelTone: AccentTone? = null,
    onChannelClick: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, 200)) + slideInVertically(tween(800, 200)) { 20 }
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetworkStatusPill(isOnline = isOnline)

            if (channelName != null) {
                val tone = channelTone ?: com.varisahayak.core.designsystem.Accents.blue
                StatusPill(
                    text = channelName,
                    tone = BadgeTone(
                        container = tone.container,
                        content = tone.accent,
                        border = tone.accent.copy(alpha = 0.25f),
                    ),
                    icon = Icons.Filled.Podcasts,
                    onClick = onChannelClick,
                )
            }
        }
    }
}
