package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.domain.model.Badge
import com.varisahayak.domain.model.RewardProfile

@Composable
fun RewardSummaryCard(
    profile: RewardProfile,
    modifier: Modifier = Modifier
) {
    val colors = VariTheme.colors
    
    OperationalCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Dimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            // Level and XP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.reward_level, profile.level),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${profile.totalXp} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.brandSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = colors.onBrandSubtle,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                LinearProgressIndicator(
                    progress = { profile.progressToNextLevel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(Dimens.CornerPill)),
                    color = colors.brandSolid,
                    trackColor = colors.brandSubtle.copy(alpha = 0.5f),
                )
                Text(
                    text = stringResource(R.string.reward_xp_to_next, profile.xpToNextLevel, profile.level + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }

            HorizontalDivider(color = colors.cardBorder)

            // Impact Stats
            ImpactStatsRow(profile = profile)

            // Badges
            if (profile.badges.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.reward_badges),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                BadgeRow(badges = profile.badges)
            }
        }
    }
}

@Composable
private fun ImpactStatsRow(profile: RewardProfile) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ImpactItem(label = stringResource(R.string.reward_impact_resolved), value = profile.impact.incidentsResolved.toString())
        ImpactItem(label = stringResource(R.string.reward_impact_sos), value = profile.impact.sosResponses.toString())
        ImpactItem(label = stringResource(R.string.reward_impact_assisted), value = (profile.impact.peopleAssisted + profile.impact.lostFoundAssisted).toString())
    }
}

@Composable
private fun ImpactItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VariTheme.colors.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VariTheme.colors.textMuted
        )
    }
}

@Composable
private fun BadgeRow(badges: List<Badge>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        contentPadding = PaddingValues(vertical = Dimens.SpaceXs)
    ) {
        items(badges) { badge ->
            BadgeChip(badge = badge)
        }
    }
}

@Composable
private fun BadgeChip(badge: Badge) {
    val colors = VariTheme.colors
    Surface(
        shape = RoundedCornerShape(Dimens.CornerPill),
        color = colors.brandSubtle.copy(alpha = 0.3f),
        border = BorderStroke(Dimens.Hairline, colors.brandBorder.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.SpaceSm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MilitaryTech,
                contentDescription = null,
                tint = colors.brandSolid,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = badge.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = colors.brandSolid
            )
        }
    }
}
