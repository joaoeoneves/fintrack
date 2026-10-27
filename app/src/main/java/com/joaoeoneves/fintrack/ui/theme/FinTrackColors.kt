package com.joaoeoneves.fintrack.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-specific semantic colors that have no Material 3 role: money direction, hairline dividers,
 * the two surface tones, the secondary text colour and the gold accent (fill, text, soft tint).
 * Read through [FinTrackTheme.colors].
 */
@Immutable
data class FinTrackColors(
    val positive: Color,
    val negative: Color,
    val hairline: Color,
    val surface1: Color,
    val surface2: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
)

val LocalFinTrackColors =
    staticCompositionLocalOf<FinTrackColors> { error("FinTrackColors not provided; wrap in FinTrackTheme") }

/** Accessor for the app-specific theme values, mirroring `MaterialTheme.colorScheme`. */
object FinTrackTheme {
    val colors: FinTrackColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFinTrackColors.current
}
