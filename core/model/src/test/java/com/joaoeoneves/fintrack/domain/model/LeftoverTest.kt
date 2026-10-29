package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class LeftoverTest {
    private fun ym(
        y: Int,
        m: Int,
    ) = YearMonth.of(y, m)

    private fun totals(
        y: Int,
        m: Int,
        income: Long,
        spent: Long = 0L,
        saved: Long = 0L,
    ) = MonthTotals(ym(y, m), income, spent, saved)

    private val today = ym(2026, 10)

    @Test
    fun normal_sumsIncomeMinusSpendingMinusSaved_andAverages() {
        val data = listOf(totals(2026, 1, 1_000, 400, 100), totals(2026, 2, 1_000, 200, 0))
        val result = leftoverOf(data, ym(2026, 2), today)
        assertEquals(Leftover(1_300L, 650L, 2, ym(2026, 2)), result)
    }

    @Test
    fun runningMonthIsExcluded_evenWhenUpToIsThatMonth() {
        val data = listOf(totals(2026, 9, 500), totals(2026, 10, 9_999))
        assertEquals(Leftover(500L, 500L, 1, ym(2026, 9)), leftoverOf(data, ym(2026, 10), today))
    }

    @Test
    fun futureUpTo_isCappedAtTheLastClosedMonth() {
        val data = listOf(totals(2026, 9, 500), totals(2026, 10, 700), totals(2026, 12, 800))
        assertEquals(Leftover(500L, 500L, 1, ym(2026, 9)), leftoverOf(data, ym(2026, 12), today))
    }

    @Test
    fun january_hasNoClosedMonths() {
        val data = listOf(totals(2026, 1, 1_000))
        assertEquals(Leftover(0L, null, 0), leftoverOf(data, ym(2026, 1), ym(2026, 1)))
    }

    @Test
    fun otherYearsAreIgnored() {
        val data = listOf(totals(2025, 12, 5_000), totals(2026, 1, 100))
        assertEquals(Leftover(100L, 100L, 1, ym(2026, 1)), leftoverOf(data, ym(2026, 3), today))
    }

    @Test
    fun negativeLeftover_isKept() {
        val data = listOf(totals(2026, 1, 100, 300), totals(2026, 2, 0, 0, 50))
        assertEquals(Leftover(-250L, -125L, 2, ym(2026, 2)), leftoverOf(data, ym(2026, 2), today))
    }

    @Test
    fun emptyTotals_isZeroWithNullAverage() {
        assertEquals(Leftover(0L, null, 0), leftoverOf(emptyList(), ym(2026, 5), today))
    }

    @Test
    fun noData_hasZeroMonthsNullAverageAndNoUntil() {
        val empty = listOf(totals(2026, 1, 0), totals(2026, 2, 0), totals(2026, 3, 0))

        val result = leftoverOf(empty, ym(2026, 3), today)

        assertEquals(0, result.months)
        assertNull(result.averageCents)
        assertNull(result.until)
    }

    @Test
    fun dataOnlyFromMidYear_averagesFromThereOn() {
        val data = listOf(totals(2026, 1, 0), totals(2026, 2, 0), totals(2026, 3, 900), totals(2026, 4, 300))

        val result = leftoverOf(data, ym(2026, 4), today)

        assertEquals(Leftover(1_200L, 600L, 2, ym(2026, 4)), result)
    }

    @Test
    fun emptyMonthAfterTheFirstRecord_stillCounts() {
        val data = listOf(totals(2026, 3, 900), totals(2026, 4, 0), totals(2026, 5, 0))

        assertEquals(Leftover(900L, 300L, 3, ym(2026, 5)), leftoverOf(data, ym(2026, 5), today))
    }

    @Test
    fun until_isTheLastClosedMonth_cappedByToday() {
        val data = listOf(totals(2026, 8, 100), totals(2026, 9, 100), totals(2026, 10, 100))

        assertEquals(ym(2026, 9), leftoverOf(data, ym(2026, 12), today).until)
        assertEquals(ym(2026, 8), leftoverOf(data, ym(2026, 8), today).until)
    }

    @Test
    fun endOfMonthSalary_countsInItsOwnMonthByDate() {
        val zone = ZoneOffset.UTC
        val pay = testIncome("pay_sal_202603", 100_000L, LocalDate.of(2026, 3, 31).atTime(12, 0).toInstant(zone))
        val months = listOf(ym(2026, 3), ym(2026, 4))
        val monthly = monthlyTotalsOf(months, emptyList(), listOf(pay), emptyList(), zone)

        assertEquals(Leftover(100_000L, 100_000L, 1, ym(2026, 3)), leftoverOf(monthly, ym(2026, 3), today))
        assertEquals(Leftover(100_000L, 50_000L, 2, ym(2026, 4)), leftoverOf(monthly, ym(2026, 4), today))
    }

    @Test
    fun seriesComparison_noEarlierData_nullAverage() {
        val result = seriesComparisonOf(listOf(0L, 0L, 500L), 2)
        assertEquals(500L, result.currentCents)
        assertNull(result.previousCents)
        assertNull(result.averageCents)
        assertEquals(0, result.averageMonths)
    }

    @Test
    fun seriesComparison_skipsZeros_andAveragesTheRest() {
        val result = seriesComparisonOf(listOf(100L, 0L, 300L, 0L, 50L), 4)
        assertEquals(200L, result.averageCents)
        assertEquals(2, result.averageMonths)
        assertNull(result.previousCents)
        assertEquals(300L, seriesComparisonOf(listOf(100L, 300L, 50L), 2).previousCents)
    }

    @Test
    fun seriesComparison_windowLimitsLookBack() {
        val values = listOf(1_000L, 10L, 20L, 30L, 40L, 50L, 60L, 70L)
        val result = seriesComparisonOf(values, 7, window = 3)
        assertEquals(50L, result.averageCents)
        assertEquals(3, result.averageMonths)
        assertEquals(1, seriesComparisonOf(listOf(5L, 7L), 1, window = 1).averageMonths)
    }

    @Test
    fun seriesComparison_indexOutOfRange_isZeroCurrent() {
        val result = seriesComparisonOf(listOf(10L), 5)
        assertEquals(0L, result.currentCents)
        assertNull(result.previousCents)
        assertEquals(10L, result.averageCents)
    }
}
