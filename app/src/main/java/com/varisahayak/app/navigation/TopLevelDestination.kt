package com.varisahayak.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector
import com.varisahayak.R
import com.varisahayak.domain.model.UserRole

/**
 * Bottom-bar entries.
 *
 * The set is role-dependent: a volunteer's most-used surface is the QR scanner, which a
 * command user has no reason to see. Keeping this in one place stops each screen from
 * growing its own idea of what the navigation looks like.
 */
enum class TopLevelDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val route: Destination,
) {
    HOME(R.string.nav_dashboard, Icons.Filled.Home, Destination.VolunteerDashboard),
    INCIDENTS(R.string.nav_incidents, Icons.Filled.ListAlt, Destination.IncidentList),
    MAP(R.string.nav_map, Icons.Filled.Map, Destination.IncidentMap),
    SCAN(R.string.nav_scan, Icons.Filled.QrCodeScanner, Destination.QrScanner),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Destination.Profile),
    ;

    companion object {
        fun forRole(role: UserRole): List<TopLevelDestination> = when (role) {
            UserRole.VOLUNTEER -> listOf(HOME, INCIDENTS, MAP, SCAN, PROFILE)

            // Responders triage and act on assigned work; they scan far less often.
            UserRole.MEDICAL_RESPONDER,
            UserRole.POLICE_RESPONDER,
            UserRole.NGO_RESPONDER,
            -> listOf(HOME, INCIDENTS, MAP, PROFILE)

            UserRole.ORGANISER, UserRole.ADMINISTRATOR ->
                listOf(HOME, INCIDENTS, MAP, PROFILE)
        }

        /** The start destination for a role, used once the profile resolves. */
        fun homeRoute(role: UserRole): Destination = when (role) {
            UserRole.VOLUNTEER -> Destination.VolunteerDashboard
            UserRole.MEDICAL_RESPONDER,
            UserRole.POLICE_RESPONDER,
            UserRole.NGO_RESPONDER,
            -> Destination.ResponderDashboard

            UserRole.ORGANISER -> Destination.CommandDashboard
            UserRole.ADMINISTRATOR -> Destination.AdminDashboard
        }
    }
}
