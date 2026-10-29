package com.joaoeoneves.fintrack.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.LocalDate
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

/*
 * The app's two named date formats, both built from the platform's locale data so the order, separators and
 * month names follow the language: the short date ("27/10/2026") and the day + month ("30 out." / "Oct 30").
 */

/** The short date of [locale] (day, month and year), e.g. "27/10/2026". */
fun formatShortDate(
    date: LocalDate,
    locale: Locale,
): String = mediumDateFormatter(locale).format(date)

/** The day and month of [locale] without the year, e.g. "30 out." / "Oct 30". */
fun formatDayMonth(
    date: LocalDate,
    locale: Locale,
): String = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(date)

/** [formatShortDate] in the current language. */
@Composable
fun rememberShortDateText(date: LocalDate): String {
    val locale = currentLocale()
    return remember(date, locale) { formatShortDate(date, locale) }
}

/** [formatDayMonth] in the current language. */
@Composable
fun rememberDayMonthText(date: LocalDate): String {
    val locale = currentLocale()
    return remember(date, locale) { formatDayMonth(date, locale) }
}
