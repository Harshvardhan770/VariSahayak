package com.varisahayak.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.common.AppError
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme

/**
 * The loading / empty / error / offline surfaces every screen is required to handle.
 *
 * [OfflineBanner] is intentionally reassuring rather than alarming: offline is a normal
 * operating mode on the route, and the message a volunteer needs is "your work is safe",
 * not "something failed".
 */

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.state_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceMd),
        )
    }
}

/**
 * A sequential three-dot loading animation, intended for use within buttons or
 * small inline containers.
 */
@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary
) {
    val transition = rememberInfiniteTransition(label = "loading_dots")

    val dotSize = 8.dp
    val spacing = 4.dp

    @Composable
    fun animateDotAlpha(delay: Int): State<Float> {
        return transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0.2f at delay
                    1.0f at delay + 400
                    0.2f at delay + 800
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_alpha_$delay"
        )
    }

    val alpha1 by animateDotAlpha(0)
    val alpha2 by animateDotAlpha(300)
    val alpha3 by animateDotAlpha(600)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(alpha = alpha1, color = color, size = dotSize)
        Dot(alpha = alpha2, color = color, size = dotSize)
        Dot(alpha = alpha3, color = color, size = dotSize)
    }
}

@Composable
private fun Dot(
    alpha: Float,
    color: Color,
    size: androidx.compose.ui.unit.Dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = color.copy(alpha = alpha)
    ) {}
}

@Composable
fun EmptyState(
    message: String = stringResource(R.string.state_empty),
    modifier: Modifier = Modifier,
) {
    MessageState(
        icon = Icons.Filled.Inbox,
        title = message,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ErrorState(
    error: AppError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val message = when (error) {
        is AppError.Offline -> stringResource(R.string.state_offline)
        is AppError.SessionExpired -> stringResource(R.string.auth_session_expired)
        is AppError.Unauthorised -> stringResource(R.string.auth_error_no_role)
        is AppError.Validation -> error.message
        else -> stringResource(R.string.state_error)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MessageState(
            icon = Icons.Filled.ErrorOutline,
            title = message,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier,
        )
        if (onRetry != null) {
            VariSecondaryButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.padding(top = Dimens.SpaceMd),
            )
        }
    }
}

/**
 * A persistent, non-blocking indicator. Shown above content rather than instead of it:
 * the local database still has everything the volunteer needs.
 */
@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier,
    detail: String? = stringResource(R.string.state_offline_detail),
) {
    val colors = VariTheme.colors
    Surface(
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
        shape = RoundedCornerShape(Dimens.CornerSm),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceSm),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconMd),
            )
            Column {
                Text(
                    text = stringResource(R.string.state_offline),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Dimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.IconLg),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
