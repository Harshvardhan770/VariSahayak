package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NearMe
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
import androidx.compose.ui.text.style.TextOverflow
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.utils.formatDistance
import com.varisahayak.core.utils.formatRelativeTime
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.usecase.distanceMetresTo

/**
 * The triage card.
 *
 * Reading order is the order a responder decides in: *how bad*, *what*, *how far*, *how
 * long ago*, then the action. Priority leads because it is the only field that can make
 * the rest irrelevant.
 *
 * @param myLocation when present, the card shows real separation. When absent it shows
 *   nothing rather than a placeholder — an unknown distance rendered as "—" invites
 *   someone to read it as "near".
 * @param onAction the primary action. Null renders the card without one, for read-only
 *   contexts like the volunteer feed.
 */
@Composable
fun IncidentCard(
    incident: Incident,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    myLocation: GeoPoint? = null,
    assigneeInitials: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = VariTheme.colors

    OperationalCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        accentEdge = AccentEdge(incident.priority.solidColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriorityBadge(priority = incident.priority)

                // An SOS is not a priority level, it is a different kind of record. It gets
                // its own marker so it cannot be read as "just another critical".
                if (incident.isSos) {
                    LabelledBadge(
                        text = stringResource(R.string.badge_sos),
                        icon = Icons.Filled.Campaign,
                        tone = colors.criticalTone(),
                        contentDescription = null,
                    )
                }

                Spacer(Modifier.weight(1f))

                StatusChip(status = incident.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(incident.category.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = incident.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (assigneeInitials != null) {
                    ResponderAvatar(initials = assigneeInitials)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val separation = myLocation?.let { mine ->
                    incident.location?.let { theirs -> mine.distanceMetresTo(theirs) }
                }
                if (separation != null) {
                    MetaItem(
                        icon = Icons.Filled.NearMe,
                        text = formatDistance(separation),
                        emphasised = true,
                    )
                }

                MetaItem(
                    icon = Icons.Filled.Schedule,
                    text = formatRelativeTime(incident.reportedAtEpochMillis, nowMillis),
                )

                Spacer(Modifier.weight(1f))

                // Sync state only appears when it is not the boring answer. A "Synced"
                // badge on every row is noise that trains people to stop reading badges.
                if (incident.syncState.needsSync) {
                    SyncBadge(syncState = incident.syncState)
                }
            }

            if (actionLabel != null && onAction != null) {
                VariActionButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    destructive = incident.isSos,
                )
            }
        }
    }
}

/** Icon plus value. The unit of metadata on a card. */
@Composable
private fun MetaItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    val colors = VariTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasised) colors.textSecondary else colors.textMuted,
            modifier = Modifier.size(Dimens.IconSm),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            // Distance is a decision input; time is context. They should not weigh the same.
            color = if (emphasised) colors.textPrimary else colors.textMuted,
        )
    }
}

/**
 * Who currently owns this incident.
 *
 * Initials rather than a photo: there is no avatar storage in this app, and a generated
 * cartoon face would be decoration standing in for information. Two letters at least
 * answer "is this mine or someone else's" at a glance.
 */
@Composable
fun ResponderAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dimens.AvatarMd,
) {
    val colors = VariTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.brandSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onBrandSubtle,
        )
    }
}

/** Compact separator used between stacked incident rows in the drawer. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    val colors = VariTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.Hairline)
            .background(colors.cardBorder),
    )
}
