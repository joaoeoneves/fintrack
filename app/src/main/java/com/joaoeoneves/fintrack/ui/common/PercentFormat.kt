package com.joaoeoneves.fintrack.ui.common

import java.text.NumberFormat
import java.util.Locale

private const val PERCENT_DIVISOR = 100.0

/** Formats a whole [percent] following [locale] ("25%" in English, "25 %" in pt-PT). */
fun formatPercent(
    percent: Int,
    locale: Locale,
): String = NumberFormat.getPercentInstance(locale).format(percent / PERCENT_DIVISOR)
