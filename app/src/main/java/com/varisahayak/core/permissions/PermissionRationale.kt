package com.varisahayak.core.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.varisahayak.R

/**
 * Explains why a permission is being asked for, before asking again.
 *
 * The dismiss action is always genuinely dismissive — every permission in this app is
 * optional. A volunteer who declines location can still report incidents, and one who
 * declines camera can still enter a QR code by hand. A rationale dialog that traps the
 * user would be both hostile and, given the field context, actively harmful.
 */
@Composable
fun PermissionRationaleDialog(
    title: String,
    rationale: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.action_continue),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(rationale) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Shown when the permission has been permanently denied. The confirm action opens app
 * settings, because a further permission request would be silently dropped by the system.
 */
@Composable
fun PermissionPermanentlyDeniedDialog(
    title: String,
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.action_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
