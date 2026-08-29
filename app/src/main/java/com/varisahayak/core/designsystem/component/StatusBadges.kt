package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariColors
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.SyncState

/**
 * Status surfaces.
 *
 * Every badge here carries an icon *and* a text label alongside its colour. The product
 * requirements forbid communicating priority by colour alone, and a volunteer reading a
 * screen in direct sunlight through a cracked protector is exactly the case that rule
 * exists for.
 *
 * Each badge is a tinted container, a darker label, and a hairline in between. The
 * hairline is what keeps a pale container from dissolving into a white card in
 * sunlight — it is doing real work, not decoration.
 */

/** A container / content / border triple. One severity, resolved once. */
@Immutable
data class BadgeTone(
    val container: Color,
    val content: Color,
    val border: Color,
)

/** Critical / SOS. */
fun VariColors.criticalTone() = BadgeTone(criticalContainer, onCriticalContainer, criticalBorder)

/** High / priority. */
fun VariColors.warningTone() = BadgeTone(warningContainer, onWarningContainer, warningBorder)

/** In-progress / dispatched. */
fun VariColors.infoTone() = BadgeTone(infoContainer, onInfoContainer, infoBorder)

/** Resolved / active. */
fun VariColors.successTone() = BadgeTone(successContainer, onSuccessContainer, successBorder)

@Composable
fun IncidentPriority.tone(): BadgeTone {
    val colors = VariTheme.colors
    return when (this) {
        IncidentPriority.CRITICAL -> colors.criticalTone()
        IncidentPriority.HIGH -> colors.warningTone()
        IncidentPriority.MEDIUM -> colors.infoTone()
        IncidentPriority.LOW -> BadgeTone(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            border = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** The solid fill for a priority — map pins and severity bars, not text. */
@Composable
fun IncidentPriority.solidColor(): Color {
    val colors = VariTheme.colors
    return when (this) {
        IncidentPriority.CRITICAL -> colors.critical
        IncidentPriority.HIGH -> colors.warning
        IncidentPriority.MEDIUM -> colors.info
        IncidentPriority.LOW -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun PriorityBadge(
    priority: IncidentPriority,
    modifier: Modifier = Modifier,
) {
    val tone = priority.tone()
    val icon = when (priority) {
        IncidentPriority.CRITICAL -> Icons.Filled.PriorityHigh
        IncidentPriority.HIGH -> Icons.Filled.Warning
        IncidentPriority.MEDIUM, IncidentPriority.LOW -> Icons.Filled.Info
    }

    val label = stringResource(priority.labelRes())

    LabelledBadge(
        text = label,
        icon = icon,
        tone = tone,
        contentDescription = stringResource(R.string.cd_priority_badge, label),
        modifier = modifier,
    )
}

@Composable
fun SyncBadge(
    syncState: SyncState,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val (tone, icon) = when (syncState) {
        SyncState.PENDING -> colors.warningTone() to Icons.Filled.CloudOff
        SyncState.SYNCING -> colors.infoTone() to Icons.Filled.CloudUpload
        SyncState.SYNCED -> colors.successTone() to Icons.Filled.Check
        SyncState.FAILED -> colors.criticalTone() to Icons.Filled.ErrorOutline
    }

    val label = stringResource(syncState.labelRes())

    LabelledBadge(
        text = label,
        icon = icon,
        tone = tone,
        contentDescription = stringResource(R.string.cd_sync_badge, label),
        modifier = modifier,
    )
}

@Composable
fun StatusChip(
    status: IncidentStatus,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val tone = when (status) {
        IncidentStatus.ESCALATED,
        IncidentStatus.REASSIGNMENT_REQUIRED,
        -> colors.criticalTone()

        IncidentStatus.RESOLVED -> colors.successTone()

        IncidentStatus.PENDING_SYNC -> colors.warningTone()

        // Everything on the dispatch line reads as one state: someone owns this.
        IncidentStatus.ASSIGNED,
        IncidentStatus.ACCEPTED,
        IncidentStatus.IN_PROGRESS,
        -> colors.infoTone()

        IncidentStatus.REPORTED,
        IncidentStatus.TRIAGED,
        IncidentStatus.CANCELLED,
        -> BadgeTone(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            border = MaterialTheme.colorScheme.outlineVariant,
        )
    }

    LabelledBadge(
        text = stringResource(status.labelRes()),
        icon = null,
        tone = tone,
        contentDescription = null,
        modifier = modifier,
    )
}

/**
 * The shared badge shell.
 *
 * Public because the top bar and the walkie widget need the same object with content that
 * is not a domain enum — a network state, a language code, a channel name.
 */
@Composable
fun LabelledBadge(
    text: String,
    icon: ImageVector?,
    tone: BadgeTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimens.CornerSm)
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(tone.container)
            .border(BorderStroke(Dimens.Hairline, tone.border), shape)
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs)
            .then(semantics),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
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
            // A badge is a single token and must never break. Without this, a badge squeezed
            // by a tight parent wraps one character per line rather than overflowing, which
            // is both unreadable and silently changes the height of whatever contains it.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
