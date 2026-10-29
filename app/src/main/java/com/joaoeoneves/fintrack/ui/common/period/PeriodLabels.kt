package com.joaoeoneves.fintrack.ui.common.period

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.mediumDateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats [yearMonth] with the locale's own "year + month name" shape ("October 2026" /
 * "outubro de 2026"). With [titlecase] the first letter is capitalised, for standalone labels
 * (selector pill, picker header); pass false when the label sits inside a sentence or brackets.
 */
fun formatMonthLabel(
    yearMonth: YearMonth,
    locale: Locale,
    titlecase: Boolean = true,
): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
    val text = DateTimeFormatter.ofPattern(pattern, locale).format(yearMonth)
    return if (titlecase) text.titlecaseFirst(locale) else text
}

/** Short, titlecased month name for the month picker grid, e.g. "Oct" / "Out". */
fun formatShortMonthName(
    yearMonth: YearMonth,
    locale: Locale,
): String = DateTimeFormatter.ofPattern("MMM", locale).format(yearMonth).titlecaseFirst(locale)

/** Formats an inclusive custom range as e.g. "Oct 1, 2026 – Oct 15, 2026" (locale-dependent). */
fun formatCustomRangeLabel(
    start: LocalDate,
    endInclusive: LocalDate,
    locale: Locale,
): String {
    val formatter = mediumDateFormatter(locale)
    return "${formatter.format(start)} – ${formatter.format(endInclusive)}"
}

private fun String.titlecaseFirst(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** The label of [range] as shown in the period menu and selector. */
@Composable
fun rollingRangeLabel(range: TimeRange): String =
    when (range) {
        TimeRange.THREE_MONTHS -> stringResource(R.string.period_last_3_months)
        TimeRange.SIX_MONTHS -> stringResource(R.string.period_last_6_months)
        TimeRange.ONE_YEAR -> stringResource(R.string.period_last_12_months)
    }

/**
 * The selector label for [period]: the month name for month periods (including the current
 * month), the rolling range's name, or the custom range's dates.
 */
@Composable
fun periodLabel(
    period: Period,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val locale = currentLocale()
    return when (period) {
        Period.CurrentMonth -> formatMonthLabel(currentYearMonth(now, zone), locale)
        is Period.Month -> formatMonthLabel(period.yearMonth, locale)
        is Period.Year -> period.year.toString()
        is Period.Rolling -> rollingRangeLabel(period.range)
        is Period.Custom -> formatCustomRangeLabel(period.start, period.endInclusive, locale)
    }
}
