package com.varisahayak.feature.incidents

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
import androidx.compose.ui.Modifier
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

        LocationStatusLine(state = uiState.locationState, onRetry = viewModel::captureLocation)

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

@Composable
private fun LocationStatusLine(
    state: LocationCaptureState,
    onRetry: () -> Unit,
) {
    val text = when (state) {
        LocationCaptureState.Idle,
        LocationCaptureState.Capturing,
        -> stringResource(R.string.report_location_capturing)

        LocationCaptureState.Captured -> stringResource(R.string.report_location)
        LocationCaptureState.Approximate -> stringResource(R.string.permission_location_coarse_only)
        LocationCaptureState.Unavailable -> stringResource(R.string.report_location_unavailable)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state == LocationCaptureState.Unavailable) {
        VariSecondaryButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
        )
    }
}
