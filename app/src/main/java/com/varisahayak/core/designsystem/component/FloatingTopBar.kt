package com.varisahayak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.locale.AppLocale
import com.varisahayak.domain.model.UserRole

/**
 * The floating command bar.
 *
 * It floats rather than docking because on the map screen the content underneath is the
 * point — a solid app bar would permanently occlude the top of the route. Frosted, inset
 * from the edges, and sized so that everything on it is state a coordinator needs
 * continuously: who they are, whether the device can reach the server, and whether the
 * radio is open.
 *
 * Nothing decorative is allowed on this bar. Every element either reports a fact or
 * changes one.
 */
@Composable
fun FloatingTopBar(
    title: String,
    role: UserRole?,
    isOnline: Boolean,
    locale: AppLocale,
    onLocaleChange: (AppLocale) -> Unit,
    walkieEnabled: Boolean,
    onToggleWalkie: () -> Unit,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
) {
    val colors = VariTheme.colors

    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                LanguageSwitcher(
                    selected = locale,
                    onSelect = onLocaleChange,
                )

                if (showDetails) {
                    WalkieToggle(
                        enabled = walkieEnabled,
                        onClick = onToggleWalkie,
                    )
                }
            }

            if (showDetails) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NetworkStatusPill(isOnline = isOnline)
                    if (role != null) {
                        RoleBadge(role = role)
                    }
                }
            }
        }
    }
}

/**
 * EN | MR | HI, as a segmented control.
 *
 * Segments rather than a dropdown: three options do not justify hiding two of them behind
 * a tap, and a volunteer handing the phone to a Marathi-speaking pilgrim needs the switch
 * to be visible, not discoverable.
 *
 * Each label is set in its own script — मराठी, not "Marathi" — because someone who cannot
 * read the current language cannot read the English name of their own.
 */
@Composable
fun LanguageSwitcher(
    selected: AppLocale,
    onSelect: (AppLocale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val shape = RoundedCornerShape(Dimens.CornerPill)

    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(Dimens.Hairline, colors.cardBorder), shape)
            .padding(SegmentGap),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLocale.selectable.forEach { option ->
            LanguageSegment(
                label = option.shortLabel,
                fullName = option.endonym,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun LanguageSegment(
    label: String,
    fullName: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VariTheme.colors
    val shape = RoundedCornerShape(Dimens.CornerPill)

    val container by animateColorAsState(
        targetValue = if (selected) colors.cardSurface else Color.Transparent,
        animationSpec = tween(150),
        label = "langContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textMuted,
        animationSpec = tween(150),
        label = "langContent",
    )

    Box(
        modifier = Modifier
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            // The segment reads compact but still meets the touch floor.
            .defaultMinSize(minWidth = Dimens.MinTouchTarget, minHeight = Dimens.MinTouchTarget)
            .background(if (selected) colors.brandSolid.copy(alpha = 0.1f) else Color.Transparent)
            // TalkBack announces the endonym, not the two-letter code: "MR" is a label for
            // the eye, not something anyone wants read aloud to them.
            .semantics { contentDescription = fullName },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(Dimens.PillHeight)
                .clip(shape)
                .background(container)
                .border(
                    if (selected) BorderStroke(1.dp, colors.brandBorder) else BorderStroke(0.dp, Color.Transparent),
                    shape
                )
                .padding(horizontal = Dimens.SpaceSm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

/**
 * Opens and closes the radio widget.
 *
 * Tinted when the channel is showing, neutral when it is not — a coordinator glancing down
 * should be able to tell whether the radio panel is on screen without looking for it.
 */
@Composable
private fun WalkieToggle(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors
    val shape = RoundedCornerShape(Dimens.CornerPill)

    val container by animateColorAsState(
        targetValue = if (enabled) colors.brandSubtle else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(150),
        label = "walkieToggleContainer",
    )
    val content = if (enabled) colors.onBrandSubtle else colors.textMuted
    val border = if (enabled) colors.brandBorder else colors.cardBorder

    Box(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .size(Dimens.MinTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.PillHeight)
                .clip(shape)
                .background(container)
                .border(BorderStroke(Dimens.Hairline, border), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Podcasts,
                contentDescription = stringResource(
                    if (enabled) R.string.walkie_hide else R.string.walkie_show,
                ),
                tint = content,
                modifier = Modifier.size(Dimens.IconSm),
            )
        }
    }
}

/** Inner gutter of the segmented language control. */
private val SegmentGap = 2.dp
