package com.varisahayak.core.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * A small runtime-permission holder for Compose.
 *
 * Accompanist is not used — this app needs only two permission groups, and hand-rolling
 * them avoids another dependency to keep version-aligned.
 *
 * The behaviour that matters: after a denial, `shouldShowRequestPermissionRationale`
 * returning false means the user chose "don't ask again". Re-requesting then does nothing
 * at all — the system silently drops it — so the UI must send them to app settings
 * instead of showing a button that appears broken.
 */
@Immutable
data class PermissionState(
    val granted: Set<String>,
    val shouldShowRationale: Boolean,
    val hasBeenRequested: Boolean,
) {
    fun isGranted(permission: String): Boolean = permission in granted

    val isAnyGranted: Boolean get() = granted.isNotEmpty()

    /**
     * True only once a request has actually been made and come back denied with no
     * rationale available. Before the first request this is always false — a fresh
     * install also reports "no rationale", and treating that as permanent denial would
     * send a first-time user straight to settings.
     */
    fun isPermanentlyDenied(permissions: List<String>): Boolean =
        hasBeenRequested && !shouldShowRationale && permissions.none { it in granted }
}

class PermissionController internal constructor(
    private val context: Context,
    private val permissions: List<String>,
    private val onRequest: () -> Unit,
    val state: PermissionState,
) {
    fun request() = onRequest()

    val isPermanentlyDenied: Boolean get() = state.isPermanentlyDenied(permissions)

    /** The only route back once a permission has been permanently denied. */
    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}

@Composable
fun rememberPermissionController(
    permissions: List<String>,
    onResult: (Map<String, Boolean>) -> Unit = {},
): PermissionController {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var granted by remember {
        mutableStateOf(permissions.filter { context.isPermissionGranted(it) }.toSet())
    }
    var hasBeenRequested by remember { mutableStateOf(false) }
    var shouldShowRationale by remember {
        mutableStateOf(activity.shouldShowRationaleForAny(permissions))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.filterValues { it }.keys
        hasBeenRequested = true
        // Re-read after the result: the flag only becomes meaningful post-denial.
        shouldShowRationale = activity.shouldShowRationaleForAny(permissions)
        onResult(result)
    }

    return PermissionController(
        context = context,
        permissions = permissions,
        onRequest = { launcher.launch(permissions.toTypedArray()) },
        state = PermissionState(
            granted = granted,
            shouldShowRationale = shouldShowRationale,
            hasBeenRequested = hasBeenRequested,
        ),
    )
}

private fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Activity?.shouldShowRationaleForAny(permissions: List<String>): Boolean {
    val activity = this ?: return false
    return permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

/** Compose's LocalContext is often a ContextWrapper rather than the Activity itself. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
