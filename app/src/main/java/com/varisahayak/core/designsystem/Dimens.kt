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

    val IconSm = 16.dp
    val IconMd = 24.dp
    val IconLg = 32.dp

    /** Primary field actions (report, SOS) are deliberately larger than the minimum. */
    val PrimaryActionHeight = 64.dp
    val SosActionHeight = 88.dp

    val ScreenPadding = 16.dp
    val CardElevation = 1.dp
}
