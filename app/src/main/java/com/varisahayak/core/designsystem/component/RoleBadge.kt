package com.varisahayak.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.domain.model.UserRole

/**
 * Who the signed-in user is, in the top bar.
 *
 * The role is carried by an icon as well as a tint, for the same reason priority is: a
 * medical responder glancing at a handed-over phone should be able to tell whose session
 * it is without reading. The label comes from [labelRes] rather than a hardcoded English
 * string, so it switches with the app language along with everything else.
 */
@Composable
fun RoleBadge(
    role: UserRole,
    modifier: Modifier = Modifier,
) {
    val colors = VariTheme.colors

    val tone = when (role) {
        UserRole.VOLUNTEER -> colors.successTone()
        UserRole.MEDICAL_RESPONDER -> colors.criticalTone()
        UserRole.POLICE_RESPONDER -> colors.infoTone()
        UserRole.NGO_RESPONDER -> colors.infoTone()
        UserRole.ORGANISER -> BadgeTone(
            container = colors.brandSubtle,
            content = colors.onBrandSubtle,
            border = colors.brandBorder,
        )

        UserRole.ADMINISTRATOR -> BadgeTone(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            border = MaterialTheme.colorScheme.outlineVariant,
        )
    }

    val icon = when (role) {
        UserRole.VOLUNTEER -> Icons.Filled.VolunteerActivism
        UserRole.MEDICAL_RESPONDER -> Icons.Filled.LocalHospital
        UserRole.POLICE_RESPONDER -> Icons.Filled.LocalPolice
        UserRole.NGO_RESPONDER -> Icons.Filled.Campaign
        UserRole.ORGANISER -> Icons.Filled.Campaign
        UserRole.ADMINISTRATOR -> Icons.Filled.AdminPanelSettings
    }

    LabelledBadge(
        text = stringResource(role.labelRes()),
        icon = icon,
        tone = tone,
        contentDescription = null,
        modifier = modifier,
    )
}
