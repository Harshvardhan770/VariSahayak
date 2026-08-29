package com.varisahayak.feature.lostfound

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.model.LostFoundReport
import com.varisahayak.domain.model.LostFoundStatus

/**
 * The Lost & Found board.
 *
 * Two actions rather than one, and both prominent. §7.15 asks for "Found Person" to be a
 * first-class action, because the volunteer holding a lost child is the one under the most
 * pressure and has the least patience for navigating a form hierarchy.
 */
@Composable
fun LostFoundScreen(
    modifier: Modifier = Modifier,
    onOpenMatches: () -> Unit = {},
    viewModel: LostFoundViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        // Candidates first. A pending match is somebody waiting to be reunited, and it
        // outranks everything else on this screen.
        if (uiState.candidateCount > 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Dimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                ) {
                    Text(
                        text = stringResource(
                            R.string.lostfound_pending_matches,
                            uiState.candidateCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = onOpenMatches) {
                        Text(stringResource(R.string.lostfound_review_matches))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            VariPrimaryButton(
                text = stringResource(R.string.lostfound_report_lost),
                onClick = { viewModel.openReport(LostFoundKind.LOST) },
                modifier = Modifier.weight(1f),
            )
            VariPrimaryButton(
                text = stringResource(R.string.lostfound_report_found),
                onClick = { viewModel.openReport(LostFoundKind.FOUND) },
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text(stringResource(R.string.lostfound_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.MinTouchTarget),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            BoardFilter.entries.forEach { side ->
                FilterChip(
                    selected = uiState.filter == side,
                    onClick = { viewModel.onFilterChanged(side) },
                    label = { Text(stringResource(side.labelRes())) },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.MinTouchTarget),
                )
            }
        }

        if (reports.isEmpty()) {
            EmptyState(message = stringResource(R.string.lostfound_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                items(reports, key = { it.clientId }) { report ->
                    LostFoundRow(report = report)
                }
            }
        }
    }

    if (uiState.isReportOpen) {
        ReportDialog(
            state = uiState,
            onChange = viewModel::updateForm,
            onSubmit = viewModel::submitReport,
            onDismiss = viewModel::closeReport,
        )
    }

    uiState.justReportedClientId?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(stringResource(R.string.lostfound_title)) },
            // The photo notice, when there is one, replaces the generic confirmation: it
            // is the only thing on this dialog the volunteer might act on.
            text = { Text(uiState.photoNotice ?: stringResource(R.string.report_saved_offline)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissConfirmation) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

private fun BoardFilter.labelRes(): Int = when (this) {
    BoardFilter.ALL -> R.string.filter_all
    BoardFilter.LOST -> R.string.lostfound_side_lost
    BoardFilter.FOUND -> R.string.lostfound_side_found
}

@Composable
private fun LostFoundRow(report: LostFoundReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(text = report.title, style = MaterialTheme.typography.titleMedium)

            // The attributes a volunteer actually scans a list for.
            val summary = listOfNotNull(
                report.approximateAge?.let {
                    stringResource(R.string.lostfound_age_approx, it)
                },
                report.clothingDescription,
                report.qrLocationName,
            ).joinToString(" · ")

            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Who is holding this person right now — the question a frantic parent asks.
            if (report.kind == LostFoundKind.FOUND && report.custodianName != null) {
                Text(
                    text = stringResource(R.string.lostfound_with_custodian, report.custodianName),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                // Side and status as words, never colour alone.
                Text(
                    text = stringResource(
                        when (report.kind) {
                            LostFoundKind.LOST -> R.string.lostfound_side_lost
                            LostFoundKind.FOUND -> R.string.lostfound_side_found
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(
                        when (report.status) {
                            LostFoundStatus.OPEN -> R.string.lostfound_status_open
                            LostFoundStatus.MATCHED -> R.string.lostfound_status_matched
                            LostFoundStatus.REUNITED -> R.string.lostfound_status_reunited
                            LostFoundStatus.CLOSED -> R.string.lostfound_status_closed
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                SyncBadge(syncState = report.syncState)
            }
        }
    }
}

/**
 * The report form.
 *
 * Scrolls, because it is long — and it is long because collecting a clothing description
 * and a language from somebody who has no photograph is what makes the report matchable
 * at all. Only the first field is required.
 */
@Composable
private fun ReportDialog(
    state: LostFoundUiState,
    onChange: ((ReportFormState) -> ReportFormState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isFound = state.form.kind == LostFoundKind.FOUND

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isFound) R.string.lostfound_report_found else R.string.lostfound_report_lost,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                state.scannedLocation?.let { location ->
                    Text(
                        text = stringResource(R.string.lostfound_at_location, location.locationName),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Field(
                    value = state.form.title,
                    onValueChange = { v -> onChange { it.copy(title = v) } },
                    labelRes = R.string.lostfound_field_title,
                    isError = state.error != null && state.form.title.isBlank(),
                )
                Field(
                    value = state.form.personName,
                    onValueChange = { v -> onChange { it.copy(personName = v) } },
                    labelRes = R.string.lostfound_field_name,
                )
                Field(
                    value = state.form.approximateAge,
                    onValueChange = { v ->
                        // Digits only, so a typo cannot become a validation failure after
                        // the volunteer has filled in the whole form.
                        onChange { it.copy(approximateAge = v.filter(Char::isDigit).take(3)) }
                    },
                    labelRes = R.string.lostfound_field_age,
                    keyboardType = KeyboardType.Number,
                )
                Field(
                    value = state.form.gender,
                    onValueChange = { v -> onChange { it.copy(gender = v) } },
                    labelRes = R.string.lostfound_field_gender,
                )
                Field(
                    value = state.form.clothingDescription,
                    onValueChange = { v -> onChange { it.copy(clothingDescription = v) } },
                    labelRes = R.string.lostfound_field_clothing,
                )
                Field(
                    value = state.form.physicalDescription,
                    onValueChange = { v -> onChange { it.copy(physicalDescription = v) } },
                    labelRes = R.string.lostfound_field_physical,
                )
                Field(
                    value = state.form.language,
                    onValueChange = { v -> onChange { it.copy(language = v) } },
                    labelRes = R.string.lostfound_field_language,
                )

                if (isFound) {
                    Field(
                        value = state.form.condition,
                        onValueChange = { v -> onChange { it.copy(condition = v) } },
                        labelRes = R.string.lostfound_field_condition,
                    )
                } else {
                    // Guardian contact belongs to the Lost side: it is who to call when
                    // the child is found.
                    Field(
                        value = state.form.guardianName,
                        onValueChange = { v -> onChange { it.copy(guardianName = v) } },
                        labelRes = R.string.lostfound_field_guardian,
                    )
                    Field(
                        value = state.form.guardianPhone,
                        onValueChange = { v -> onChange { it.copy(guardianPhone = v) } },
                        labelRes = R.string.lostfound_field_guardian_phone,
                        keyboardType = KeyboardType.Phone,
                    )
                }

                Field(
                    value = state.form.additionalNotes,
                    onValueChange = { v -> onChange { it.copy(additionalNotes = v) } },
                    labelRes = R.string.lostfound_field_notes,
                )

                Text(
                    text = stringResource(R.string.lostfound_photo_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                (state.error as? com.varisahayak.core.common.AppError.Validation)?.let { error ->
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !state.isSubmitting && state.form.canSubmit,
            ) {
                Text(stringResource(R.string.report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.MinTouchTarget),
    )
}
