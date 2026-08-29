package com.varisahayak.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Saffron40,
    onPrimary = Color.White,
    primaryContainer = Saffron90,
    onPrimaryContainer = Saffron10,
    secondary = Indigo40,
    onSecondary = Color.White,
    secondaryContainer = Indigo90,
    onSecondaryContainer = Indigo10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral30,
    outline = Neutral50,
    error = CriticalLight,
    onError = Color.White,
    errorContainer = CriticalContainerLight,
    onErrorContainer = Color(0xFF410002),
)

private val DarkColorScheme = darkColorScheme(
    primary = Saffron80,
    onPrimary = Saffron10,
    primaryContainer = Saffron30,
    onPrimaryContainer = Saffron90,
    secondary = Indigo80,
    onSecondary = Indigo10,
    secondaryContainer = Indigo30,
    onSecondaryContainer = Indigo90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral80,
    outline = Neutral50,
    error = CriticalDark,
    onError = Color(0xFF690005),
    errorContainer = CriticalContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),
)

val LocalVariColors = staticCompositionLocalOf { LightVariColors }

/**
 * Dynamic colour is deliberately not used. Operational status must look the same on every
 * device — a responder should not have to relearn what "critical" looks like because
 * their wallpaper changed.
 */
@Composable
fun VariSahayakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val variColors = if (darkTheme) DarkVariColors else LightVariColors

    CompositionLocalProvider(LocalVariColors provides variColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VariTypography,
            content = content,
        )
    }
}

/** Access point for the semantic tokens Material 3 does not provide. */
object VariTheme {
    val colors: VariColors
        @Composable
        @ReadOnlyComposable
        get() = LocalVariColors.current
}
