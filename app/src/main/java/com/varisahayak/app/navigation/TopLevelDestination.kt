package com.varisahayak.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector
import com.varisahayak.R
import com.varisahayak.domain.model.Capabilities
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
    COMMS(R.string.comms_title, Icons.Filled.Message, Destination.Communication),
    SCAN(R.string.nav_scan, Icons.Filled.QrCodeScanner, Destination.QrScanner),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Destination.Profile),
    ;

    companion object {
        /**
         * Derived from [Capabilities] rather than from the role directly, so the bottom
         * bar and the screen contents can never disagree about what a role may do.
         *
         * Note this is about *prominence*, not permission: a responder can still reach
         * the scanner from the incident detail screen, it simply is not one of their four
         * most-used surfaces. Only [Capabilities.canScanQr] removes it entirely.
         */
        fun forRole(role: UserRole): List<TopLevelDestination> {
            val capabilities = Capabilities.of(role)

            return buildList {
                add(HOME)
                add(INCIDENTS)
                add(MAP)
                add(COMMS)
                // The scanner earns a permanent slot only for the volunteer, whose whole
                // job on the route is meeting Varkaris who have no phone.
                if (capabilities.canScanQr && !capabilities.canSeeAreaWideIncidents) add(SCAN)
                add(PROFILE)
            }
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
