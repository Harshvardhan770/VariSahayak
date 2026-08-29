package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
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
 */

@Composable
fun PriorityBadge(
    priority: IncidentPriority,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val (container, content, icon) = when (priority) {
        IncidentPriority.CRITICAL ->
            Triple(colors.criticalContainer, colors.onCriticalContainer, Icons.Filled.PriorityHigh)

        IncidentPriority.HIGH ->
            Triple(colors.warningContainer, colors.onWarningContainer, Icons.Filled.Warning)

        IncidentPriority.MEDIUM ->
            Triple(colors.infoContainer, colors.onInfoContainer, Icons.Filled.Info)

        IncidentPriority.LOW -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Info,
        )
    }

    val label = stringResource(priority.labelRes())

    LabelledBadge(
        text = label,
        icon = icon,
        containerColor = container,
        contentColor = content,
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
    val (container, content, icon) = when (syncState) {
        SyncState.PENDING ->
            Triple(colors.warningContainer, colors.onWarningContainer, Icons.Filled.CloudOff)

        SyncState.SYNCING ->
            Triple(colors.infoContainer, colors.onInfoContainer, Icons.Filled.CloudUpload)

        SyncState.SYNCED ->
            Triple(colors.successContainer, colors.onSuccessContainer, Icons.Filled.Check)

        SyncState.FAILED ->
            Triple(colors.criticalContainer, colors.onCriticalContainer, Icons.Filled.ErrorOutline)
    }

    val label = stringResource(syncState.labelRes())

    LabelledBadge(
        text = label,
        icon = icon,
        containerColor = container,
        contentColor = content,
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
    val (container, content) = when (status) {
        IncidentStatus.ESCALATED, IncidentStatus.REASSIGNMENT_REQUIRED ->
            colors.criticalContainer to colors.onCriticalContainer

        IncidentStatus.RESOLVED -> colors.successContainer to colors.onSuccessContainer

        IncidentStatus.PENDING_SYNC -> colors.warningContainer to colors.onWarningContainer

        else -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    }

    LabelledBadge(
        text = stringResource(status.labelRes()),
        icon = null,
        containerColor = container,
        contentColor = content,
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun LabelledBadge(
    text: String,
    icon: ImageVector?,
    containerColor: Color,
    contentColor: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(Dimens.CornerSm))
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs)
            .then(semantics),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Dimens.IconSm),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
