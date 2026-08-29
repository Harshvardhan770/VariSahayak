package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.CornerMd),
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.PrimaryActionHeight),
    ) {
        ButtonContent(text = text, icon = icon)
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.CornerMd),
        modifier = modifier.defaultMinSize(minHeight = Dimens.MinTouchTarget),
    ) {
        ButtonContent(text = text, icon = icon)
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
    Button(
        onClick = onClick,
        enabled = enabled,
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
