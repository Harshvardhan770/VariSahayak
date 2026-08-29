package com.varisahayak.core.designsystem.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.varisahayak.core.designsystem.Dimens

/**
 * A shimmer effect modifier that creates a moving gradient over a component.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
    background(brush)
}

/**
 * A single shimmer bar, used to build skeleton loaders.
 */
@Composable
fun ShimmerItem(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 20.dp,
    widthFraction: Float = 1f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(Dimens.CornerSm)
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .shimmerEffect()
    )
}

/**
 * A skeleton loader that mimics the appearance of a text block or list item.
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
    ) {
        repeat(8) { index ->
            ShimmerItem(
                widthFraction = when (index % 4) {
                    0 -> 0.95f
                    1 -> 0.9f
                    2 -> 0.85f
                    else -> 0.7f
                },
                height = 16.dp
            )
        }
    }
}
