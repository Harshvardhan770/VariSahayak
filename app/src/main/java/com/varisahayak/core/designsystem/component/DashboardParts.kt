package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.AccentTone
import com.varisahayak.core.designsystem.Accents
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.accent
import com.varisahayak.core.utils.formatDistance
import com.varisahayak.core.utils.formatRelativeTime
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.usecase.distanceMetresTo

/**
 * The repeated furniture of a role dashboard.
 *
 * Every one of these appears on three or more of the five role screens. Keeping them here
 * rather than per-screen is what stops "incidents near you" from drifting into five
 * slightly different rows that a user has to re-learn each time they change role.
 */

/** Section title with an optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = VariTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.info,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onAction)
                    // The label is small; the target it sits in is not.
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceMd),
            )
        }
    }
}

/** One entry in the quick-action grid. */
data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val tone: AccentTone,
    val onClick: () -> Unit,
)

/**
 * The quick-action row.
 *
 * Four across on a phone, each a square-ish card with a tinted glyph. Four is the ceiling:
 * a fifth drops each target below a comfortable thumb width at 360dp, and these are the
 * controls a volunteer uses while walking.
 */
@Composable
fun QuickActionGrid(
    actions: List<QuickAction>,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        actions.forEach { action ->
            OperationalCard(
                modifier = Modifier
                    .weight(1f)
                    .height(112.dp),
                onClick = action.onClick,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.SpaceSm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconPlate(icon = action.icon, tone = action.tone, size = 40.dp)
                    Box(Modifier.height(Dimens.SpaceSm))
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The one job in front of this responder right now.
 *
 * Tinted by category and given a leading severity edge, because unlike the rows below it
 * this card is not one of a list — it is the answer to "what am I doing", and it should be
 * impossible to mistake for a queue item.
 */
@Composable
fun ActiveAssignmentCard(
    incident: Incident,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    myLocation: GeoPoint? = null,
    locationLabel: String? = null,
    statusLabel: String? = null,
) {
    val colors = VariTheme.colors
    val tone = incident.category.accent()

    OperationalCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        accentEdge = AccentEdge(incident.priority.solidColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            IconPlate(icon = incident.category.icon(), tone = tone, size = 48.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    Text(
                        text = stringResource(incident.category.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    PriorityBadge(priority = incident.priority)
                }

                if (locationLabel != null) {
                    MetaRow(icon = Icons.Filled.LocationOn, text = locationLabel)
                }

                MetaRow(
                    icon = Icons.Filled.Schedule,
                    text = formatRelativeTime(incident.reportedAtEpochMillis, nowMillis),
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (statusLabel != null) {
                    LabelledBadge(
                        text = statusLabel,
                        icon = null,
                        tone = BadgeTone(
                            container = colors.warningContainer,
                            content = colors.onWarningContainer,
                            border = colors.warningBorder,
                        ),
                        contentDescription = null,
                    )
                }

                val separation = myLocation?.let { mine ->
                    incident.location?.let { theirs -> mine.distanceMetresTo(theirs) }
                }
                if (separation != null) {
                    Box(Modifier.height(Dimens.SpaceSm))
                    Text(
                        text = formatDistance(separation),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.info,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A compact incident row for "near you" lists.
 *
 * Deliberately lighter than [IncidentCard]: this is a browse list, not a dispatch queue.
 * It carries no action button, because the action lives on the detail screen and a row
 * that both navigates and dispatches gives a moving thumb two things to hit.
 */
@Composable
fun IncidentRow(
    incident: Incident,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    myLocation: GeoPoint? = null,
    locationLabel: String? = null,
) {
    val colors = VariTheme.colors
    val tone = incident.category.accent()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CornerMd))
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.SpaceSm, horizontal = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        IconPlate(icon = incident.category.icon(), tone = tone, size = 40.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(incident.category.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = locationLabel ?: incident.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatRelativeTime(incident.reportedAtEpochMillis, nowMillis),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
            )
            Box(Modifier.height(Dimens.SpaceXs))
            PriorityBadge(priority = incident.priority)
        }

        val separation = myLocation?.let { mine ->
            incident.location?.let { theirs -> mine.distanceMetresTo(theirs) }
        }
        if (separation != null) {
            Text(
                text = formatDistance(separation),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(Dimens.IconMd),
        )
    }
}

/**
 * A nearby responder.
 *
 * The availability dot is the point of this card. Name and capability tell you who; the
 * dot tells you whether calling them is worth doing, which is the only question being
 * asked when someone scans this row.
 */
@Composable
fun PersonCard(
    displayName: String,
    roleLabel: String,
    tone: AccentTone,
    modifier: Modifier = Modifier,
    capabilityLabel: String? = null,
    distanceLabel: String? = null,
    isAvailable: Boolean = false,
    isSelf: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = VariTheme.colors

    OperationalCard(modifier = modifier.width(148.dp), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(tone.container),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName.initials(),
                        style = MaterialTheme.typography.titleMedium,
                        color = tone.accent,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(colors.cardSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAvailable) colors.success else colors.textMuted),
                    )
                }
            }

            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = if (isSelf) stringResource(R.string.dashboard_you) else roleLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelf) colors.brandAccent else colors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (capabilityLabel != null) {
                LabelledBadge(
                    text = capabilityLabel,
                    icon = null,
                    tone = BadgeTone(
                        container = tone.container,
                        content = tone.accent,
                        border = tone.accent.copy(alpha = 0.25f),
                    ),
                    contentDescription = null,
                )
            }

            if (distanceLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = distanceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * What a panel shows when the feature behind it has no backend yet.
 *
 * This exists so a screen can carry its intended shape without inventing content. The
 * mockups show a supplies inventory and a service-health board; neither has a table, a
 * repository, or a health check behind it. Rendering "320 food packets" would be a
 * fabrication that a coordinator could act on, so the panel states plainly that it is not
 * connected and names what would connect it.
 */
@Composable
fun NotConnectedPanel(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = VariTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconPlate(icon = icon, tone = Accents.slate, size = 36.dp, iconSize = Dimens.IconSm)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun MetaRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
