package com.varisahayak.core.designsystem.component

import androidx.annotation.StringRes
import com.varisahayak.R
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.ResponderAvailability
import com.varisahayak.domain.model.SyncState
import com.varisahayak.domain.model.UserRole

/**
 * The single place domain enums are mapped to localised strings.
 *
 * Domain models stay free of Android types, and no composable hardcodes a label — both
 * requirements come straight from the architecture and localisation rules.
 */

@StringRes
fun IncidentCategory.labelRes(): Int = when (this) {
    IncidentCategory.MEDICAL -> R.string.category_medical
    IncidentCategory.WATER -> R.string.category_water
    IncidentCategory.LOST_PERSON -> R.string.category_lost_person
    IncidentCategory.BLOCKED_ROAD -> R.string.category_blocked_road
    IncidentCategory.SANITATION -> R.string.category_sanitation
    IncidentCategory.CROWD_SURGE -> R.string.category_crowd_surge
    IncidentCategory.OTHER -> R.string.category_other
}

@StringRes
fun IncidentStatus.labelRes(): Int = when (this) {
    IncidentStatus.PENDING_SYNC -> R.string.status_pending_sync
    IncidentStatus.REPORTED -> R.string.status_reported
    IncidentStatus.TRIAGED -> R.string.status_triaged
    IncidentStatus.ASSIGNED -> R.string.status_assigned
    IncidentStatus.ACCEPTED -> R.string.status_accepted
    IncidentStatus.IN_PROGRESS -> R.string.status_in_progress
    IncidentStatus.RESOLVED -> R.string.status_resolved
    IncidentStatus.CANCELLED -> R.string.status_cancelled
    IncidentStatus.REASSIGNMENT_REQUIRED -> R.string.status_reassignment_required
    IncidentStatus.ESCALATED -> R.string.status_escalated
}

@StringRes
fun IncidentPriority.labelRes(): Int = when (this) {
    IncidentPriority.CRITICAL -> R.string.priority_critical
    IncidentPriority.HIGH -> R.string.priority_high
    IncidentPriority.MEDIUM -> R.string.priority_medium
    IncidentPriority.LOW -> R.string.priority_low
}

@StringRes
fun SyncState.labelRes(): Int = when (this) {
    SyncState.PENDING -> R.string.sync_pending
    SyncState.SYNCING -> R.string.sync_syncing
    SyncState.SYNCED -> R.string.sync_synced
    SyncState.FAILED -> R.string.sync_failed
}

@StringRes
fun UserRole.labelRes(): Int = when (this) {
    UserRole.VOLUNTEER -> R.string.role_volunteer
    UserRole.MEDICAL_RESPONDER -> R.string.role_medical
    UserRole.POLICE_RESPONDER -> R.string.role_police
    UserRole.NGO_RESPONDER -> R.string.role_ngo
    UserRole.ORGANISER -> R.string.role_organiser
    UserRole.ADMINISTRATOR -> R.string.role_admin
}

@StringRes
fun ResponderAvailability.labelRes(): Int = when (this) {
    ResponderAvailability.AVAILABLE -> R.string.dashboard_available
    ResponderAvailability.BUSY -> R.string.dashboard_busy
    ResponderAvailability.OFF_SHIFT -> R.string.dashboard_off_shift
}
