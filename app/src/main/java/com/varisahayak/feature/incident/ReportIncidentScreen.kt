package com.varisahayak.feature.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.labelRes
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.domain.model.IncidentCategory

@Composable
fun ReportIncidentScreen(
    sosBridgeToken: String?,
    isSos: Boolean,
    viewModel: ReportIncidentViewModel,
    onReportSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sosBridgeToken, isSos) {
        viewModel.initParams(sosBridgeToken, isSos)
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onReportSubmitted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Text(
            text = if (isSos) stringResource(R.string.sos_bridge_title) else stringResource(R.string.report_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Category Picker Section
        Text(
            text = stringResource(R.string.report_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            IncidentCategory.entries.chunked(2).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    rowCategories.forEach { cat ->
                        FilterChip(
                            selected = uiState.category == cat,
                            onClick = { viewModel.onCategorySelected(cat) },
                            label = { Text(stringResource(cat.labelRes())) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Description Input
        OutlinedTextField(
            value = uiState.description,
            onValueChange = viewModel::onDescriptionChanged,
            label = { Text(stringResource(R.string.report_description)) },
            placeholder = { Text(stringResource(R.string.report_description_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 4,
        )

        // Affected Person Note
        OutlinedTextField(
            value = uiState.affectedPersonNote,
            onValueChange = viewModel::onAffectedPersonNoteChanged,
            label = { Text(stringResource(R.string.report_affected_person)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Location Fix Status Card
        Card(
            shape = RoundedCornerShape(Dimens.CornerMd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.report_location),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            uiState.isCapturingLocation -> stringResource(R.string.report_location_capturing)
                            uiState.currentLocation != null -> "${uiState.currentLocation?.latitude?.toString()?.take(7)}, ${uiState.currentLocation?.longitude?.toString()?.take(7)}"
                            else -> stringResource(R.string.report_location_unavailable)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (uiState.isCapturingLocation) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        uiState.error?.let { err ->
            Text(
                text = err.cause?.message ?: stringResource(R.string.state_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceMd))

        // Submit Button
        VariPrimaryButton(
            text = stringResource(R.string.report_submit),
            onClick = { viewModel.submitReport() },
            icon = Icons.Filled.Send,
            enabled = !uiState.isSubmitting && uiState.category != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
