package com.varisahayak.domain.model

/**
 * An SOS Bridge identifier as printed on a Varkari's tag.
 *
 * The payload is an **opaque token and nothing else**. No name, no medical condition, no
 * phone number, no address, no identity number. A wristband is worn in public, gets
 * photographed, gets lost, and gets found by strangers — anything encoded in it is
 * effectively published. Whatever the token refers to is resolved server-side, and the
 * scanner is shown only what their role permits.
 */
@JvmInline
value class QrToken(val value: String) {
    override fun toString(): String = value
}

/**
 * The outcome of inspecting a scanned code.
 *
 * [ContainsPersonalData] exists as a deliberate tripwire. If a badly-produced batch of
 * tags ever encodes a phone number or an identity number, the app must refuse it loudly
 * rather than quietly storing personal data it was never supposed to hold.
 */
sealed interface QrScanResult {
    data class Valid(val token: QrToken) : QrScanResult

    /** A well-formed QR code, but not one of ours — someone scanned a product barcode. */
    data object NotRecognised : QrScanResult

    /** Ours by prefix, but the body is the wrong shape. A damaged or misprinted tag. */
    data object Malformed : QrScanResult

    data object ContainsPersonalData : QrScanResult
}

/**
 * Validates scanned payloads before anything is stored or sent.
 *
 * Format: `VS1:` followed by exactly 26 uppercase base32 characters (Crockford alphabet,
 * excluding I, L, O and U so a human reading a damaged tag aloud cannot confuse them with
 * 1 and 0). That is ~130 bits — not guessable, and short enough to be typed by hand when
 * a tag is too scuffed to scan.
 */
object QrTokenValidator {

    private const val PREFIX = "VS1:"
    private const val BODY_LENGTH = 26
    private val BODY_ALPHABET = Regex("^[0-9A-HJKMNP-TV-Z]{$BODY_LENGTH}$")

    /**
     * Patterns that indicate a payload carries personal data. Deliberately broad — a false
     * positive costs one manual entry, a false negative stores a pilgrim's phone number.
     */
    private val PERSONAL_DATA_PATTERNS = listOf(
        Regex("""\b\d{12}\b"""),                        // Aadhaar-length digit run
        Regex("""\b(?:\+91[\s-]?)?[6-9]\d{9}\b"""),     // Indian mobile number
        Regex("""[\w.+-]+@[\w-]+\.[\w.]+"""),           // email address
        Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}\b"""),   // grouped 12-digit identifier
    )

    fun validate(raw: String?): QrScanResult {
        val payload = raw?.trim().orEmpty()
        if (payload.isEmpty()) return QrScanResult.NotRecognised

        // Exact-format check first. A token body is 26 characters drawn from an alphabet
        // that includes digits, so it can legitimately contain a long digit run — running
        // the personal-data patterns first would occasionally reject a perfectly good tag.
        val body = payload.removePrefix(PREFIX)
        if (payload.startsWith(PREFIX) && BODY_ALPHABET.matches(body)) {
            return QrScanResult.Valid(QrToken(payload))
        }

        // Anything that is not a clean token gets inspected. A tag encoding a phone number
        // is worth surfacing whether or not it claims to be ours.
        if (PERSONAL_DATA_PATTERNS.any { it.containsMatchIn(payload) }) {
            return QrScanResult.ContainsPersonalData
        }

        return if (payload.startsWith(PREFIX)) {
            QrScanResult.Malformed
        } else {
            QrScanResult.NotRecognised
        }
    }

    /**
     * Accepts a hand-typed code. Tolerates lowercase, spaces, and a missing prefix, since
     * this is what a volunteer types off a scuffed tag by torchlight.
     */
    fun validateManualEntry(raw: String?): QrScanResult {
        val cleaned = raw.orEmpty()
            .trim()
            .uppercase()
            .replace(" ", "")
            .replace("-", "")

        if (cleaned.isEmpty()) return QrScanResult.NotRecognised

        val withPrefix = if (cleaned.startsWith(PREFIX)) cleaned else PREFIX + cleaned
        return validate(withPrefix)
    }
}
