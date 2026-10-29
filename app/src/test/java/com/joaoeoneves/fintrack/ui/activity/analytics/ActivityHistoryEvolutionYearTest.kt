package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.MonthTotals
import com.joaoeoneves.fintrack.domain.model.YearToDate
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** A year's Evolution chart spans January to December (up to the current month for the current year). */
class ActivityHistoryEvolutionYearTest {
    private fun year(
        year: Int,
        lastMonth: Int,
    ) = (1..lastMonth).map { YearMonth.of(year, it) }

    /** A history whose 12-month window ends at [end]; each month's value is its month number times 100. */
    private fun history(
        period: List<YearMonth>,
        end: YearMonth,
    ): Pair<ActivityHistory, List<Long>> {
        val window = (11 downTo 0).map { end.minusMonths(it.toLong()) }
        val values = window.map { it.monthValue * 100L }
        val monthly =
            MonthlyHistory(
                window.mapIndexed { i, m -> MonthTotals(m, 0L, values[i], 0L) },
                emptyMap(),
                emptyList(),
                YearToDate(end.year, 0L, 0L, 0L),
            )
        return ActivityHistory(period, false, monthly) to values
    }

    @Test
    fun currentYear_showsJanuaryUpToTheCurrentMonthOnly() {
        val (history, values) = history(year(2026, 10), YearMonth.of(2026, 10))

        val evolution = history.evolutionOf(values)!!

        assertEquals(year(2026, 10), evolution.months)
        assertEquals((1..10).map { it * 100L }, evolution.valuesCents)
        assertEquals((0..9).toSet(), evolution.highlighted)
    }

    @Test
    fun pastYear_showsJanuaryToDecember() {
        val (history, values) = history(year(2025, 12), YearMonth.of(2025, 12))

        val evolution = history.evolutionOf(values)!!

        assertEquals(12, evolution.months.size)
        assertEquals(YearMonth.of(2025, 1), evolution.months.first())
        assertEquals(YearMonth.of(2025, 12), evolution.months.last())
        assertEquals((1..12).map { it * 100L }, evolution.valuesCents)
    }

    @Test
    fun earlyInTheCurrentYear_stillShowsEverySixMonthPeriodMonth() {
        val (history, values) = history(year(2026, 6), YearMonth.of(2026, 6))

        assertEquals(6, history.evolutionOf(values)!!.months.size)
    }
}
