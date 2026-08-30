package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.utils.formatDistance
import com.varisahayak.core.utils.formatRelativeTime
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.usecase.distanceMetresTo

/**
 * Actions that can be performed on an incident from a card.
 */
data class IncidentQuickActions(
    val onAcknowledge: (() -> Unit)? = null,
    val onAccept: (() -> Unit)? = null,
    val onEnRoute: (() -> Unit)? = null,
    val onResolve: (() -> Unit)? = null,
    val onViewMap: (() -> Unit)? = null,
    val onContact: (() -> Unit)? = null,
    val onEscalate: (() -> Unit)? = null,
)

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
 * @param actions the available quick actions for this incident.
 */
@Composable
fun IncidentCard(
    incident: Incident,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    myLocation: GeoPoint? = null,
    assigneeInitials: String? = null,
    actions: IncidentQuickActions = IncidentQuickActions(),
) {
    val colors = VariTheme.colors
    val isSos = incident.isSos

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
            // Header: Priority, SOS marker, and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriorityBadge(priority = incident.priority)

                if (isSos) {
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

            // Body: Category, Description and Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val categoryLabel = stringResource(incident.category.labelRes())
                    val title = if (isSos) {
                        stringResource(R.string.alert_tpl_sos_critical_title)
                    } else {
                        when (incident.category) {
                            com.varisahayak.domain.model.IncidentCategory.MEDICAL -> stringResource(R.string.alert_tpl_medical_title)
                            com.varisahayak.domain.model.IncidentCategory.LOST_PERSON -> stringResource(R.string.alert_tpl_lost_person_title)
                            com.varisahayak.domain.model.IncidentCategory.CROWD_SURGE -> stringResource(R.string.alert_tpl_crowd_surge_title)
                            else -> categoryLabel
                        }
                    }
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSos) colors.critical else colors.textPrimary,
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

            // Meta: Distance, Time, Landmark
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

                if (incident.sosBridgeToken != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.alert_id, incident.sosBridgeToken),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (incident.syncState.needsSync) {
                    SyncBadge(syncState = incident.syncState)
                }
            }

            // Actions Row
            QuickActionRow(
                incident = incident,
                actions = actions,
                modifier = Modifier.padding(top = Dimens.SpaceSm)
            )
        }
    }
}

@Composable
private fun QuickActionRow(
    incident: Incident,
    actions: IncidentQuickActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        // Map is almost always useful
        if (actions.onViewMap != null) {
            OutlinedAction(
                text = stringResource(R.string.action_view_location),
                icon = Icons.Filled.Map,
                onClick = actions.onViewMap,
                modifier = Modifier.weight(1f)
            )
        }

        // Status-based primary actions
        when (incident.status) {
            IncidentStatus.REPORTED, IncidentStatus.TRIAGED -> {
                if (actions.onAcknowledge != null) {
                    PrimaryAction(
                        text = stringResource(R.string.action_acknowledge),
                        icon = Icons.Filled.ThumbUp,
                        onClick = actions.onAcknowledge,
                        modifier = Modifier.weight(1f),
                        isSos = incident.isSos
                    )
                } else if (actions.onAccept != null) {
                    PrimaryAction(
                        text = stringResource(R.string.action_accept_respond),
                        icon = Icons.Filled.CheckCircle,
                        onClick = actions.onAccept,
                        modifier = Modifier.weight(1f),
                        isSos = incident.isSos
                    )
                }
            }
            IncidentStatus.ASSIGNED -> {
                if (actions.onAccept != null) {
                    PrimaryAction(
                        text = stringResource(R.string.action_accept_respond),
                        icon = Icons.Filled.CheckCircle,
                        onClick = actions.onAccept,
                        modifier = Modifier.weight(1f),
                        isSos = incident.isSos
                    )
                }
            }
            IncidentStatus.ACCEPTED -> {
                if (actions.onEnRoute != null) {
                    PrimaryAction(
                        text = stringResource(R.string.action_en_route),
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        onClick = actions.onEnRoute,
                        modifier = Modifier.weight(1f),
                        isSos = incident.isSos
                    )
                }
            }
            IncidentStatus.IN_PROGRESS -> {
                if (actions.onResolve != null) {
                    PrimaryAction(
                        text = stringResource(R.string.action_mark_resolved),
                        icon = Icons.Filled.CheckCircle,
                        onClick = actions.onResolve,
                        modifier = Modifier.weight(1f),
                        isSos = incident.isSos
                    )
                }
            }
            else -> { /* No primary actions for terminal states */ }
        }
        
        // Contact as a tertiary action if space allows or as a fallback
        if (actions.onContact != null && (incident.status == IncidentStatus.ASSIGNED || incident.status == IncidentStatus.ACCEPTED || incident.status == IncidentStatus.IN_PROGRESS)) {
             OutlinedAction(
                text = "", // Icon only if space is tight
                icon = Icons.Filled.Call,
                onClick = actions.onContact,
                modifier = Modifier.size(Dimens.MinTouchTarget)
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSos: Boolean = false
) {
    val colors = VariTheme.colors
    VariActionButton(
        text = text,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        destructive = isSos
    )
}

@Composable
private fun OutlinedAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VariTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dimens.MinTouchTarget),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = Dimens.SpaceSm),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textSecondary
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSm)
        )
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(Dimens.SpaceXs))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
