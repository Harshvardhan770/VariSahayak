package com.varisahayak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.domain.model.IncidentCategory

/** The icon for a category, used by chips, cards and the map legend alike. */
fun IncidentCategory.icon(): ImageVector = when (this) {
    IncidentCategory.MEDICAL -> Icons.Filled.LocalHospital
    IncidentCategory.WATER -> Icons.Filled.WaterDrop
    IncidentCategory.LOST_PERSON -> Icons.Filled.PersonSearch
    IncidentCategory.BLOCKED_ROAD -> Icons.Filled.Block
    IncidentCategory.SANITATION -> Icons.Filled.CleanHands
    IncidentCategory.CROWD_SURGE -> Icons.Filled.Groups
    IncidentCategory.OTHER -> Icons.AutoMirrored.Filled.HelpOutline
}

/**
 * A filter chip.
 *
 * Selection is carried by fill, border, *and* a check glyph — three channels, because this
 * control sits on top of a map where the background behind any given chip is unpredictable
 * and a tint alone cannot be relied on to read.
 */
@Composable
fun VariFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
) {
    val colors = VariTheme.colors
    val shape = RoundedCornerShape(Dimens.CornerPill)

    val container by animateColorAsState(
        targetValue = if (selected) colors.brandSolid else colors.glassSurface,
        animationSpec = tween(150),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onBrandSolid else colors.textSecondary,
        animationSpec = tween(150),
        label = "chipContent",
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.brandSolid else colors.glassBorder,
        animationSpec = tween(150),
        label = "chipBorder",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = Dimens.MinTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(Dimens.ChipHeight)
                .clip(shape)
                .background(container)
                .border(BorderStroke(Dimens.Hairline, border), shape)
                .padding(horizontal = Dimens.ChipPaddingH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            val leading = if (selected) Icons.Filled.Check else icon
            if (leading != null) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(Dimens.IconSm),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
            // The count is the reason to tap a filter at all — it is what tells a
            // coordinator where the work actually is.
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                )
            }
        }
    }
}

/**
 * The map's category filter row.
 *
 * Horizontally scrollable rather than wrapped: on the map this floats over content, and a
 * row that silently grows to two lines would cover the thing the user is looking at.
 *
 * @param counts incidents currently visible per category; drives the numeral on each chip.
 */
@Composable
fun CategoryFilterRow(
    selected: Set<IncidentCategory>,
    counts: Map<IncidentCategory, Int>,
    onToggle: (IncidentCategory) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.FloatingInset),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VariFilterChip(
            label = stringResource(R.string.filter_all),
            selected = selected.isEmpty(),
            onClick = onClear,
            count = counts.values.sum().takeIf { it > 0 },
        )

        // Only categories that actually have something on the map. A filter for a category
        // with nothing in it is a control that can only ever empty the screen.
        IncidentCategory.entries
            .filter { counts.getOrDefault(it, 0) > 0 || it in selected }
            .forEach { category ->
                VariFilterChip(
                    label = stringResource(category.labelRes()),
                    selected = category in selected,
                    onClick = { onToggle(category) },
                    icon = category.icon(),
                    count = counts.getOrDefault(category, 0).takeIf { it > 0 },
                )
            }
    }
}
