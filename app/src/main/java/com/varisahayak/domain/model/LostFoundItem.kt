package com.varisahayak.domain.model

/**
 * A lost-person or lost-item report.
 *
 * A lost **person** report is also an incident of category
 * [IncidentCategory.LOST_PERSON] and travels the normal pipeline — [incidentClientId]
 * links the two. This record is the searchable projection over it, not a separate system.
 */
data class LostFoundItem(
    val clientId: String,
    val serverId: String? = null,
    val incidentClientId: String? = null,
    val kind: LostFoundKind,
    val title: String,
    val description: String,
    val lastSeenLocation: GeoPoint? = null,
    val lastSeenAtEpochMillis: Long? = null,
    val qrToken: String? = null,
    val photoLocalPath: String? = null,
    val status: LostFoundStatus,
    val reportedBy: String,
    val reportedAtEpochMillis: Long,
    val syncState: SyncState,
)

enum class LostFoundKind(val wireName: String) {
    PERSON("PERSON"),
    ITEM("ITEM"),
    ;

    companion object {
        fun fromWire(value: String?): LostFoundKind =
            entries.firstOrNull { it.wireName == value } ?: ITEM
    }
}

enum class LostFoundStatus(val wireName: String) {
    OPEN("OPEN"),
    MATCHED("MATCHED"),
    RESOLVED("RESOLVED"),
    ;

    companion object {
        fun fromWire(value: String?): LostFoundStatus =
            entries.firstOrNull { it.wireName == value } ?: OPEN
    }
}
