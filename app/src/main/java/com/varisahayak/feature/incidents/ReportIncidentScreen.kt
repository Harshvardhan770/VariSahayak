package com.varisahayak.feature.incidents

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.common.AppError
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.OfflineBanner
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.permissions.AppPermissions
import com.varisahayak.core.permissions.rememberPermissionController
import com.varisahayak.domain.model.IncidentCategory

/**
 * Fast incident capture.
 *
 * Optimised for the number of taps, not the completeness of the form: category, a line of
 * text, submit. Location is captured in the background and never gates the button —
 * "reported roughly here, now" beats "reported precisely, two minutes later".
 */
@Composable
fun ReportIncidentScreen(
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportIncidentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Nothing on this screen used to ask for location, and the ViewModel starts capturing
    // the moment it is constructed. On a device where the permission had never been
    // granted — which is every device that has not opened the map first — that capture
    // could only ever fail, and the form silently reported "location unavailable" with a
    // Retry button that asked the same unanswerable question again.
    //
    // Asked here rather than at sign-in because this is the moment it means something:
    // the volunteer is filing a report whose whole value is where it happened.
    val permissions = rememberPermissionController(AppPermissions.LOCATION) { result ->
        // Re-capture on the grant, not just on a denial dismissal. The failed attempt from
        // init is already on screen, and without this the volunteer would have to press
        // Retry to get the fix they just authorised.
        if (result.values.any { it }) viewModel.captureLocation()
    }

    var hasAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!permissions.state.isAnyGranted && !hasAsked) {
            hasAsked = true
            permissions.request()
        }
    }

    LaunchedEffect(uiState.savedClientId) {
        uiState.savedClientId?.let(onSaved)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        if (uiState.isOffline) {
            OfflineBanner()
        }

        if (uiState.isSos) {
            Text(
                text = stringResource(R.string.sos_bridge_title),
                style = MaterialTheme.typography.headlineSmall,
                color = VariTheme.colors.critical,
            )
        }

        Text(
            text = stringResource(R.string.report_category),
            style = MaterialTheme.typography.titleMedium,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            IncidentCategory.entries.forEach { category ->
                FilterChip(
                    selected = uiState.category == category,
                    onClick = { viewModel.onCategoryChanged(category) },
                    label = { Text(stringResource(category.labelRes())) },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.MinTouchTarget),
                )
            }
        }

        OutlinedTextField(
            value = uiState.description,
            onValueChange = viewModel::onDescriptionChanged,
            label = { Text(stringResource(R.string.report_description)) },
            placeholder = { Text(stringResource(R.string.report_description_hint)) },
            minLines = 3,
            isError = (uiState.error as? AppError.Validation)
                ?.field == "description",
            modifier = Modifier.fillMaxWidth(),
        )

        // Free text about the affected person is optional and deliberately unprompted for
        // detail — the less personal data captured on a device in a crowd, the better.
        OutlinedTextField(
            value = uiState.affectedPersonNote,
            onValueChange = viewModel::onAffectedPersonNoteChanged,
            label = { Text(stringResource(R.string.report_affected_person)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LocationStatusLine(
            state = uiState.locationState,
            isPermanentlyDenied = permissions.isPermanentlyDenied,
            onRetry = viewModel::captureLocation,
            onGrantPermission = permissions::request,
            onOpenSettings = permissions::openAppSettings,
            onOpenLocationSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                )
            },
        )

        uiState.error?.let { error ->
            val message = (error as? AppError.Validation)?.message
                ?: stringResource(R.string.state_error)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        VariPrimaryButton(
            text = stringResource(R.string.report_submit),
            onClick = viewModel::submit,
            enabled = !uiState.isSubmitting,
        )

        Text(
            text = stringResource(R.string.report_saved_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The location line, and the one action that can resolve whatever it is reporting.
 *
 * Each failure gets the button that fixes it. Offering Retry against a denied permission
 * is what made this look broken: the button worked perfectly and changed nothing, because
 * the answer to "may I have your location" was already no and nothing was re-asking.
 */
@Composable
private fun LocationStatusLine(
    state: LocationCaptureState,
    isPermanentlyDenied: Boolean,
    onRetry: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val text = when (state) {
        LocationCaptureState.Idle,
        LocationCaptureState.Capturing,
        -> stringResource(R.string.report_location_capturing)

        LocationCaptureState.Captured -> stringResource(R.string.report_location)
        LocationCaptureState.Approximate -> stringResource(R.string.permission_location_coarse_only)
        LocationCaptureState.PermissionRequired ->
            stringResource(R.string.permission_location_denied)

        LocationCaptureState.LocationOff -> stringResource(R.string.permission_location_disabled)
        LocationCaptureState.Unavailable -> stringResource(R.string.report_location_unavailable)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )

    when (state) {
        // "Don't ask again" makes the system drop a re-request silently, so the only
        // honest button left is the one that opens app settings.
        LocationCaptureState.PermissionRequired -> VariSecondaryButton(
            text = if (isPermanentlyDenied) {
                stringResource(R.string.action_open_settings)
            } else {
                stringResource(R.string.permission_location_grant)
            },
            onClick = if (isPermanentlyDenied) onOpenSettings else onGrantPermission,
        )

        // Not app settings: the device-wide location toggle is a different screen, and
        // sending the volunteer to the app's permission page would show them a permission
        // that is already granted.
        LocationCaptureState.LocationOff -> VariSecondaryButton(
            text = stringResource(R.string.permission_location_turn_on),
            onClick = onOpenLocationSettings,
        )

        LocationCaptureState.Unavailable -> VariSecondaryButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
        )

        else -> Unit
    }
}
