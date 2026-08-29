package com.varisahayak.domain.model

/**
 * A proposed pairing between a Lost Person report and a Found Person report.
 *
 * **This is a recommendation, never a conclusion.** No score, and no face similarity
 * however strong, moves a case to reunited on its own. A human volunteer confirms or
 * rejects, and both outcomes are kept: a rejected candidate stays auditable and stops the
 * same pair being surfaced again, without touching either underlying report.
 */
data class LostFoundMatch(
    val clientId: String,
    val serverId: String? = null,
    val lostReportClientId: String,
    val foundReportClientId: String,
    val score: MatchScore,
    val status: MatchStatus,
    val createdAtEpochMillis: Long,
    val reviewedBy: String? = null,
    val reviewedAtEpochMillis: Long? = null,
    val reviewNote: String? = null,
    val syncState: SyncState,
)

enum class MatchStatus(val wireName: String) {
    /** Surfaced by the engine, awaiting human review. */
    CANDIDATE("CANDIDATE"),

    /** A human confirmed the pairing. Reunification proceeds from here. */
    CONFIRMED("CONFIRMED"),

    /** A human said no. Kept for audit; never surfaced as a candidate again. */
    REJECTED("REJECTED"),
    ;

    companion object {
        fun fromWire(value: String?): MatchStatus =
            entries.firstOrNull { it.wireName == value } ?: CANDIDATE
    }
}

enum class MatchConfidence { HIGH, MEDIUM, LOW }

/**
 * The outcome of scoring one pair, with the reasoning kept alongside the number.
 *
 * [signals] exists because plan 07 §7.30 forbids showing an unexplained score. A volunteer
 * deciding whether to walk two route points to check on a child needs to know *why* the
 * system thinks it is worth walking — "clothing matches and the timing works" is
 * actionable, "89%" is not.
 */
data class MatchScore(
    val overall: Double,
    val confidence: MatchConfidence,
    val signals: List<MatchSignal>,
) {
    /** Signals that actually contributed, for a compact summary line. */
    val contributing: List<MatchSignal>
        get() = signals.filter { it.strength != SignalStrength.NO_SIGNAL }

    val percent: Int get() = (overall * 100).toInt()
}

/**
 * One reason a pair scored the way it did.
 *
 * [SignalStrength.NO_SIGNAL] is deliberately distinct from a weak match. "We could not
 * compare photographs" and "the photographs do not look like the same person" are opposite
 * pieces of information, and a system that renders both as a low number will send
 * volunteers to the wrong child.
 */
data class MatchSignal(
    val kind: SignalKind,
    val strength: SignalStrength,
    /** 0.0-1.0 within this signal. Meaningless when [strength] is NO_SIGNAL. */
    val value: Double,
    /** Volunteer-facing, already phrased for display. */
    val explanation: String,
)

enum class SignalKind(val label: String) {
    FACE("Photograph"),
    NAME("Name"),
    AGE("Age"),
    GENDER("Gender"),
    CLOTHING("Clothing"),
    LANGUAGE("Language"),
    PHYSICAL("Description"),
    LOCATION("Location"),
    TIME("Timing"),
    ROUTE_PROGRESSION("Route progression"),
}

enum class SignalStrength {
    /** Actively supports the pairing. */
    SUPPORTS,

    /** Compared, and neither supports nor contradicts. */
    NEUTRAL,

    /** Compared, and argues against the pairing. */
    CONTRADICTS,

    /** Could not be compared. Contributes nothing in either direction. */
    NO_SIGNAL,
}
