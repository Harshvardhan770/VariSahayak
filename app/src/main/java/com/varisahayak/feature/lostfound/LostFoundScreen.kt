package com.varisahayak.feature.lostfound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.EmptyState
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.domain.model.LostFoundItem
import com.varisahayak.domain.model.LostFoundKind

@Composable
fun LostFoundScreen(
    modifier: Modifier = Modifier,
    viewModel: LostFoundViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text(stringResource(R.string.lostfound_search)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.MinTouchTarget),
        )

        VariPrimaryButton(
            text = stringResource(R.string.lostfound_report),
            onClick = { viewModel.setReportOpen(true) },
        )

        if (items.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                items(items, key = { it.clientId }) { item ->
                    LostFoundRow(item = item)
                }
            }
        }
    }

    if (uiState.isReportOpen) {
        ReportDialog(
            state = uiState,
            onKindChanged = viewModel::onKindChanged,
            onTitleChanged = viewModel::onTitleChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onSubmit = viewModel::submitReport,
            onDismiss = { viewModel.setReportOpen(false) },
        )
    }

    if (uiState.justReported) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(stringResource(R.string.lostfound_title)) },
            text = { Text(stringResource(R.string.report_saved_offline)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissConfirmation) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun LostFoundRow(item: LostFoundItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Text(
                    text = stringResource(
                        when (item.kind) {
                            LostFoundKind.PERSON -> R.string.category_lost_person
                            LostFoundKind.ITEM -> R.string.category_other
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                SyncBadge(syncState = item.syncState)
            }
        }
    }
}

@Composable
private fun ReportDialog(
    state: LostFoundUiState,
    onKindChanged: (LostFoundKind) -> Unit,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lostfound_report)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    LostFoundKind.entries.forEach { kind ->
                        FilterChip(
                            selected = state.kind == kind,
                            onClick = { onKindChanged(kind) },
                            label = {
                                Text(
                                    stringResource(
                                        when (kind) {
                                            LostFoundKind.PERSON -> R.string.category_lost_person
                                            LostFoundKind.ITEM -> R.string.category_other
                                        },
                                    ),
                                )
                            },
                            modifier = Modifier.defaultMinSize(minHeight = Dimens.MinTouchTarget),
                        )
                    }
                }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    label = { Text(stringResource(R.string.report_description)) },
                    singleLine = true,
                    isError = state.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChanged,
                    label = { Text(stringResource(R.string.lostfound_last_seen)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !state.isSubmitting) {
                Text(stringResource(R.string.report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
