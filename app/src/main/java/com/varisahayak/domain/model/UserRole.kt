package com.varisahayak.domain.model

/**
 * Operational roles.
 *
 * The client caches a role only to choose which screens to show. Every actual
 * authorisation decision is enforced by Postgres row-level security — a tampered client
 * that claims [ADMINISTRATOR] gains nothing.
 */
enum class UserRole(val wireName: String) {
    VOLUNTEER("VOLUNTEER"),
    MEDICAL_RESPONDER("MEDICAL_RESPONDER"),
    POLICE_RESPONDER("POLICE_RESPONDER"),
    NGO_RESPONDER("NGO_RESPONDER"),
    ORGANISER("ORGANISER"),
    ADMINISTRATOR("ADMINISTRATOR"),
    ;

    val isResponder: Boolean
        get() = this == MEDICAL_RESPONDER || this == POLICE_RESPONDER || this == NGO_RESPONDER

    /** Command users may triage, assign, escalate, and override priority. */
    val isCommand: Boolean
        get() = this == ORGANISER || this == ADMINISTRATOR

    companion object {
        /**
         * Returns null for an unrecognised role. Callers must route to an explicit error
         * state — never default an unknown role to a privileged one.
         */
        fun fromWire(value: String?): UserRole? =
            entries.firstOrNull { it.wireName == value }
    }
}
