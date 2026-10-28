package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/** The scheduled dates in `[from, through]`, never past the end date; empty when `from > through`. */
fun RecurringItem.occurrencesBetween(
    from: LocalDate,
    through: LocalDate,
): List<LocalDate> {
    val limit = endDate?.let { minOf(it, through) } ?: through
    if (from > limit) return emptyList()
    val firstIndex = lastIndexOnOrBefore(from.minusDays(1)) + 1
    return (firstIndex..lastIndexOnOrBefore(limit)).map { occurrence(it) }
}

/** What identifies the occurrence period of [date]: its month for monthly items, its year for yearly ones. */
internal fun RecurringItem.periodKey(date: LocalDate): Any =
    if (frequency == RecurringFrequency.YEARLY) Year.of(date.year) else YearMonth.from(date)
