package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** [monthlyTotalsOf] buckets income by its own date (end-of-month paydays), and splits saved from spent. */
class MonthlyTotalsByDateTest {
    private val utc = ZoneOffset.UTC
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)

    private fun at(
        m: Int,
        d: Int,
    ) = LocalDate.of(2026, m, d).atTime(12, 0).toInstant(utc)

    @Test
    fun incomeDatedTheTwentyNinthCountsInOctober_theThirtyFirstInDecember() {
        val totals =
            monthlyTotalsOf(
                expenses = emptyList(),
                income = listOf(testIncome("a", 100_000L, at(10, 29)), testIncome("b", 200_000L, at(12, 31))),
                months = october..december,
                zone = utc,
                categories = BuiltInCategories.all,
            )

        assertEquals(listOf(100_000L, 0L, 200_000L), totals.map { it.summary.incomeCents })
    }

    @Test
    fun savingOutflow_isSavedNotSpending() {
        val etf = userCategory("etf", kind = CategoryKind.SAVING)
        val totals =
            monthlyTotalsOf(
                expenses = listOf(testExpense("i", 5_000L, at(10, 3), etf), testExpense("s", 900L, at(10, 4))),
                income = emptyList(),
                months = october..october,
                zone = utc,
                categories = BuiltInCategories.all + etf,
            )

        val summary = totals.single().summary
        assertEquals(5_000L, summary.savedCents)
        assertEquals(900L, summary.spendingCents)
    }

    @Test
    fun zeroAmounts_stayZero() {
        val totals =
            monthlyTotalsOf(
                expenses = listOf(testExpense("z", 0L, at(11, 3))),
                income = listOf(testIncome("zi", 0L, at(11, 30))),
                months = november..november,
                zone = utc,
                categories = BuiltInCategories.all,
            )

        assertEquals(0L, totals.single().summary.incomeCents)
        assertEquals(0L, totals.single().summary.outflowCents)
    }
}
