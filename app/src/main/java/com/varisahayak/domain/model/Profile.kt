package com.varisahayak.domain.model

/**
 * The signed-in user's operational identity, resolved from the server and cached locally
 * so role-aware navigation still works on a cold start with no connectivity.
 */
data class Profile(
    val userId: String,
    val displayName: String,
    val role: UserRole,
    val organisationId: String? = null,
    val organisationName: String? = null,
    val areaId: String? = null,
    val areaName: String? = null,
    val phone: String? = null,
    val capabilities: Set<String> = emptySet(),
)

/**
 * A responder's dispatchable state. Distinct from [Profile] because availability changes
 * constantly while identity does not.
 */
data class Responder(
    val userId: String,
    val role: UserRole,
    val availability: ResponderAvailability,
    val areaId: String? = null,
    val organisationId: String? = null,
    val capabilities: Set<String> = emptySet(),
    val lastKnownLocation: GeoPoint? = null,
    val lastLocationAtEpochMillis: Long? = null,
    val activeAssignmentCount: Int = 0,
)

enum class ResponderAvailability(val wireName: String) {
    AVAILABLE("AVAILABLE"),
    BUSY("BUSY"),
    OFF_SHIFT("OFF_SHIFT"),
    ;

    companion object {
        fun fromWire(value: String?): ResponderAvailability =
            entries.firstOrNull { it.wireName == value } ?: OFF_SHIFT
    }
}
