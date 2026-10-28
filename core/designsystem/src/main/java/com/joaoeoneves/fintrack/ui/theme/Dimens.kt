package com.joaoeoneves.fintrack.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing and size tokens on an 8dp grid. */
object Dimens {
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val ScreenPadding = 20.dp
    val RowHeight = 64.dp
    val SettingsRowMinHeight = 56.dp
    val IconBubble = 40.dp
    val PillButtonHeight = 56.dp
    val ThinBarHeight = 4.dp
    val HairlineThickness = 1.dp

    /** Height of the floating add pill (two 48dp actions plus the capsule padding). */
    val AddPillHeight = 56.dp

    /** Gap between the floating add pill and the bottom of its screen area. */
    val AddPillBottomMargin = Space16

    /** Bottom content padding that lets the last list rows scroll clear of the floating add pill. */
    val AddPillClearance = AddPillHeight + AddPillBottomMargin + Space32
}
