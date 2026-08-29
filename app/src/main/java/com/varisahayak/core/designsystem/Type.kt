package com.varisahayak.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Sizes sit a step above the Material default because the app is read at arm's length in
 * bright sunlight. Line heights are generous: Devanagari (Hindi and Marathi) sets taller
 * than Latin and clips against tight leading.
 *
 * ## On the font family
 *
 * The design direction called for Inter. This ships [FontFamily.Default] instead, and the
 * reason is not laziness — it is that Inter has no Devanagari coverage. A third of this
 * app is Hindi and a third is Marathi, so Inter would render Latin and hand every
 * Devanagari string to a fallback face anyway, producing two visibly different typefaces
 * inside the same sentence on `values-hi` and `values-mr`.
 *
 * The alternative — downloadable fonts via Play Services — makes first render depend on
 * network and on Google Play being present, which is exactly backwards for an app whose
 * defining constraint is working in a dead spot.
 *
 * [FontFamily.Default] resolves to Roboto, which ships on-device with a matched Devanagari
 * companion. The design intent — tight tracking on display sizes, a firm weight ramp, and
 * an unambiguous size jump between levels — is carried by the scale below rather than by
 * the face.
 */

/**
 * Devanagari matras sit above and below the Latin band. Trimming only the last line's
 * descent keeps blocks optically centred without clipping the top of a `ि` or the bottom
 * of a conjunct.
 */
private val DevanagariSafeLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun variStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = DevanagariSafeLineHeight,
)

internal val VariTypography = Typography(
    // Display — the SOS confirmation and the walkie channel readout. Negative tracking
    // because large text set at default tracking reads loose and amateur.
    displayLarge = variStyle(44, 52, FontWeight.Bold, tracking = -0.5),
    displayMedium = variStyle(38, 46, FontWeight.Bold, tracking = -0.4),
    displaySmall = variStyle(34, 44, FontWeight.Bold, tracking = -0.3),

    headlineLarge = variStyle(30, 40, FontWeight.Bold, tracking = -0.3),
    headlineMedium = variStyle(26, 36, FontWeight.Bold, tracking = -0.2),
    headlineSmall = variStyle(22, 32, FontWeight.SemiBold, tracking = -0.2),

    titleLarge = variStyle(20, 30, FontWeight.SemiBold, tracking = -0.1),
    titleMedium = variStyle(17, 26, FontWeight.SemiBold),
    titleSmall = variStyle(15, 22, FontWeight.SemiBold),

    bodyLarge = variStyle(17, 26, FontWeight.Normal),
    bodyMedium = variStyle(15, 23, FontWeight.Normal),
    bodySmall = variStyle(13, 20, FontWeight.Normal),

    labelLarge = variStyle(15, 22, FontWeight.SemiBold),
    labelMedium = variStyle(13, 18, FontWeight.Medium),
    // Positive tracking on the smallest label: at 11sp, letters set at default tracking
    // collide in Roboto, and this is the size badges and pills use.
    labelSmall = variStyle(11, 16, FontWeight.SemiBold, tracking = 0.3),
)
