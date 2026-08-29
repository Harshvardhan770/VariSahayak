package com.varisahayak.domain.model

/**
 * Incident lifecycle states.
 *
 * The main line is REPORTED -> TRIAGED -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> RESOLVED.
 * PENDING_SYNC, CANCELLED, REASSIGNMENT_REQUIRED and ESCALATED sit alongside it.
 *
 * Legal transitions live in [IncidentStateMachine], not here — an enum that also owns its
 * transition rules is harder to test and harder to read.
 */
enum class IncidentStatus(val wireName: String) {
    /** Captured on-device, not yet accepted by the server. */
    PENDING_SYNC("PENDING_SYNC"),
    REPORTED("REPORTED"),
    TRIAGED("TRIAGED"),
    ASSIGNED("ASSIGNED"),
    ACCEPTED("ACCEPTED"),
    IN_PROGRESS("IN_PROGRESS"),
    RESOLVED("RESOLVED"),
    CANCELLED("CANCELLED"),
    REASSIGNMENT_REQUIRED("REASSIGNMENT_REQUIRED"),
    ESCALATED("ESCALATED"),
    ;

    /** Terminal states carry no further work and drop out of active queues. */
    val isTerminal: Boolean get() = this == RESOLVED || this == CANCELLED

    /** States that still need someone to act. Drives the "open incidents" view. */
    val isOpen: Boolean get() = !isTerminal && this != PENDING_SYNC

    companion object {
        fun fromWire(value: String?): IncidentStatus =
            entries.firstOrNull { it.wireName == value } ?: REPORTED
    }
}
