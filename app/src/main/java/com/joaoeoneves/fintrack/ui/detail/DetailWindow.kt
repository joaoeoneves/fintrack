package com.joaoeoneves.fintrack.ui.detail

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private const val DECEMBER = 12

/** The last month of [year]'s bars: December, or the current month while [year] is the current year. */
private fun lastMonthOfYear(
    year: Int,
    today: YearMonth,
): YearMonth = if (year >= today.year) today else YearMonth.of(year, DECEMBER)

/**
 * The months a detail page charts for [period], oldest first. A month period shows [monthCount] months, ending at
 * [routeEnd] (the month the page was opened on) and sliding only when the selected month leaves that window; a year
 * period shows the months of that calendar year up to the current month.
 */
internal fun chartMonthsOf(
    period: Period,
    routeEnd: YearMonth,
    monthCount: Int,
    now: Instant,
    zone: ZoneId,
): List<YearMonth> {
    val today = currentYearMonth(now, zone)
    return if (period is Period.Year) {
        val last = lastMonthOfYear(period.year, today)
        (1..last.monthValue).map { YearMonth.of(period.year, it) }
    } else {
        val selected = period.anchorMonth(now, zone) ?: today
        val first = routeEnd.minusMonths(monthCount - 1L)
        val end =
            when {
                selected > routeEnd -> selected
                selected < first -> minOf(selected.plusMonths(monthCount - 1L), routeEnd)
                else -> routeEnd
            }
        (monthCount - 1 downTo 0).map { end.minusMonths(it.toLong()) }
    }
}

/** The month the page opens on for [period]: its own month, or the last charted month of a year. */
internal fun routeEndOf(
    period: Period,
    now: Instant,
    zone: ZoneId,
): YearMonth {
    val today = currentYearMonth(now, zone)
    return if (period is Period.Year) lastMonthOfYear(period.year, today) else period.anchorMonth(now, zone) ?: today
}
