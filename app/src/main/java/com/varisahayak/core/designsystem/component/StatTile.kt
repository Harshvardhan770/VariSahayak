package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.varisahayak.core.designsystem.AccentTone
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme

/**
 * Stat surfaces.
 *
 * One rule governs all of them: the *number* is the content and it is always set in
 * [com.varisahayak.core.designsystem.VariColors.textPrimary]. The accent colour lives in
 * the icon plate and, at most, in a one-line caption underneath. A dashboard that colours
 * its numbers turns every tile into an alarm and leaves nothing to distinguish the tile
 * that actually is one.
 */

/**
 * A half-width tile: icon plate, label, big number, optional caption.
 *
 * The label sits above the value, not below. A coordinator scanning four tiles reads the
 * labels once to learn the layout and the numbers every time after — putting the label
 * second would make them re-read it on every glance.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tone: AccentTone,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = VariTheme.colors

    OperationalCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalAlignment = Alignment.Top,
        ) {
            IconPlate(icon = icon, tone = tone)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = captionColor ?: colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One entry in a [StatStrip]. */
@Immutable
data class CompactStat(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tone: AccentTone,
    val caption: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * A row of compact stats inside one card, separated by hairlines.
 *
 * Entries take equal weight rather than intrinsic width: the divider positions have to
 * stay put when a number goes from 9 to 10, or the whole strip twitches every time a count
 * changes.
 */
@Composable
fun StatStrip(
    stats: List<CompactStat>,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty()) return
    val colors = VariTheme.colors

    OperationalCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(Dimens.Hairline)
                            .background(colors.cardBorder),
                    )
                }
                CompactStatColumn(
                    stat = stat,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Centred rather than left-aligned: at strip width the icon, number and label share one
 * narrow column, and a ragged left edge reads as broken.
 */
@Composable
private fun CompactStatColumn(
    stat: CompactStat,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val description = listOfNotNull(stat.label, stat.value, stat.caption).joinToString(", ")

    Column(
        modifier = modifier
            .then(
                if (stat.onClick != null) {
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = stat.onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = Dimens.SpaceSm, horizontal = Dimens.SpaceXs)
            // One announcement per stat. Read as three separate nodes it becomes
            // "shield, eighteen, patrol tasks", which is not a sentence.
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        IconPlate(icon = stat.icon, tone = stat.tone, size = 36.dp, iconSize = Dimens.IconSm)

        Text(
            text = stat.value,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (stat.caption != null) {
            Text(
                text = stat.caption,
                style = MaterialTheme.typography.labelSmall,
                color = stat.tone.accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The round tinted plate behind a stat's glyph. */
@Composable
fun IconPlate(
    icon: ImageVector,
    tone: AccentTone,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = Dimens.IconMd,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.accent,
            modifier = Modifier.size(iconSize),
        )
    }
}
