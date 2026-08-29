package com.varisahayak.domain.model

/**
 * A fixed, geo-tagged point on the Wari route that carries a printed QR code.
 *
 * **A QR code is a place, never a person.** This is the single most important rule in
 * Phase 7 and it inverts the earlier design, which put opaque tokens on pilgrim
 * wristbands. Tokens are now installed on checkpoints, water points, medical tents and
 * junctions: a sign bolted to a post cannot be lost, cannot be swapped between people,
 * and cannot leak an identity when it is photographed by a stranger.
 *
 * One physical code serves four public functions — SOS, volunteer details, WhatsApp
 * channel, live journey — and the same code serves authenticated volunteers as a location
 * reference. Because none of that configuration lives in the payload, a help point can
 * move, change its WhatsApp channel, or change its staffing without anybody reprinting a
 * sign.
 */
data class QrLocation(
    val token: String,
    val locationName: String,
    val description: String? = null,
    val point: GeoPoint,
    val routeSegment: String? = null,
    val routeSequence: Int? = null,
    val locationType: QrLocationType,
    val status: QrLocationStatus,
    val publicPageEnabled: Boolean = true,
    val areaId: String? = null,
    val organisationId: String? = null,
    val lastVerifiedAtEpochMillis: Long? = null,
) {
    /** A location only answers public scans when it is active *and* opted in. */
    val servesPublicPage: Boolean
        get() = status == QrLocationStatus.ACTIVE && publicPageEnabled

    /** A volunteer may reference any active location, public page or not. */
    val usableAsReference: Boolean
        get() = status == QrLocationStatus.ACTIVE
}

enum class QrLocationType(val wireName: String) {
    CHECKPOINT("CHECKPOINT"),
    MEDICAL_POINT("MEDICAL_POINT"),
    VOLUNTEER_POINT("VOLUNTEER_POINT"),
    REST_AREA("REST_AREA"),
    WATER_POINT("WATER_POINT"),
    ROUTE_POINT("ROUTE_POINT"),
    EMERGENCY_POINT("EMERGENCY_POINT"),
    OTHER("OTHER"),
    ;

    companion object {
        fun fromWire(value: String?): QrLocationType =
            entries.firstOrNull { it.wireName == value } ?: OTHER
    }
}

enum class QrLocationStatus(val wireName: String) {
    ACTIVE("ACTIVE"),

    /** Installed but not yet in service, or temporarily out of service. */
    DISABLED("DISABLED"),

    /** Withdrawn permanently. A revoked token must never resolve to a location. */
    REVOKED("REVOKED"),
    ;

    companion object {
        fun fromWire(value: String?): QrLocationStatus =
            entries.firstOrNull { it.wireName == value } ?: DISABLED
    }
}

/**
 * The result of scanning or typing a location code.
 *
 * [Unknown] and [Malformed] are kept apart on purpose. "We do not recognise this code" and
 * "this code is damaged" lead a volunteer to different next actions — try another sign
 * versus type the code by hand — and collapsing them into one error costs time in a
 * situation where somebody is waiting.
 */
sealed interface QrLocationResolution {

    data class Resolved(val location: QrLocation) : QrLocationResolution

    /** Well-formed and ours, but no such location — or it has been revoked. */
    data object Unknown : QrLocationResolution

    /** Ours by prefix, wrong shape. A scuffed or misprinted sign. */
    data object Malformed : QrLocationResolution

    /** A valid QR code, but not one of ours. Somebody scanned a product barcode. */
    data object NotOurs : QrLocationResolution

    /**
     * No connectivity and no cached copy.
     *
     * The token is still returned, because a report must be fileable against a raw token
     * and resolved on sync. Nothing about getting help for the person in front of you may
     * depend on a working mast.
     */
    data class Offline(val token: String) : QrLocationResolution
}

/**
 * Validates a scanned location payload before anything is stored or sent.
 *
 * Format: `VARI-LOC-` followed by 8 uppercase Crockford base32 characters — I, L, O and U
 * are excluded so a volunteer reading a scuffed sign aloud cannot confuse them with 1 and
 * 0. Short enough to type by torchlight, wide enough (~40 bits) that codes are not
 * guessable by hand.
 *
 * The personal-data tripwire is retained from the wristband design even though a location
 * code should never contain such a thing. If a badly-produced batch of signs ever encodes
 * a phone number, the app must refuse it loudly rather than quietly storing personal data
 * it was never meant to hold.
 */
object QrLocationValidator {

    const val PREFIX = "VARI-LOC-"
    private const val BODY_LENGTH = 8
    private val BODY_ALPHABET = Regex("^[0-9A-HJKMNP-TV-Z]{$BODY_LENGTH}$")

    private val PERSONAL_DATA_PATTERNS = listOf(
        Regex("""\b\d{12}\b"""), // Aadhaar-length digit run
        Regex("""\b(?:\+91[\s-]?)?[6-9]\d{9}\b"""), // Indian mobile number
        Regex("""[\w.+-]+@[\w-]+\.[\w.]+"""), // email address
    )

    /** Outcome of *format* validation only. Resolution against the route is separate. */
    sealed interface Format {
        data class Valid(val token: String) : Format
        data object Malformed : Format
        data object NotOurs : Format
        data object ContainsPersonalData : Format
    }

    fun validate(raw: String?): Format {
        val payload = normalisePayload(raw) ?: return Format.NotOurs

        // Exact-format check first. A token body is drawn from an alphabet that includes
        // digits, so it can legitimately contain a digit run — running the personal-data
        // patterns first would occasionally reject a perfectly good sign.
        if (payload.startsWith(PREFIX) && BODY_ALPHABET.matches(payload.removePrefix(PREFIX))) {
            return Format.Valid(payload)
        }

        if (PERSONAL_DATA_PATTERNS.any { it.containsMatchIn(payload) }) {
            return Format.ContainsPersonalData
        }

        return if (payload.startsWith(PREFIX)) Format.Malformed else Format.NotOurs
    }

    /**
     * Accepts a hand-typed code from the manual-entry fallback.
     *
     * Tolerant on purpose: uppercases, strips spaces, and supplies the prefix if the
     * volunteer typed only the 8-character body. A torn sign must not mean no help.
     */
    fun validateManualEntry(raw: String?): Format {
        val cleaned = raw.orEmpty().trim().uppercase().replace(" ", "")
        if (cleaned.isEmpty()) return Format.NotOurs

        // Strip separators only from the body, so "VARI-LOC-8F72A91C" and "8F72A91C" and
        // "8F72-A91C" all work.
        val body = cleaned.removePrefix(PREFIX).replace("-", "")
        return validate(PREFIX + body)
    }

    /**
     * Extracts a token from a scanned *URL*.
     *
     * A public sign encodes a link so an ordinary phone camera opens the website rather
     * than showing unreadable text. The volunteer app scans the same physical code, so it
     * must accept both the URL form and the bare token.
     */
    private fun normalisePayload(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val candidate = when {
            // https://…/l/VARI-LOC-XXXXXXXX  or  …?loc=VARI-LOC-XXXXXXXX
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ->
                trimmed.substringAfterLast('/', "")
                    .substringAfterLast('=', trimmed.substringAfterLast('/', ""))

            else -> trimmed
        }

        return candidate.trim().uppercase().ifEmpty { null }
    }
}
