package com.varisahayak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme

/**
 * The two surfaces this app is built out of.
 *
 * There are deliberately only two. A field interface that invents a third surface every
 * screen is one a responder has to re-read; two means "this floats over the map" and
 * "this is a record you can act on", and nothing else needs saying.
 */

/** The leading severity bar on a card. Colour comes from the caller's status mapping. */
@Immutable
data class AccentEdge(val color: Color)

private val AccentEdgeWidth = 3.dp

/**
 * A frosted panel that floats over the map.
 *
 * ## On the blur
 *
 * Compose has no backdrop blur. `Modifier.blur` and `RenderEffect` both blur a
 * composable's *own* content, not the pixels behind it — sampling the backdrop needs a
 * `RenderNode` capture (the approach the `haze` library takes) and costs a full-screen
 * readback every frame.
 *
 * That cost buys very little here. The map underneath is styled to a desaturated
 * grayscale, so an 85% white fill already flattens it to a near-solid, legible ground; the
 * blur would be spent smoothing detail that has been styled away. What actually does the
 * separating is the bright inner hairline
 * ([com.varisahayak.core.designsystem.VariColors.glassBorder]), which is why it is not
 * optional.
 *
 * If a device-class budget ever justifies real blur, it belongs here and only here.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.CornerCard),
    content: @Composable () -> Unit,
) {
    val colors = VariTheme.colors
    Box(
        modifier = modifier
            .shadow(
                elevation = Dimens.SpaceXs,
                shape = shape,
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow,
            )
            .clip(shape)
            .background(colors.glassSurface)
            .border(BorderStroke(Dimens.Hairline, colors.glassBorder), shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            content()
        }
    }
}

/**
 * An elevated operational card: a record someone can act on.
 *
 * The press affordance is a border darkening, not a lift or a scale. Anything that changes
 * a card's size reflows the list under a moving thumb, and a responder tapping "Dispatch"
 * on a scrolling queue cannot afford the target to move.
 *
 * @param onClick when null the card is inert — it renders identically but takes no input
 *   and reports no click affordance to TalkBack.
 * @param accentEdge draws a severity bar down the leading edge. A bar rather than a tinted
 *   card body: tinting the body would put every label on a coloured ground and cost the
 *   contrast the palette was built for.
 */
@Composable
fun OperationalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Dimens.CornerCard),
    accentEdge: AccentEdge? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VariTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isPressed) colors.cardBorderHover else colors.cardBorder,
        animationSpec = tween(durationMillis = 150),
        label = "cardBorder",
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = onClick,
        )
    } else {
        Modifier
    }

    // The severity bar is painted, not laid out.
    //
    // A child Box with fillMaxHeight() looks like the obvious way to draw it and is wrong:
    // inside a LazyColumn the incoming height constraint is unbounded, fillMaxHeight falls
    // back to minHeight, and the bar silently measures to zero. drawBehind runs after the
    // card has been measured, so it always gets the real height.
    val accentModifier = if (accentEdge != null) {
        Modifier.drawBehind {
            drawRect(
                color = accentEdge.color,
                size = Size(AccentEdgeWidth.toPx(), size.height),
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = Dimens.SpaceXs,
                shape = shape,
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadow,
            )
            .clip(shape)
            .background(colors.cardSurface)
            .then(accentModifier)
            .border(BorderStroke(Dimens.Hairline, borderColor), shape)
            .then(clickModifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            Column(content = content)
        }
    }
}
