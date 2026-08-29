package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
