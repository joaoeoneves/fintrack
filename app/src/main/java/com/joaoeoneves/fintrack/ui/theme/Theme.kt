package com.joaoeoneves.fintrack.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat

/**
 * Whether the effective (resolved) theme is dark, exposed to composables below [FinTrackTheme]
 * so components that need theme-aware but non-role-based colors (e.g. category colors) can read it
 * without recomputing [isSystemInDarkTheme].
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val DarkFinTrackColors =
    FinTrackColors(
        positive = DarkPositive,
        negative = DarkNegative,
        hairline = DarkHairline,
        surface1 = DarkSurface1,
        surface2 = DarkSurface2,
        textSecondary = DarkTextSecondary,
        accent = DarkAccent,
        accentText = DarkAccentText,
    )

private val LightFinTrackColors =
    FinTrackColors(
        positive = LightPositive,
        negative = LightNegative,
        hairline = LightHairline,
        surface1 = LightSurface1,
        surface2 = LightSurface2,
        textSecondary = LightTextSecondary,
        accent = LightAccent,
        accentText = LightAccentText,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkAccent,
        onPrimary = DarkOnAccent,
        primaryContainer = DarkSurface2,
        onPrimaryContainer = DarkTextPrimary,
        secondary = DarkTextPrimary,
        onSecondary = DarkOnAccent,
        secondaryContainer = DarkSurface2,
        onSecondaryContainer = DarkTextPrimary,
        tertiary = DarkTextPrimary,
        onTertiary = DarkOnAccent,
        tertiaryContainer = DarkSurface2,
        onTertiaryContainer = DarkTextPrimary,
        background = DarkBg,
        onBackground = DarkTextPrimary,
        surface = DarkSurface1,
        onSurface = DarkTextPrimary,
        surfaceVariant = DarkSurface2,
        onSurfaceVariant = DarkTextSecondary,
        surfaceTint = Color.Transparent,
        outline = DarkTextSecondary,
        outlineVariant = DarkHairline,
        error = DarkNegative,
        onError = DarkTextPrimary,
        errorContainer = DarkSurface2,
        onErrorContainer = DarkNegative,
    ).withSurfaceContainers(DarkBg, DarkSurface1, DarkSurface2)

private val LightColorScheme =
    lightColorScheme(
        primary = LightAccent,
        onPrimary = LightOnAccent,
        primaryContainer = LightSurface2,
        onPrimaryContainer = LightTextPrimary,
        secondary = LightTextPrimary,
        onSecondary = LightOnAccent,
        secondaryContainer = LightSurface2,
        onSecondaryContainer = LightTextPrimary,
        tertiary = LightTextPrimary,
        onTertiary = LightOnAccent,
        tertiaryContainer = LightSurface2,
        onTertiaryContainer = LightTextPrimary,
        background = LightBg,
        onBackground = LightTextPrimary,
        surface = LightSurface1,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurface2,
        onSurfaceVariant = LightTextSecondary,
        surfaceTint = Color.Transparent,
        outline = LightTextSecondary,
        outlineVariant = LightHairline,
        error = LightNegative,
        onError = LightOnAccent,
        errorContainer = LightSurface2,
        onErrorContainer = LightNegative,
    ).withSurfaceContainers(LightBg, LightSurface1, LightSurface2)

/** Material's surfaceContainer* roles collapse onto the two surface tones of the design. */
private fun ColorScheme.withSurfaceContainers(
    lowest: Color,
    low: Color,
    high: Color,
): ColorScheme =
    copy(
        surfaceContainerLowest = lowest,
        surfaceContainerLow = low,
        surfaceContainer = high,
        surfaceContainerHigh = high,
        surfaceContainerHighest = high,
        surfaceBright = high,
        surfaceDim = lowest,
    )

@Composable
fun FinTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val finTrackColors = if (darkTheme) DarkFinTrackColors else LightFinTrackColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.setBackgroundDrawable(colorScheme.background.toArgb().toDrawable())
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalFinTrackColors provides finTrackColors,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = FinTrackShapes,
            content = content,
        )
    }
}
