package com.varisahayak.domain.model

/**
 * One side of a separation: either somebody is looking for a person, or a volunteer has a
 * person who is looking for their family.
 *
 * Both sides are first-class and independent. A Lost Person report is filed by whoever is
 * searching; a Found Person report is filed by the volunteer currently caring for
 * somebody. The matching engine pairs them, but neither depends on the other existing.
 *
 * **A photograph is never mandatory.** A parent who reaches a volunteer at dusk with no
 * phone and no picture of their child must be able to file a complete, matchable report
 * from description alone. Every field below except [title] and [kind] is optional for
 * exactly that reason, and the matching engine treats an absent field as *no signal*
 * rather than as a mismatch.
 */
data class LostFoundReport(
    val clientId: String,
    val serverId: String? = null,
    val incidentClientId: String? = null,
    val kind: LostFoundKind,
    val subjectType: LostFoundSubjectType,

    /** A short human label for lists — "Aarav, approx 8" or "Blue cloth bag". */
    val title: String,
    val description: String = "",

    // --- person attributes, all optional -------------------------------------------------
    val personName: String? = null,
    val approximateAge: Int? = null,
    val gender: String? = null,
    val approximateHeightCm: Int? = null,
    val clothingDescription: String? = null,
    val physicalDescription: String? = null,
    val language: String? = null,
    val condition: String? = null,
    val additionalNotes: String? = null,

    // --- guardian, for a Lost Person report ----------------------------------------------
    val guardianName: String? = null,
    val guardianPhone: String? = null,

    // --- location: three distinct things, never conflated --------------------------------
    /** The fixed sign the report was filed against. Precise, and does not drift. */
    val qrLocationToken: String? = null,
    val qrLocationName: String? = null,
    /** Where the reporting device was. Approximate, and only if permission was granted. */
    val deviceLocation: GeoPoint? = null,
    /** Best current belief, which a volunteer may correct by hand. */
    val lastKnownLocation: GeoPoint? = null,
    val routeSegment: String? = null,
    val routeSequence: Int? = null,

    // --- time -----------------------------------------------------------------------------
    /** When the person was last seen (Lost) or when they were found (Found). */
    val occurredAtEpochMillis: Long? = null,
    val reportedAtEpochMillis: Long,

    // --- photo and face matching ----------------------------------------------------------
    val photoLocalPath: String? = null,
    val photoRemotePath: String? = null,
    /**
     * Whether the photo produced a usable face embedding server-side.
     *
     * [FaceMatchStatus.NO_FACE] and friends are *not* failures of the report. They mean
     * "this photo cannot contribute a face signal"; every other signal still applies.
     */
    val faceMatchStatus: FaceMatchStatus = FaceMatchStatus.NOT_APPLICABLE,

    // --- custodian, for a Found Person ------------------------------------------------------
    val custodianUserId: String? = null,
    val custodianName: String? = null,
    val custodianContact: String? = null,

    val status: LostFoundStatus,
    val reportedBy: String,
    val syncState: SyncState,
) {
    val isPerson: Boolean get() = subjectType == LostFoundSubjectType.PERSON

    /** A photo exists on the device or the server, whatever its face-match state. */
    val hasPhoto: Boolean get() = photoLocalPath != null || photoRemotePath != null

    /** This report can contribute a face-similarity signal to matching. */
    val hasUsableFace: Boolean get() = faceMatchStatus == FaceMatchStatus.READY

    /** Still worth matching against. Closed reports drop out of the candidate pool. */
    val isActive: Boolean
        get() = status == LostFoundStatus.OPEN || status == LostFoundStatus.MATCHED
}

/** Which side of the separation this report describes. */
enum class LostFoundKind(val wireName: String) {
    /** Somebody is searching for this person or item. */
    LOST("LOST"),

    /** A volunteer currently has this person or item. */
    FOUND("FOUND"),
    ;

    /** The side a report of this kind should be matched against. */
    val opposite: LostFoundKind get() = if (this == LOST) FOUND else LOST

    companion object {
        fun fromWire(value: String?): LostFoundKind =
            entries.firstOrNull { it.wireName == value }
            // Legacy rows from the pre-Plan-07 schema stored PERSON/ITEM in this column.
            // Both described something that had been lost.
                ?: LOST
    }
}

enum class LostFoundSubjectType(val wireName: String) {
    PERSON("PERSON"),
    ITEM("ITEM"),
    ;

    companion object {
        fun fromWire(value: String?): LostFoundSubjectType =
            entries.firstOrNull { it.wireName == value } ?: PERSON
    }
}

enum class LostFoundStatus(val wireName: String) {
    OPEN("OPEN"),

    /** At least one candidate is awaiting human review. Still actively matched. */
    MATCHED("MATCHED"),

    /** A human confirmed the pairing and the person is back with their family. */
    REUNITED("REUNITED"),

    /** Closed for another reason — duplicate, withdrawn, resolved off-system. */
    CLOSED("CLOSED"),
    ;

    companion object {
        fun fromWire(value: String?): LostFoundStatus =
            entries.firstOrNull { it.wireName == value }
            // Legacy rows used RESOLVED for what is now REUNITED.
                ?: if (value == "RESOLVED") REUNITED else OPEN
    }
}

/**
 * What happened when the server tried to turn a photo into a face embedding.
 *
 * Every value except [READY] means "no face signal available for this report" — and none
 * of them invalidates the report. This distinction is the whole point: *missing photo is
 * not a face mismatch, and no detected face is not a person mismatch.*
 */
enum class FaceMatchStatus(val wireName: String) {
    /** No photo was supplied. Perfectly normal. */
    NOT_APPLICABLE("NOT_APPLICABLE"),

    /** Photo uploaded, waiting for the CV service. Retried after sync. */
    PENDING("PENDING"),

    /** An embedding exists and this report can contribute a face signal. */
    READY("READY"),

    /** The image decoded but held no detectable face. */
    NO_FACE("NO_FACE"),

    /** More than one face — ambiguous which person the report is about. */
    MULTIPLE_FACES("MULTIPLE_FACES"),

    /** Corrupt, unsupported, or oversized image. */
    INVALID_IMAGE("INVALID_IMAGE"),

    /** The CV service was unreachable or errored. Distinct from a bad photo. */
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE"),
    ;

    /** A state the volunteer could fix by supplying a different picture. */
    val isRetryableByUser: Boolean
        get() = this == NO_FACE || this == MULTIPLE_FACES || this == INVALID_IMAGE

    /** A state the system should retry on its own. */
    val isRetryableBySystem: Boolean
        get() = this == PENDING || this == SERVICE_UNAVAILABLE

    companion object {
        fun fromWire(value: String?): FaceMatchStatus =
            entries.firstOrNull { it.wireName == value } ?: NOT_APPLICABLE
    }
}

/**
 * Who is currently responsible for a found person, and where they are.
 *
 * Tracked explicitly and as a chain rather than a single mutable field, because "who has
 * this child right now" is the question a frantic parent is actually asking, and the
 * answer has to survive a handover between volunteers at a shift change.
 */
data class CustodyRecord(
    val clientId: String,
    val reportClientId: String,
    val custodianUserId: String,
    val custodianName: String? = null,
    val helpPointName: String? = null,
    val qrLocationToken: String? = null,
    val location: GeoPoint? = null,
    val fromEpochMillis: Long,
    /** Null while this is the current custodian. */
    val untilEpochMillis: Long? = null,
    val handoverNote: String? = null,
    val syncState: SyncState,
) {
    val isCurrent: Boolean get() = untilEpochMillis == null
}
