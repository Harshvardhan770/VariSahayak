package com.varisahayak.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens for VARI Sahayak.
 *
 * Values are tuned for outdoor legibility on low-end screens in daylight: high contrast,
 * saturated status hues, and no low-contrast greys for anything load-bearing. Nothing in
 * the app references a raw [Color] — features read [VariColors] through [LocalVariColors].
 *
 * Colour is never the only carrier of meaning. Every status surface pairs its colour with
 * an icon and a text label (see PriorityBadge, SyncBadge).
 */

// --- brand ---
internal val Saffron40 = Color(0xFF9A4B00)
internal val Saffron80 = Color(0xFFFFB77C)
internal val Saffron90 = Color(0xFFFFDCC4)
internal val Saffron10 = Color(0xFF331200)
internal val Saffron30 = Color(0xFF6E3500)

internal val Indigo40 = Color(0xFF3A4A8F)
internal val Indigo80 = Color(0xFFB6C4FF)
internal val Indigo90 = Color(0xFFDCE1FF)
internal val Indigo10 = Color(0xFF001551)
internal val Indigo30 = Color(0xFF203371)

// --- neutrals ---
internal val Neutral99 = Color(0xFFFFFBFE)
internal val Neutral95 = Color(0xFFF3EFF3)
internal val Neutral90 = Color(0xFFE5E1E6)
internal val Neutral10 = Color(0xFF101418)
internal val Neutral20 = Color(0xFF1D2024)
internal val Neutral30 = Color(0xFF32353A)
internal val Neutral50 = Color(0xFF74777C)
internal val Neutral80 = Color(0xFFC7C6CA)

// --- status: chosen to stay distinguishable under the most common colour-vision
// deficiencies, and always accompanied by an icon and label at the call site ---
internal val CriticalLight = Color(0xFFB3261E)
internal val CriticalDark = Color(0xFFFFB4AB)
internal val CriticalContainerLight = Color(0xFFFFDAD6)
internal val CriticalContainerDark = Color(0xFF93000A)

internal val WarningLight = Color(0xFF8A5300)
internal val WarningDark = Color(0xFFFFB95C)
internal val WarningContainerLight = Color(0xFFFFDDB3)
internal val WarningContainerDark = Color(0xFF684200)

internal val SuccessLight = Color(0xFF1B6B3A)
internal val SuccessDark = Color(0xFF7EDCA0)
internal val SuccessContainerLight = Color(0xFFA7F2C0)
internal val SuccessContainerDark = Color(0xFF00522A)

internal val InfoLight = Color(0xFF00639A)
internal val InfoDark = Color(0xFF97CBFF)
internal val InfoContainerLight = Color(0xFFCDE5FF)
internal val InfoContainerDark = Color(0xFF004A77)

/**
 * The semantic tokens Material 3's [androidx.compose.material3.ColorScheme] does not
 * carry. Primary, surface, error and their on-colours come from the M3 scheme; these are
 * the operational-status additions the PRD requires.
 */
@Immutable
data class VariColors(
    val critical: Color,
    val onCritical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

internal val LightVariColors = VariColors(
    critical = CriticalLight,
    onCritical = Color.White,
    criticalContainer = CriticalContainerLight,
    onCriticalContainer = Color(0xFF410002),
    warning = WarningLight,
    onWarning = Color.White,
    warningContainer = WarningContainerLight,
    onWarningContainer = Color(0xFF2B1700),
    success = SuccessLight,
    onSuccess = Color.White,
    successContainer = SuccessContainerLight,
    onSuccessContainer = Color(0xFF00210F),
    info = InfoLight,
    onInfo = Color.White,
    infoContainer = InfoContainerLight,
    onInfoContainer = Color(0xFF001D32),
)

internal val DarkVariColors = VariColors(
    critical = CriticalDark,
    onCritical = Color(0xFF690005),
    criticalContainer = CriticalContainerDark,
    onCriticalContainer = Color(0xFFFFDAD6),
    warning = WarningDark,
    onWarning = Color(0xFF482900),
    warningContainer = WarningContainerDark,
    onWarningContainer = Color(0xFFFFDDB3),
    success = SuccessDark,
    onSuccess = Color(0xFF003919),
    successContainer = SuccessContainerDark,
    onSuccessContainer = Color(0xFFA7F2C0),
    info = InfoDark,
    onInfo = Color(0xFF003354),
    infoContainer = InfoContainerDark,
    onInfoContainer = Color(0xFFCDE5FF),
)
