package com.varisahayak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.varisahayak.domain.model.UserRole

@Composable
fun RoleBadge(
    role: UserRole,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) = when (role) {
        UserRole.VOLUNTEER ->
            Color(0xFF66BB6A) to Color.White

        UserRole.MEDICAL_RESPONDER ->
            Color(0xFFE53935) to Color.White

        UserRole.POLICE_RESPONDER ->
            Color(0xFF3949AB) to Color.White

        UserRole.NGO_RESPONDER ->
            Color(0xFF00897B) to Color.White

        UserRole.ORGANISER ->
            Color(0xFF8E24AA) to Color.White

        UserRole.ADMINISTRATOR ->
            Color(0xFFB71C1C) to Color.White
    }

    Text(
        text = role.displayName(),
        color = textColor,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
    )
}

private fun UserRole.displayName(): String {
    return when (this) {
        UserRole.VOLUNTEER -> "Volunteer"
        UserRole.MEDICAL_RESPONDER -> "Medical Responder"
        UserRole.POLICE_RESPONDER -> "Police Responder"
        UserRole.NGO_RESPONDER -> "NGO Responder"
        UserRole.ORGANISER -> "Organiser"
        UserRole.ADMINISTRATOR -> "Administrator"
    }
}