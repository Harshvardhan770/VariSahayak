package com.varisahayak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme

/**
 * Field-first buttons.
 *
 * All of these enforce at least [Dimens.MinTouchTarget]; the primary and SOS variants are
 * deliberately much larger. These are pressed while walking, one-handed, sometimes with
 * wet or dusty hands.
 */

@Composable
fun VariPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = VariTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    HighlightWrapper(isPressed = isPressed, color = colors.brandSolid, shape = RoundedCornerShape(Dimens.CornerMd)) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(Dimens.CornerMd),
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.brandSolid,
                contentColor = colors.onBrandSolid,
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(Dimens.PrimaryActionHeight),
        ) {
            ButtonContent(text = text, icon = icon)
        }
    }
}

/**
 * The in-card action: "Accept", "Dispatch", "Resolve".
 *
 * Exactly [Dimens.MinTouchTarget] tall and no shorter. This is the control a responder
 * hits under time pressure on a moving list, so it gets the floor, not a compact variant
 * of it.
 */
@Composable
fun VariActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = VariTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val color = if (destructive) colors.critical else colors.brandSolid

    HighlightWrapper(isPressed = isPressed, color = color, shape = RoundedCornerShape(Dimens.CornerMd)) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(Dimens.CornerMd),
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = if (destructive) colors.onCritical else colors.onBrandSolid,
            ),
            modifier = modifier.defaultMinSize(minHeight = Dimens.MinTouchTarget),
        ) {
            ButtonContent(text = text, icon = icon)
        }
    }
}

@Composable
fun VariSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = VariTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    HighlightWrapper(isPressed = isPressed, color = colors.brandSolid, shape = RoundedCornerShape(Dimens.CornerMd)) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(Dimens.CornerMd),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
            border = BorderStroke(Dimens.Hairline, colors.cardBorderHover),
            modifier = modifier.defaultMinSize(minHeight = Dimens.MinTouchTarget),
        ) {
            ButtonContent(text = text, icon = icon)
        }
    }
}

/**
 * The SOS control. Visually unmistakable and larger than anything else on the screen —
 * raising an emergency must never require finding a small target.
 *
 * The caller is responsible for confirming the action before it fires; this composable
 * only raises the intent.
 */
@Composable
fun SosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = VariTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    HighlightWrapper(isPressed = isPressed, color = colors.critical, shape = RoundedCornerShape(Dimens.CornerLg)) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(Dimens.CornerLg),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.critical,
                contentColor = colors.onCritical,
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(Dimens.SosActionHeight),
        ) {
            ButtonContent(text = text, icon = Icons.Filled.Campaign, large = true)
        }
    }
}

/**
 * A wrapper that shows a highlighted background when the content is pressed.
 */
@Composable
private fun HighlightWrapper(
    isPressed: Boolean,
    color: Color,
    shape: Shape,
    content: @Composable () -> Unit
) {
    val highlightColor by animateColorAsState(
        targetValue = if (isPressed) color.copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = tween(150),
        label = "button_highlight"
    )

    Box(
        modifier = Modifier
            .background(highlightColor, shape)
            .padding(if (isPressed) 4.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ButtonContent(
    text: String,
    icon: ImageVector?,
    large: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (large) Dimens.IconLg else Dimens.IconMd),
            )
        }
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = if (large) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
        )
    }
}
