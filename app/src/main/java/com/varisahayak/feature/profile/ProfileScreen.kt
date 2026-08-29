package com.varisahayak.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.RewardSummaryCard
import com.varisahayak.core.designsystem.component.ShimmerLoadingState
import com.varisahayak.core.designsystem.component.RoleBadge
import com.varisahayak.core.designsystem.component.VariPrimaryButton

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onSignOut: () -> Unit,
    onNavigateToBulkRegistration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading && uiState.profile == null) {
        ShimmerLoadingState(modifier = modifier)
        return
    }

    val profile = uiState.profile

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
    ) {
        // User Profile Header Card
        Card(
            shape = RoundedCornerShape(Dimens.CornerLg),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceLg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }

                Text(
                    text = profile?.displayName ?: stringResource(R.string.nav_profile),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                profile?.role?.let { role ->
                    RoleBadge(role = role)
                }

                if (profile?.phone != null) {
                    Text(
                        text = profile.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // Rewards Section
        uiState.rewardProfile?.let { rewardProfile ->
            RewardSummaryCard(profile = rewardProfile)
        }

        // Details Section
        Card(
            shape = RoundedCornerShape(Dimens.CornerMd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Text(
                    text = "Account Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                HorizontalDivider()

                ProfileItemRow(
                    label = stringResource(R.string.profile_organisation),
                    value = profile?.organisationName ?: stringResource(R.string.profile_not_assigned),
                )

                ProfileItemRow(
                    label = stringResource(R.string.profile_area),
                    value = profile?.areaName ?: stringResource(R.string.profile_all_route),
                )
            }
        }

        // System Information
        Card(
            shape = RoundedCornerShape(Dimens.CornerMd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.IconSm)
                    )
                    Text(
                        text = stringResource(R.string.profile_app_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                HorizontalDivider()

                ProfileItemRow(
                    label = stringResource(R.string.profile_version),
                    value = "1.0.0",
                )

                ProfileItemRow(
                    label = stringResource(R.string.profile_environment),
                    value = if (com.varisahayak.BuildConfig.DEBUG) "Development" else "Production",
                )
            }
        }

        if (profile?.role?.isCommand == true) {
            // Management Section
            Card(
                shape = RoundedCornerShape(Dimens.CornerMd),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.SpaceMd),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                ) {
                    Text(
                        text = "Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    HorizontalDivider()

                    VariPrimaryButton(
                        text = "Bulk Registration",
                        onClick = onNavigateToBulkRegistration,
                        icon = Icons.Filled.PersonAdd,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "Import multiple responders or volunteers via Excel sheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sync & Offline Diagnostics Card
        Card(
            shape = RoundedCornerShape(Dimens.CornerMd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Text(
                    text = "Sync & Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Unsynced Incidents",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (uiState.unsyncedCount == 0) "All changes synced" else "${uiState.unsyncedCount} pending sync",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.unsyncedCount == 0) VariTheme.colors.success else VariTheme.colors.warning,
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.syncPending() },
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.IconSm),
                        )
                        Spacer(modifier = Modifier.size(Dimens.SpaceXs))
                        Text(text = stringResource(R.string.sync_retry_now))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Sign Out Button
        VariPrimaryButton(
            text = stringResource(R.string.auth_sign_out),
            onClick = {
                viewModel.signOut()
                onSignOut()
            },
            icon = Icons.Filled.Logout,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileItemRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
