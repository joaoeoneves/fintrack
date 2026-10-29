package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** The month's headline numbers, the per-month actuals and the money carried over between months. */
class MonthFiguresTest {
    private val zone = ZoneOffset.UTC

    private fun actual(
        month: YearMonth,
        income: Long,
        spent: Long,
        saved: Long = 0L,
    ) = MonthActuals(month, income, spent, saved)

    private fun m(
        year: Int,
        month: Int,
    ) = YearMonth.of(year, month)

    @Test
    fun monthFiguresOf_addUp_andRecurringIsNotCountedInEnvelopes() {
        val summary =
            EnvelopesSummary(
                envelopes = emptyList(),
                unenveloped = emptyList(),
                freeCents = 40_000L,
                baseCents = 100_000L,
                committedCents = 60_000L,
                savingCommittedCents = 10_000L,
                incomeCents = 100_000L,
                recurringCents = 30_000L,
            )

        val figures = monthFiguresOf(summary, carryCents = 5_000L)

        assertEquals(30_000L, figures.recurringCents)
        // 100 - 40 free - 10 saving - 30 recurring.
        assertEquals(20_000L, figures.envelopesCents)
        assertEquals(10_000L, figures.savingCents)
        assertEquals(5_000L + 100_000L - 30_000L - 20_000L - 10_000L, figures.availableCents)
        assertEquals(45_000L, figures.availableCents)
    }

    @Test
    fun availableCents_canBeNegative_withNegativeCarry() {
        val figures = MonthFigures(carryCents = -50_000L, incomeCents = 10_000L, 0L, 0L, 0L)

        assertEquals(-40_000L, figures.availableCents)
    }

    @Test
    fun monthFiguresOf_emptySummary_isAllZeroPlusCarry() {
        val figures = monthFiguresOf(EnvelopesSummary(emptyList(), emptyList(), 0L), 1_234L)

        assertEquals(MonthFigures(1_234L, 0L, 0L, 0L, 0L), figures)
        assertEquals(1_234L, figures.availableCents)
    }

    @Test
    fun leftover_isIncomeMinusSpentMinusSaved_andCanBeNegative() {
        assertEquals(700L, actual(m(2026, 9), 1_000L, 200L, 100L).leftoverCents)
        assertEquals(-300L, actual(m(2026, 9), 0L, 200L, 100L).leftoverCents)
    }

    @Test
    fun monthActualsOf_groupsByMonth_sortedAndSplitsSavings() {
        val savingsCat = BuiltInCategories.INVESTMENTS
        val catalog = BuiltInCategories.all
        val expenses =
            listOf(
                testExpense("a", 1_000L, Instant.parse("2026-10-05T12:00:00Z")),
                testExpense("b", 400L, Instant.parse("2026-09-05T12:00:00Z")),
                testExpense("c", 500L, Instant.parse("2026-09-06T12:00:00Z"), savingsCat),
            )
        val incomes = listOf(testIncome("i", 3_000L, Instant.parse("2026-09-10T12:00:00Z")))

        val result =
            monthActualsOf(expenses, incomes, catalog, emptyList(), IncomeFunding.unshifted(m(2026, 10), 20), zone)

        assertEquals(
            listOf(actual(m(2026, 9), 3_000L, 400L, 500L), actual(m(2026, 10), 0L, 1_000L)),
            result,
        )
    }

    @Test
    fun monthActualsOf_attributesShiftedSalaryToTheFundedMonth() {
        val salary = PlannedIncome("sal", "Salary", 100_000L, 0, managedKey = "salary")
        val funding = IncomeFunding(true) { 20 }
        val incomes = listOf(testIncome("pay_sal_202609", 100_000L, Instant.parse("2026-09-30T12:00:00Z")))

        val result = monthActualsOf(emptyList(), incomes, BuiltInCategories.all, listOf(salary), funding, zone)

        assertEquals(listOf(actual(m(2026, 10), 100_000L, 0L)), result)
    }

    @Test
    fun monthActualsOf_empty_isEmpty() {
        val funding = IncomeFunding.unshifted(m(2026, 10), 20)

        assertEquals(
            emptyList<MonthActuals>(),
            monthActualsOf(emptyList(), emptyList(), BuiltInCategories.all, emptyList(), funding, zone),
        )
    }

    @Test
    fun carryOver_withNoHistory_isZero() {
        assertEquals(0L, carryOverCents(m(2026, 10), emptyList()))
    }

    @Test
    fun carryOver_sumsLeftoversOfEarlierMonths_cumulatively() {
        val actuals =
            listOf(
                actual(m(2026, 8), 100_000L, 60_000L),
                actual(m(2026, 9), 100_000L, 70_000L, 10_000L),
            )

        assertEquals(40_000L + 20_000L, carryOverCents(m(2026, 10), actuals))
        assertEquals(40_000L, carryOverCents(m(2026, 9), actuals))
    }

    @Test
    fun carryOver_canBeNegative() {
        val actuals = listOf(actual(m(2026, 9), 10_000L, 50_000L))

        assertEquals(-40_000L, carryOverCents(m(2026, 10), actuals))
    }

    @Test
    fun carryOver_startsAtTheFirstMonthWithReceivedIncome() {
        val actuals =
            listOf(
                actual(m(2026, 6), 0L, 30_000L),
                actual(m(2026, 7), 0L, 30_000L),
                actual(m(2026, 8), 100_000L, 20_000L),
                actual(m(2026, 9), 0L, 10_000L),
            )

        assertEquals(80_000L - 10_000L, carryOverCents(m(2026, 10), actuals))
    }

    @Test
    fun carryOver_withNoIncomeAtAll_isZero() {
        assertEquals(0L, carryOverCents(m(2026, 10), listOf(actual(m(2026, 9), 0L, 5_000L))))
    }

    @Test
    fun carryOver_ignoresTheViewedMonthAndLater() {
        val actuals =
            listOf(
                actual(m(2026, 9), 100L, 0L),
                actual(m(2026, 10), 1_000_000L, 0L),
                actual(m(2026, 11), 1_000_000L, 0L),
            )

        assertEquals(100L, carryOverCents(m(2026, 10), actuals))
    }

    @Test
    fun carryOver_looksBackAtMostTwelveMonths() {
        val viewed = m(2026, 10)
        val actuals =
            listOf(
                actual(viewed.minusMonths(13), 999_999L, 0L),
                actual(viewed.minusMonths(12), 1_000L, 0L),
                actual(viewed.minusMonths(1), 0L, 100L),
            )

        assertEquals(12L, CARRY_LOOKBACK_MONTHS)
        assertEquals(900L, carryOverCents(viewed, actuals))
    }

    @Test
    fun carryOver_ordersByMonth_whateverTheInputOrder() {
        val actuals = listOf(actual(m(2026, 9), 0L, 10_000L), actual(m(2026, 8), 50_000L, 0L))

        assertEquals(40_000L, carryOverCents(m(2026, 10), actuals))
    }
}
