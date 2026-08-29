package com.varisahayak.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The light scheme is the operational default. `background` is slate, not white, so a
 * white card reads as raised without needing a heavy shadow to prove it — the whole
 * elevation system rests on that one-step separation.
 */
private val LightColorScheme = lightColorScheme(
    primary = Amber700,
    onPrimary = Color.White,
    primaryContainer = Amber50,
    onPrimaryContainer = Amber900,

    secondary = Slate600,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate800,

    tertiary = Blue700,
    onTertiary = Color.White,
    tertiaryContainer = Blue50,
    onTertiaryContainer = Blue800,

    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceContainer = Slate50,
    surfaceContainerHigh = Slate100,
    surfaceContainerLow = Color.White,

    outline = Slate400,
    outlineVariant = Slate200,

    error = Red700,
    onError = Color.White,
    errorContainer = Red50,
    onErrorContainer = Red800,

    scrim = Slate950,
)

private val DarkColorScheme = darkColorScheme(
    primary = Amber600,
    onPrimary = Color(0xFF1C1207),
    primaryContainer = Color(0xFF3A2405),
    onPrimaryContainer = Amber200,

    secondary = Slate300,
    onSecondary = Slate900,
    secondaryContainer = Slate800,
    onSecondaryContainer = Slate200,

    tertiary = Blue300Dark,
    onTertiary = Color(0xFF172554),
    tertiaryContainer = Color(0xFF172554),
    onTertiaryContainer = Blue200,

    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    surfaceContainer = Slate900,
    surfaceContainerHigh = Slate800,
    surfaceContainerLow = Slate950,

    outline = Slate500,
    outlineVariant = Slate700,

    error = Red300Dark,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Red200,

    scrim = Color.Black,
)

/**
 * Corner radii. Small components stay tight; anything that behaves as a surface gets the
 * card radius, so a drawer, a dialog and a card all read as the same material.
 */
private val VariShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.CornerSm),
    small = RoundedCornerShape(Dimens.CornerSm),
    medium = RoundedCornerShape(Dimens.CornerMd),
    large = RoundedCornerShape(Dimens.CornerCard),
    extraLarge = RoundedCornerShape(Dimens.CornerLg),
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
            shapes = VariShapes,
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
