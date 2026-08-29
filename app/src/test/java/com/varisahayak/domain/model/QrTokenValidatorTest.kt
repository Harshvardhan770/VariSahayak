package com.varisahayak.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The QR payload rule is the single hardest requirement in the SOS Bridge: a wristband is
 * worn in public and anything encoded in it is effectively published. These tests exist to
 * make a regression here loud.
 */
class QrTokenValidatorTest {

    private val validBody = "7ZK2M9QW4XB3HN5PRT8VCD6JFG"

    @Test
    fun `accepts a well formed token`() {
        val result = QrTokenValidator.validate("VS1:$validBody")

        assertTrue(result is QrScanResult.Valid)
        assertEquals("VS1:$validBody", (result as QrScanResult.Valid).token.value)
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertTrue(QrTokenValidator.validate("  VS1:$validBody  ") is QrScanResult.Valid)
    }

    @Test
    @DisplayName("a foreign barcode is simply not ours, not an error")
    fun `rejects foreign payloads`() {
        assertEquals(QrScanResult.NotRecognised, QrTokenValidator.validate("5901234123457"))
        assertEquals(QrScanResult.NotRecognised, QrTokenValidator.validate("https://example.org"))
    }

    @Test
    fun `rejects empty and null payloads`() {
        assertEquals(QrScanResult.NotRecognised, QrTokenValidator.validate(null))
        assertEquals(QrScanResult.NotRecognised, QrTokenValidator.validate("   "))
    }

    @Test
    @DisplayName("ours by prefix but wrong body length is malformed, not unrecognised")
    fun `rejects wrong length body`() {
        assertEquals(QrScanResult.Malformed, QrTokenValidator.validate("VS1:TOOSHORT"))
        assertEquals(QrScanResult.Malformed, QrTokenValidator.validate("VS1:${validBody}EXTRA"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["I", "L", "O", "U"])
    @DisplayName("ambiguous letters are excluded so a code read aloud cannot be mistyped")
    fun `rejects ambiguous alphabet characters`(character: String) {
        val body = character + validBody.drop(1)
        assertEquals(QrScanResult.Malformed, QrTokenValidator.validate("VS1:$body"))
    }

    @Test
    fun `rejects lowercase in a scanned payload`() {
        assertEquals(QrScanResult.Malformed, QrTokenValidator.validate("VS1:${validBody.lowercase()}"))
    }

    // --- the tripwire ---

    @ParameterizedTest
    @ValueSource(
        strings = [
            "VS1:9876543210",
            "+91 9876543210",
            "VS1:priya@example.org",
            "123456789012",
            "1234 5678 9012",
        ],
    )
    @DisplayName("a payload carrying personal data is refused outright")
    fun `refuses payloads containing personal data`(payload: String) {
        assertEquals(QrScanResult.ContainsPersonalData, QrTokenValidator.validate(payload))
    }

    @Test
    @DisplayName("the personal-data check runs even on payloads claiming to be ours")
    fun `personal data check precedes prefix check`() {
        assertEquals(
            QrScanResult.ContainsPersonalData,
            QrTokenValidator.validate("VS1:$validBody 9876543210"),
        )
    }

    // --- manual entry, typed off a scuffed tag by torchlight ---

    @Test
    fun `manual entry accepts a bare body without the prefix`() {
        assertTrue(QrTokenValidator.validateManualEntry(validBody) is QrScanResult.Valid)
    }

    @Test
    fun `manual entry tolerates lowercase spaces and dashes`() {
        val typed = validBody.lowercase().chunked(4).joinToString("-")
        val result = QrTokenValidator.validateManualEntry(typed)

        assertTrue(result is QrScanResult.Valid)
        assertEquals("VS1:$validBody", (result as QrScanResult.Valid).token.value)
    }

    @Test
    fun `manual entry still rejects a short code`() {
        assertEquals(QrScanResult.Malformed, QrTokenValidator.validateManualEntry("ABC123"))
    }

    @Test
    fun `manual entry rejects an empty code`() {
        assertEquals(QrScanResult.NotRecognised, QrTokenValidator.validateManualEntry(""))
    }
}
