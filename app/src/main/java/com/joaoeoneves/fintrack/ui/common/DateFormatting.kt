package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The medium date pattern of [locale] ("Oct 27, 2026" in English, "27/10/2026" in pt-PT), taken
 * from the platform's locale data rather than hardcoded.
 */
fun mediumDatePattern(locale: Locale): String =
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale)

/** A medium-style date formatter for [locale] (see [mediumDatePattern]). */
fun mediumDateFormatter(locale: Locale): DateTimeFormatter {
    val pattern = mediumDatePattern(locale)
    return DateTimeFormatter.ofPattern(pattern, locale)
}

/**
 * A medium-style date formatter that follows [currentLocale], so it is rebuilt whenever the
 * configuration changes, e.g. after `AppCompatDelegate.setApplicationLocales` recreates the
 * activity for a new in-app language. A module-level `val` formatter would instead be fixed at class-load time and
 * never pick up a later in-session language change.
 */
@Composable
fun rememberLocaleAwareDateFormatter(): DateTimeFormatter {
    val locale = currentLocale()
    return remember(locale) { mediumDateFormatter(locale) }
}
