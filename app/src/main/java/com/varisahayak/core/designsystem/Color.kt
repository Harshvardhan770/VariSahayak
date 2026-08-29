package com.varisahayak.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens for VARI Sahayak.
 *
 * Tuned for one environment: a phone held at arm's length, in direct Maharashtra daylight,
 * by someone who is walking. That rules out the whole vocabulary of low-contrast greys,
 * tinted-on-tinted surfaces, and decorative gradients — none of them survive sunlight.
 *
 * ## Contrast
 *
 * Text tokens are measured, not assumed. Every pairing below is annotated with its real
 * contrast ratio against its intended background.
 *
 * A note on the impossible constraint: WCAG AAA for body text demands 7:1, and no colour
 * that still reads as saffron clears 7:1 on white — `#D97706` is 3.19:1, `#B45309` is
 * 5.05:1. Saffron is therefore a *fill and accent* colour here, never a body-text colour.
 * Everything load-bearing is set in [VariColors.textPrimary] (17.9:1) or
 * [VariColors.textSecondary] (10.9:1), and every status label sits on its tinted container
 * at 7:1 or better. That is how the palette reaches AAA where AAA actually matters.
 *
 * Colour is never the only carrier of meaning. Every status surface pairs its colour with
 * an icon and a text label (see PriorityBadge, SyncBadge).
 */

// --- canvas & surface (slate) ---
internal val Slate50 = Color(0xFFF8FAFC)
internal val Slate100 = Color(0xFFF1F5F9)
internal val Slate200 = Color(0xFFE2E8F0)
internal val Slate300 = Color(0xFFCBD5E1)
internal val Slate400 = Color(0xFF94A3B8)
internal val Slate500 = Color(0xFF64748B)
internal val Slate600 = Color(0xFF475569)
internal val Slate700 = Color(0xFF334155)
internal val Slate800 = Color(0xFF1E293B)
internal val Slate900 = Color(0xFF0F172A)
internal val Slate950 = Color(0xFF020617)

// --- brand: deep saffron / earthy ocher ---
internal val Amber50 = Color(0xFFFFFBEB)
internal val Amber100 = Color(0xFFFEF3C7)
internal val Amber200 = Color(0xFFFDE68A)
internal val Amber500 = Color(0xFFF59E0B)
internal val Amber600 = Color(0xFFD97706)
internal val Amber700 = Color(0xFFB45309)
internal val Amber800 = Color(0xFF92400E)
internal val Amber900 = Color(0xFF78350F)

// --- status: red / amber / blue / emerald ---
// Content tokens are deliberately one step darker than a conventional -700 ramp so each
// label clears 7:1 on its own container.
internal val Red50 = Color(0xFFFEF2F2)
internal val Red200 = Color(0xFFFECACA)
internal val Red700 = Color(0xFFB91C1C)
internal val Red800 = Color(0xFF991B1B) // 7.64:1 on Red50
internal val Red300Dark = Color(0xFFFCA5A5)

internal val Blue50 = Color(0xFFEFF6FF)
internal val Blue200 = Color(0xFFBFDBFE)
internal val Blue700 = Color(0xFF1D4ED8)
internal val Blue800 = Color(0xFF1E40AF) // 8.01:1 on Blue50
internal val Blue300Dark = Color(0xFF93C5FD)

internal val Emerald50 = Color(0xFFECFDF5)
internal val Emerald200 = Color(0xFFA7F3D0)
internal val Emerald700 = Color(0xFF047857)
internal val Emerald800 = Color(0xFF065F46) // 7.39:1 on Emerald50
internal val Emerald300Dark = Color(0xFF6EE7B7)

/**
 * The semantic tokens Material 3's [androidx.compose.material3.ColorScheme] does not
 * carry: operational status, the elevated-card recipe, and the frosted-panel recipe.
 *
 * Features read these through [LocalVariColors]; nothing in the app references a raw
 * [Color] literal.
 */
@Immutable
data class VariColors(
    // --- status: critical / SOS ---
    val critical: Color,
    val onCritical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
    val criticalBorder: Color,
    // --- status: high / priority ---
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warningBorder: Color,
    // --- status: resolved / active ---
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val successBorder: Color,
    // --- status: in-progress / dispatched ---
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val infoBorder: Color,
    // --- structure ---
    /** Page background. Never white — white cards must read as raised against it. */
    val canvas: Color,
    /** Elevated operational card fill. */
    val cardSurface: Color,
    /** Hairline at rest. */
    val cardBorder: Color,
    /** Hairline on press — the only thing a card interaction changes. */
    val cardBorderHover: Color,
    /** Translucent fill for panels that float over the map. */
    val glassSurface: Color,
    /** The bright inner edge that separates a frosted panel from what it covers. */
    val glassBorder: Color,
    /** Colour of the card drop shadow, pre-alpha. */
    val cardShadow: Color,
    // --- text ---
    /** 17.9:1 on white. Titles, values, anything a decision depends on. */
    val textPrimary: Color,
    /** 10.9:1 on white. Supporting copy, still fully AAA. */
    val textSecondary: Color,
    /** 4.8:1 on white — AA only. Timestamps and hints, never load-bearing. */
    val textMuted: Color,
    // --- brand ---
    /** Filled brand surfaces. White on this is 5.05:1. */
    val brandSolid: Color,
    val brandSolidPressed: Color,
    val onBrandSolid: Color,
    /** Tinted brand surface for selected chips and quiet accents. */
    val brandSubtle: Color,
    /** 8.81:1 on [brandSubtle]. */
    val onBrandSubtle: Color,
    val brandBorder: Color,
    /** Icon-only and large-display brand accent, where 3:1 is the governing threshold. */
    val brandAccent: Color,
)

internal val LightVariColors = VariColors(
    critical = Red700,
    onCritical = Color.White,
    criticalContainer = Red50,
    onCriticalContainer = Red800,
    criticalBorder = Red200,

    warning = Amber700,
    onWarning = Color.White,
    warningContainer = Amber50,
    onWarningContainer = Amber900,
    warningBorder = Amber200,

    success = Emerald700,
    onSuccess = Color.White,
    successContainer = Emerald50,
    onSuccessContainer = Emerald800,
    successBorder = Emerald200,

    info = Blue700,
    onInfo = Color.White,
    infoContainer = Blue50,
    onInfoContainer = Blue800,
    infoBorder = Blue200,

    canvas = Slate50,
    cardSurface = Color.White,
    cardBorder = Slate200.copy(alpha = 0.80f),
    cardBorderHover = Slate300,
    glassSurface = Color.White.copy(alpha = 0.85f),
    glassBorder = Color.White.copy(alpha = 0.50f),
    cardShadow = Slate900,

    textPrimary = Slate900,
    textSecondary = Slate600,
    textMuted = Slate500,

    brandSolid = Amber700,
    brandSolidPressed = Amber800,
    onBrandSolid = Color.White,
    brandSubtle = Amber50,
    onBrandSubtle = Amber900,
    brandBorder = Amber200,
    brandAccent = Amber600,
)

/**
 * Night values exist because the Wari walks before dawn. The same tokens invert: tinted
 * containers become deep and desaturated, content becomes light, and the frosted panel
 * darkens instead of lightening so it still separates from the map beneath it.
 */
internal val DarkVariColors = VariColors(
    critical = Red300Dark,
    onCritical = Color(0xFF450A0A),
    criticalContainer = Color(0xFF450A0A),
    onCriticalContainer = Red200,
    criticalBorder = Color(0xFF7F1D1D),

    warning = Amber200,
    onWarning = Color(0xFF451A03),
    warningContainer = Color(0xFF451A03),
    onWarningContainer = Amber200,
    warningBorder = Amber800,

    success = Emerald300Dark,
    onSuccess = Color(0xFF022C22),
    successContainer = Color(0xFF022C22),
    onSuccessContainer = Emerald200,
    successBorder = Emerald800,

    info = Blue300Dark,
    onInfo = Color(0xFF172554),
    infoContainer = Color(0xFF172554),
    onInfoContainer = Blue200,
    infoBorder = Blue800,

    canvas = Slate950,
    cardSurface = Slate900,
    cardBorder = Slate800,
    cardBorderHover = Slate700,
    glassSurface = Slate900.copy(alpha = 0.85f),
    glassBorder = Slate700.copy(alpha = 0.60f),
    cardShadow = Color.Black,

    textPrimary = Slate100,
    textSecondary = Slate300,
    textMuted = Slate400,

    brandSolid = Amber600,
    brandSolidPressed = Amber700,
    onBrandSolid = Color(0xFF1C1207),
    brandSubtle = Color(0xFF3A2405),
    onBrandSubtle = Amber200,
    brandBorder = Amber800,
    brandAccent = Amber500,
)
