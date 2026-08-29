package com.varisahayak.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Routes are serializable objects rather than string literals so a typo is a compile
 * error, and so arguments carry their types instead of being parsed out of a URL.
 */
sealed interface Destination {

    @Serializable
    data object Splash : Destination

    @Serializable
    data object SignIn : Destination

    @Serializable
    data object ForgotPassword : Destination

    @Serializable
    data object SignUp : Destination

    // --- volunteer / responder ---
    @Serializable
    data object VolunteerDashboard : Destination

    @Serializable
    data object ResponderDashboard : Destination

    // --- command ---
    @Serializable
    data object CommandDashboard : Destination

    @Serializable
    data object AdminDashboard : Destination

    // --- shared ---
    @Serializable
    data object IncidentList : Destination

    @Serializable
    data class IncidentDetail(val clientId: String) : Destination

    @Serializable
    data class ReportIncident(val sosBridgeToken: String? = null, val isSos: Boolean = false) :
        Destination

    @Serializable
    data object IncidentMap : Destination

    @Serializable
    data object QrScanner : Destination

    @Serializable
    data object LostAndFound : Destination

    @Serializable
    data object Documentation : Destination

    @Serializable
    data class Conversation(val channelId: String) : Destination

    @Serializable
    data object Notifications : Destination

    @Serializable
    data object Profile : Destination

    /** Shown when a signed-in account has no recognised role. */
    @Serializable
    data object NoRole : Destination
}
