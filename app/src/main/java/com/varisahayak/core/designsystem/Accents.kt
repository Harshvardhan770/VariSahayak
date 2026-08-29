package com.varisahayak.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.UserRole

/**
 * Accent tones for iconography.
 *
 * Distinct from the status tones in `StatusBadges.kt`, and the distinction matters. A
 * status tone means *this record is critical*; an accent tone means *this tile is about
 * medical things*. Using one for the other is how a dashboard ends up with five red
 * elements where only one is urgent.
 *
 * Each tone is a pale container with a saturated glyph. The glyph clears 3:1 against its
 * own container — the threshold for a graphical object — while every number and label
 * beside it is still set in [VariColors.textPrimary] at 17.9:1. Colour identifies the
 * category here; it never carries the value.
 */
@Immutable
data class AccentTone(
    /** Fill of the round icon plate. */
    val container: Color,
    /** The glyph itself, and the colour of any trend text tied to this tile. */
    val accent: Color,
)

// Containers are the -100 step and glyphs the -500, which is the pairing that lands just
// over 3:1 across all five hues. Going lighter on the container breaks that.
internal val AccentRed = AccentTone(Color(0xFFFEE2E2), Color(0xFFDC2626))
internal val AccentAmber = AccentTone(Color(0xFFFFEDD5), Color(0xFFEA580C))
internal val AccentGreen = AccentTone(Color(0xFFDCFCE7), Color(0xFF16A34A))
internal val AccentBlue = AccentTone(Color(0xFFDBEAFE), Color(0xFF2563EB))
internal val AccentPurple = AccentTone(Color(0xFFEDE9FE), Color(0xFF7C3AED))
internal val AccentSlate = AccentTone(Color(0xFFF1F5F9), Color(0xFF475569))

/** The five accents, in a stable order. Charts index into this so a series keeps its hue. */
val AccentPalette: List<AccentTone> =
    listOf(AccentGreen, AccentBlue, AccentPurple, AccentRed, AccentAmber)

object Accents {
    val red get() = AccentRed
    val amber get() = AccentAmber
    val green get() = AccentGreen
    val blue get() = AccentBlue
    val purple get() = AccentPurple
    val slate get() = AccentSlate
}

/**
 * The accent a category always wears.
 *
 * Fixed per category rather than assigned by position, so "medical is red" holds on the
 * map, in the chart legend, and on a list row alike. A hue that means one thing on one
 * screen and another elsewhere is worse than no hue at all.
 */
@Composable
fun IncidentCategory.accent(): AccentTone = when (this) {
    IncidentCategory.MEDICAL -> AccentRed
    IncidentCategory.WATER -> AccentBlue
    IncidentCategory.LOST_PERSON -> AccentGreen
    IncidentCategory.BLOCKED_ROAD -> AccentAmber
    IncidentCategory.SANITATION -> AccentPurple
    IncidentCategory.CROWD_SURGE -> AccentAmber
    IncidentCategory.OTHER -> AccentSlate
}

/** Non-composable variant, for building chart series off the composition. */
fun IncidentCategory.accentTone(): AccentTone = when (this) {
    IncidentCategory.MEDICAL -> AccentRed
    IncidentCategory.WATER -> AccentBlue
    IncidentCategory.LOST_PERSON -> AccentGreen
    IncidentCategory.BLOCKED_ROAD -> AccentAmber
    IncidentCategory.SANITATION -> AccentPurple
    IncidentCategory.CROWD_SURGE -> AccentAmber
    IncidentCategory.OTHER -> AccentSlate
}

/** The accent a role wears in avatars and role chips. */
fun UserRole.accentTone(): AccentTone = when (this) {
    UserRole.VOLUNTEER -> AccentGreen
    UserRole.MEDICAL_RESPONDER -> AccentRed
    UserRole.POLICE_RESPONDER -> AccentBlue
    UserRole.NGO_RESPONDER -> AccentPurple
    UserRole.ORGANISER -> AccentAmber
    UserRole.ADMINISTRATOR -> AccentSlate
}
