package com.joaoeoneves.fintrack.ui.common

import java.text.NumberFormat
import java.util.Locale

private const val PERCENT_BASE = 100.0

/** Formats a whole [percent] (25 = 25%) the same way as the [fraction] overload. */
fun formatPercent(
    percent: Int,
    locale: Locale,
): String = formatPercent(percent / PERCENT_BASE, locale)

/** Formats a [fraction] (0.25 = 25%) as a whole-number percentage following [locale] ("25%" / pt-PT "25 %"). */
fun formatPercent(
    fraction: Double,
    locale: Locale,
): String =
    NumberFormat
        .getPercentInstance(locale)
        .apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }.format(fraction)
