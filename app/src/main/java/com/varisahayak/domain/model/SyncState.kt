package com.varisahayak.domain.model

/**
 * Whether a locally captured record has reached the server.
 *
 * This is deliberately separate from [IncidentStatus]: an incident can be IN_PROGRESS and
 * still be unsynced, and the volunteer needs to see both facts at once.
 *
 * [FAILED] is not a discard. A failed record stays on the device, stays visible, and stays
 * retryable — the product rule is that no locally captured incident silently disappears.
 */
enum class SyncState {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    ;

    val needsSync: Boolean get() = this == PENDING || this == FAILED
}
