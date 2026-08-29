package com.varisahayak.feature.lostfound

import com.varisahayak.domain.model.LostFoundKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The report form's pure logic.
 *
 * Worth testing on its own because two of these rules are load-bearing for matching rather
 * than for the UI: the closed vocabularies exist so that `LostFoundMatchingEngine`'s exact
 * string comparisons can actually fire, and the clothing string exists so its bag-of-words
 * overlap has words to work with.
 */
class ReportFormTest {

    // --- what may be submitted ---------------------------------------------------------

    @Test
    fun `an empty form cannot be submitted`() {
        // Not a validation rule so much as a guard against noise: a report with nothing
        // identifying on it cannot be matched against anything.
        assertFalse(ReportFormState().canSubmit)
    }

    @Test
    fun `a photograph alone is enough`() {
        // The whole point of the redesign. A volunteer who takes one picture and taps
        // submit has filed something the face pipeline can work with.
        assertTrue(ReportFormState(photoLocalPath = "/data/photo.jpg").canSubmit)
    }

    @Test
    fun `a single tapped attribute is enough`() {
        assertTrue(ReportFormState(gender = GenderOption.FEMALE).canSubmit)
        assertTrue(ReportFormState(clothingColours = setOf(ClothingColour.YELLOW)).canSubmit)
        assertTrue(ReportFormState(approximateAge = "8").canSubmit)
    }

    @Test
    fun `whitespace alone is not a detail`() {
        assertFalse(ReportFormState(personName = "   ", clothingDetail = "  ").canSubmit)
    }

    // --- age ----------------------------------------------------------------------------

    @Test
    fun `age presets write a number the engine can compare`() {
        // The engine scores age numerically with a plus or minus two tolerance, so a band
        // stored as a range would throw that away.
        assertEquals(5, AgePreset.YOUNG_CHILD.representativeAge)
        assertEquals(10, AgePreset.CHILD.representativeAge)
    }

    @Test
    fun `a typed age selects the band it falls into`() {
        // So the chips reflect a hand-entered number rather than looking unset.
        assertEquals(AgePreset.INFANT, AgePreset.forAge(1))
        assertEquals(AgePreset.YOUNG_CHILD, AgePreset.forAge(6))
        assertEquals(AgePreset.CHILD, AgePreset.forAge(11))
        assertEquals(AgePreset.TEENAGER, AgePreset.forAge(16))
        assertEquals(AgePreset.ADULT, AgePreset.forAge(40))
        assertEquals(AgePreset.ELDERLY, AgePreset.forAge(80))
    }

    @Test
    fun `no age selects no band`() {
        assertNull(AgePreset.forAge(null))
    }

    @Test
    fun `both sides tapping the same preset agree exactly`() {
        // The common case, and the one worth optimising: two volunteers who each only know
        // "a small child" produce an exact age match rather than two blanks.
        val lost = ReportFormState(approximateAge = AgePreset.CHILD.representativeAge.toString())
        val found = ReportFormState(approximateAge = AgePreset.CHILD.representativeAge.toString())

        assertEquals(lost.ageOrNull, found.ageOrNull)
    }

    @Test
    fun `a non-numeric age is simply absent`() {
        assertNull(ReportFormState(approximateAge = "").ageOrNull)
    }

    // --- clothing -----------------------------------------------------------------------

    @Test
    fun `clothing puts the tapped colours before the free text`() {
        val form = ReportFormState(clothingDetail = "school uniform")

        assertEquals(
            "yellow blue school uniform",
            form.clothingDescription(listOf("yellow", "blue")),
        )
    }

    @Test
    fun `clothing works with colours and no detail`() {
        assertEquals("red", ReportFormState().clothingDescription(listOf("red")))
    }

    @Test
    fun `clothing works with detail and no colours`() {
        val form = ReportFormState(clothingDetail = "torn kurta")
        assertEquals("torn kurta", form.clothingDescription(emptyList()))
    }

    @Test
    fun `clothing is blank when nothing was given`() {
        assertEquals("", ReportFormState().clothingDescription(emptyList()))
    }

    @Test
    fun `tapped colours give two hurried descriptions words in common`() {
        // The reason the colour chips exist. "mustard kurta" and "yellow top" overlap on
        // nothing; two people tapping YELLOW overlap on a token the engine can score.
        val lost = ReportFormState(clothingDetail = "kurta").clothingDescription(listOf("yellow"))
        val found = ReportFormState(clothingDetail = "top").clothingDescription(listOf("yellow"))

        val shared = lost.split(" ").intersect(found.split(" ").toSet())
        assertEquals(setOf("yellow"), shared)
    }

    // --- stored vocabulary ---------------------------------------------------------------

    @Test
    fun `wire values are lowercase english regardless of display language`() {
        // A report filed in Marathi and one filed in Hindi have to compare equal, so the
        // stored value can never be the localised label.
        GenderOption.entries.forEach { assertEquals(it.wireValue.lowercase(), it.wireValue) }
        LanguageOption.entries.forEach { assertEquals(it.wireValue.lowercase(), it.wireValue) }
        ConditionOption.entries.forEach { assertEquals(it.wireValue.lowercase(), it.wireValue) }
        ClothingColour.entries.forEach { assertEquals(it.wireValue.lowercase(), it.wireValue) }
    }

    @Test
    fun `every option round-trips through its wire value`() {
        // Reports come back from the server as strings; a value that cannot be parsed back
        // would render as an unselected chip and silently lose the volunteer's answer.
        GenderOption.entries.forEach {
            assertEquals(it, GenderOption.fromWire(it.wireValue))
        }
        LanguageOption.entries.forEach {
            assertEquals(it, LanguageOption.fromWire(it.wireValue))
        }
        ConditionOption.entries.forEach {
            assertEquals(it, ConditionOption.fromWire(it.wireValue))
        }
        ClothingColour.entries.forEach {
            assertEquals(it, ClothingColour.fromWire(it.wireValue))
        }
    }

    @Test
    fun `parsing tolerates the casing and padding a server round trip may add`() {
        assertEquals(GenderOption.MALE, GenderOption.fromWire(" MALE "))
        assertEquals(LanguageOption.MARATHI, LanguageOption.fromWire("Marathi"))
    }

    @Test
    fun `an unrecognised stored value parses to nothing rather than guessing`() {
        assertNull(GenderOption.fromWire("boy"))
        assertNull(GenderOption.fromWire(null))
        assertNull(ConditionOption.fromWire(""))
    }

    @Test
    fun `wire values are unique within each vocabulary`() {
        // A duplicate would make fromWire return whichever came first and quietly drop the
        // other option from every form that tried to restore it.
        assertEquals(
            GenderOption.entries.size,
            GenderOption.entries.map { it.wireValue }.toSet().size,
        )
        assertEquals(
            LanguageOption.entries.size,
            LanguageOption.entries.map { it.wireValue }.toSet().size,
        )
        assertEquals(
            ClothingColour.entries.size,
            ClothingColour.entries.map { it.wireValue }.toSet().size,
        )
    }

    @Test
    fun `condition is ordered worst first`() {
        // Triage information for whoever comes to help: the option that needs a responder
        // must be the one a volunteer sees without scrolling.
        assertEquals(ConditionOption.NEEDS_MEDICAL, ConditionOption.entries.first())
    }

    @Test
    fun `language offers a way to record that the person is not speaking`() {
        // Common with a frightened child, and more useful than an empty field — which the
        // engine reads as "unknown" rather than as an observation.
        assertTrue(LanguageOption.entries.any { it == LanguageOption.NOT_SPEAKING })
    }

    // --- form defaults --------------------------------------------------------------------

    @Test
    fun `a new form starts on the side it was opened for`() {
        assertEquals(LostFoundKind.FOUND, ReportFormState(kind = LostFoundKind.FOUND).kind)
    }

    @Test
    fun `extra details start collapsed`() {
        assertFalse(ReportFormState().isExpanded)
    }

    @Test
    fun `nothing is selected by default`() {
        val form = ReportFormState()

        assertNull(form.gender)
        assertNull(form.language)
        assertNull(form.condition)
        assertNull(form.photoLocalPath)
        assertTrue(form.clothingColours.isEmpty())
    }
}
