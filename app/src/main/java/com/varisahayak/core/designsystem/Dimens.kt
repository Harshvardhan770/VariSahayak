package com.varisahayak.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing constants.
 *
 * [Dimens.MinTouchTarget] is a hard floor, not a suggestion: this app is operated
 * one-handed, outdoors, often while walking in a crowd. Every interactive component in
 * [com.varisahayak.core.designsystem.component] enforces it.
 */
object Dimens {
    val MinTouchTarget = 48.dp

    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 32.dp

    val CornerSm = 8.dp
    val CornerMd = 12.dp
    val CornerLg = 20.dp

    /** Operational card radius. Matches the 16dp of a `rounded-2xl` surface. */
    val CornerCard = 16.dp

    /** Pills and chips are fully rounded — they read as state, not as tappable cards. */
    val CornerPill = 999.dp

    val IconSm = 16.dp
    val IconMd = 24.dp
    val IconLg = 32.dp

    /** Primary field actions (report, SOS) are deliberately larger than the minimum. */
    val PrimaryActionHeight = 64.dp
    val SosActionHeight = 88.dp

    val ScreenPadding = 16.dp
    val CardElevation = 1.dp

    // --- surfaces ---
    /** Hairline width. One physical hairline, not a decorative stroke. */
    val Hairline = 1.dp

    /**
     * Card shadow spread. Deliberately tiny: the shadow exists to lift a white card off a
     * slate canvas, not to make it look glossy.
     */
    val CardShadow = 8.dp
    val CardShadowPressed = 12.dp

    /** Blur radius for frosted panels floating over the map. */
    val GlassBlur = 24.dp

    // --- pills & chips ---
    val PillHeight = 32.dp
    val ChipHeight = 36.dp
    val PillPaddingH = 10.dp
    val ChipPaddingH = 14.dp

    // --- floating chrome ---
    /** Gap between a floating panel and the screen edge. */
    val FloatingInset = 12.dp
    val FloatingBarHeight = 56.dp

    // --- incident drawer ---
    /** Width of the right-hand incident drawer on a tablet or landscape phone. */
    val DrawerWidth = 340.dp
    /** How much of the bottom sheet stays visible when collapsed on a phone. */
    val DrawerPeekHeight = 132.dp

    // --- avatars ---
    val AvatarSm = 28.dp
    val AvatarMd = 36.dp

    // --- walkie-talkie ---
    val WaveformHeight = 28.dp
    val PttButtonSize = 64.dp
}
