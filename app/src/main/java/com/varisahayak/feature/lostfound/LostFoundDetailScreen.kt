package com.varisahayak.feature.lostfound

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.SyncBadge
import com.varisahayak.core.media.PhotoCapture
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.model.LostFoundStatus

@Composable
fun LostFoundDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LostFoundDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                Text(
                    text = uiState.report?.title ?: stringResource(R.string.lostfound_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Dimens.SpaceSm)
                )
            }
        }
    ) { innerPadding ->
        val report = uiState.report
        if (report == null) {
            // Loading or not found
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            item {
                ReportHeaderCard(report = report)
            }

            if (candidates.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.lostfound_pending_matches, candidates.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Dimens.SpaceSm)
                    )
                }
                items(candidates, key = { it.match.clientId }) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        enabled = !uiState.isReviewing,
                        onConfirm = { viewModel.confirm(candidate.match.clientId) },
                        onReject = { viewModel.reject(candidate.match.clientId) }
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.match_no_candidates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportHeaderCard(report: com.varisahayak.domain.model.LostFoundReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (report.photoLocalPath != null) {
                    val bitmap = remember(report.photoLocalPath) {
                        PhotoCapture.thumbnail(report.photoLocalPath)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = report.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(
                            when (report.kind) {
                                LostFoundKind.LOST -> R.string.lostfound_side_lost
                                LostFoundKind.FOUND -> R.string.lostfound_side_found
                            }
                        ) + " · " + stringResource(
                            when (report.status) {
                                LostFoundStatus.OPEN -> R.string.lostfound_status_open
                                LostFoundStatus.MATCHED -> R.string.lostfound_status_matched
                                LostFoundStatus.REUNITED -> R.string.lostfound_status_reunited
                                LostFoundStatus.CLOSED -> R.string.lostfound_status_closed
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SyncBadge(syncState = report.syncState)

            HorizontalDivider()

            val details = listOfNotNull(
                report.personName?.let { "Name: $it" },
                report.approximateAge?.let { stringResource(R.string.lostfound_age_approx, it) },
                report.gender?.let { "Gender: $it" },
                report.clothingDescription?.let { "Clothing: $it" },
                report.physicalDescription?.let { "Physical: $it" },
                report.language?.let { "Language: $it" },
                report.qrLocationName?.let { "Location: $it" },
                report.guardianName?.let { "Guardian: $it" },
                report.guardianPhone?.let { "Guardian Phone: $it" },
                report.condition?.let { "Condition: $it" },
                report.additionalNotes?.let { "Notes: $it" }
            )

            details.forEach { detail ->
                Text(text = detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
