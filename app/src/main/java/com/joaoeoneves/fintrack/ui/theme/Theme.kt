// The 0xFF000000/0xFFFFFFFF literals below are the black/white `onPrimary`/`onError` role colors
// completing the two Material color schemes defined in this file; a named constant above each one
// would just rename the same self-evident literal, so suppress at the file level instead.
@file:Suppress("MagicNumber")

package com.joaoeoneves.fintrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Whether the effective (resolved) theme is dark, exposed to composables below [FinTrackTheme]
 * so components that need theme-aware but non-role-based colors (e.g. category colors) can read it
 * without recomputing [isSystemInDarkTheme].
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme =
    darkColorScheme(
        primary = AccentGreen,
        onPrimary = Color(0xFF000000),
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = AccentBlue,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary = AccentYellow,
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        background = DarkBg,
        onBackground = TextPrimary,
        surface = CardBg,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = TextSecondary,
        outline = OutlineDark,
        error = AccentRed,
        onError = Color(0xFFFFFFFF),
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = LightAccentGreen,
        onPrimary = Color(0xFF000000),
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        secondary = LightAccentBlue,
        secondaryContainer = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary = LightAccentYellow,
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        background = LightBg,
        onBackground = LightTextPrimary,
        surface = LightCardBg,
        onSurface = LightTextPrimary,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = LightTextSecondary,
        outline = OutlineLight,
        error = LightAccentRed,
        onError = Color(0xFFFFFFFF),
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
    )

@Composable
fun FinTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but is off by default so the intentional
    // jade/emerald palette isn't silently overridden by Material You wallpaper colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
