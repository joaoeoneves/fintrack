// Typography tokens are named font sizes / line heights; naming each literal would only rename it.
@file:Suppress("MagicNumber")

package com.joaoeoneves.fintrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.R

val InterFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** Tabular figures so amounts keep a fixed width while they animate. */
private const val TABULAR_FIGURES = "tnum"

private fun style(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val Typography =
    Typography(
        displayLarge = style(FontWeight.SemiBold, 57, 64, -0.25),
        displayMedium = style(FontWeight.SemiBold, 45, 52),
        displaySmall = style(FontWeight.SemiBold, 36, 44),
        headlineLarge = style(FontWeight.SemiBold, 32, 40),
        headlineMedium = style(FontWeight.SemiBold, 28, 36),
        headlineSmall = style(FontWeight.SemiBold, 24, 32),
        titleLarge = style(FontWeight.SemiBold, 22, 28),
        titleMedium = style(FontWeight.SemiBold, 16, 24, 0.1),
        titleSmall = style(FontWeight.Medium, 14, 20, 0.1),
        bodyLarge = style(FontWeight.Normal, 16, 24),
        bodyMedium = style(FontWeight.Normal, 14, 20),
        bodySmall = style(FontWeight.Normal, 12, 16),
        labelLarge = style(FontWeight.Medium, 14, 20, 0.1),
        labelMedium = style(FontWeight.Medium, 12, 16, 0.3),
        labelSmall = style(FontWeight.Medium, 11, 16, 0.3),
    )

/** The big balance figure on the dashboard. */
val HeroAmount =
    style(FontWeight.SemiBold, 48, 56, -1.5).copy(fontFeatureSettings = TABULAR_FIGURES)

/** Secondary large figure (safe to spend, section totals). */
val AmountLarge =
    style(FontWeight.SemiBold, 32, 40, -0.5).copy(fontFeatureSettings = TABULAR_FIGURES)

/** Regular amount text in rows and tables. */
val Amount =
    style(FontWeight.Medium, 16, 24).copy(fontFeatureSettings = TABULAR_FIGURES)

/** Sentence-case section label above a group of rows. */
val SectionLabel = style(FontWeight.Medium, 14, 20, 0.1)
