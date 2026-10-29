package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MonthlyHistoryTest {
    private val zone = ZoneOffset.UTC
    private val off = IncomeFunding(false) { 20 }
    private val on = IncomeFunding(true) { 20 }
    private val salary = PlannedIncome("sal", "Salário líquido", 100_000L, 0, managedKey = MANAGED_NET_SALARY)
    private val saving = userCategory("save", kind = CategoryKind.SAVING)
    private val food = userCategory("food")
    private val categories = listOf(saving, food)

    private fun at(
        y: Int,
        m: Int,
        d: Int,
    ): Instant = LocalDate.of(y, m, d).atTime(12, 0).toInstant(zone)

    private fun ym(
        y: Int,
        m: Int,
    ) = YearMonth.of(y, m)

    private fun totals(
        months: List<YearMonth>,
        expenses: List<Expense> = emptyList(),
        income: List<Income> = emptyList(),
        lines: List<PlannedIncome> = emptyList(),
        funding: IncomeFunding = off,
    ) = monthlyTotalsOf(months, expenses, income, categories, lines, funding, zone)

    @Test
    fun totals_noHistory_zerosAndNoData() {
        val result = totals(listOf(ym(2026, 9), ym(2026, 10)))
        assertEquals(2, result.size)
        assertTrue(result.all { it.incomeCents == 0L && it.spendingCents == 0L && it.savedCents == 0L })
        assertTrue(result.none { it.hasData })
    }

    @Test
    fun totals_refundNets_andCanGoNegative() {
        val result =
            totals(
                listOf(ym(2026, 10)),
                listOf(
                    testExpense("a", 1_000L, at(2026, 10, 3), food),
                    testExpense("r", 1_500L, at(2026, 10, 4), food, isRefund = true),
                ),
            )
        assertEquals(-500L, result.single().spendingCents)
        assertTrue(result.single().hasData)
    }

    @Test
    fun totals_savingCategory_countsAsSaved() {
        val result = totals(listOf(ym(2026, 10)), listOf(testExpense("s", 2_000L, at(2026, 10, 3), saving)))
        assertEquals(2_000L, result.single().savedCents)
        assertEquals(0L, result.single().spendingCents)
    }

    @Test
    fun totals_shiftOn_endOfMonthSalaryFundsNextMonth_acrossYear() {
        val pay = testIncome("pay_sal_202512", 100_000L, at(2025, 12, 31))
        val result =
            totals(listOf(ym(2025, 12), ym(2026, 1)), income = listOf(pay), lines = listOf(salary), funding = on)
        assertEquals(0L, result[0].incomeCents)
        assertEquals(100_000L, result[1].incomeCents)
    }

    @Test
    fun totals_shiftOn_salaryInLastWindowMonth_isDropped() {
        val pay = testIncome("pay_sal_202610", 100_000L, at(2026, 10, 30))
        val result =
            totals(listOf(ym(2026, 9), ym(2026, 10)), income = listOf(pay), lines = listOf(salary), funding = on)
        assertEquals(listOf(0L, 0L), result.map { it.incomeCents })
    }

    @Test
    fun totals_shiftOff_salaryStaysInItsOwnMonth() {
        val pay = testIncome("pay_sal_202610", 100_000L, at(2026, 10, 30))
        val result = totals(listOf(ym(2026, 10)), income = listOf(pay), lines = listOf(salary))
        assertEquals(100_000L, result.single().incomeCents)
    }

    @Test
    fun totals_outsideWindowIgnored() {
        val result =
            totals(
                listOf(ym(2026, 10)),
                listOf(testExpense("old", 1_000L, at(2026, 8, 1), food)),
                listOf(testIncome("old", 5_000L, at(2026, 8, 1))),
            )
        assertFalse(result.single().hasData)
    }

    @Test
    fun categoryMonthly_netsRefunds_floorsAtZero_omitsEmptyCategories() {
        val months = listOf(ym(2026, 9), ym(2026, 10))
        val result =
            categoryMonthlyOf(
                months,
                listOf(
                    testExpense("a", 1_000L, at(2026, 9, 3), food),
                    testExpense("r", 400L, at(2026, 9, 4), food, isRefund = true),
                    testExpense("b", 100L, at(2026, 10, 3), food),
                    testExpense("rr", 900L, at(2026, 10, 4), food, isRefund = true),
                ),
                categories,
                zone,
            )
        assertEquals(setOf(food.id), result.keys)
        assertEquals(listOf(600L, 0L), result[food.id])
    }

    @Test
    fun categoryMonthly_includesSaving_andUnknownCategoryIds() {
        val ghost = Category.unknown(CategoryId("ghost"))
        val result =
            categoryMonthlyOf(
                listOf(ym(2026, 10)),
                listOf(testExpense("s", 700L, at(2026, 10, 3), saving), testExpense("g", 50L, at(2026, 10, 3), ghost)),
                categories,
                zone,
            )
        assertEquals(listOf(700L), result[saving.id])
        assertEquals(listOf(50L), result[ghost.id])
    }

    private fun spendingTotals(vararg pairs: Pair<YearMonth, Long>) = pairs.map { (m, s) -> MonthTotals(m, 0L, s, 0L) }

    @Test
    fun comparison_noEarlierData_nulls() {
        val c = spendingComparisonOf(spendingTotals(ym(2026, 10) to 500L), ym(2026, 10))
        assertEquals(SpendingComparison(500L, null, null, 0), c)
    }

    @Test
    fun comparison_fewerThanSixMonths_averagesWhatExists() {
        val c =
            spendingComparisonOf(
                spendingTotals(ym(2026, 8) to 100L, ym(2026, 9) to 300L, ym(2026, 10) to 500L),
                ym(2026, 10),
            )
        assertEquals(300L, c.previousCents)
        assertEquals(200L, c.averageCents)
        assertEquals(2, c.averageMonths)
    }

    @Test
    fun comparison_gapMonthsSkipped_previousNullWhenLastMonthEmpty() {
        val c =
            spendingComparisonOf(
                spendingTotals(ym(2026, 7) to 400L, ym(2026, 8) to 0L, ym(2026, 9) to 0L, ym(2026, 10) to 100L),
                ym(2026, 10),
            )
        assertNull(c.previousCents)
        assertEquals(400L, c.averageCents)
        assertEquals(1, c.averageMonths)
    }

    @Test
    fun comparison_monthWithOnlyIncome_countsAsData() {
        val c =
            spendingComparisonOf(
                listOf(MonthTotals(ym(2026, 9), 1_000L, 0L, 0L), MonthTotals(ym(2026, 10), 0L, 200L, 0L)),
                ym(2026, 10),
            )
        assertEquals(0L, c.previousCents)
        assertEquals(0L, c.averageCents)
        assertEquals(1, c.averageMonths)
    }

    @Test
    fun comparison_moreThanSixMonths_usesLastSix() {
        val months = (0..9).map { ym(2026, 1).plusMonths(it.toLong()) }
        val pairs = months.mapIndexed { i, m -> m to (i + 1) * 100L }.toTypedArray()
        val c = spendingComparisonOf(spendingTotals(*pairs), ym(2026, 10))
        // Oct is the 10th month; the six before it are Apr..Sep = 400..900.
        assertEquals(6, c.averageMonths)
        assertEquals(650L, c.averageCents)
        assertEquals(900L, c.previousCents)
    }

    @Test
    fun percentChange_cases() {
        assertNull(percentChange(100L, 0L))
        assertNull(percentChange(100L, -5L))
        assertNull(percentChange(100L, null))
        assertEquals(50, percentChange(150L, 100L))
        assertEquals(-25, percentChange(75L, 100L))
        assertEquals(33, percentChange(4L, 3L))
        assertEquals(-100, percentChange(0L, 100L))
        assertEquals(0, percentChange(100L, 100L))
    }

    private val series =
        listOf(
            MonthTotals(ym(2025, 12), 900L, 900L, 900L),
            MonthTotals(ym(2026, 1), 1L, 10L, 100L),
            MonthTotals(ym(2026, 2), 2L, 20L, 200L),
            MonthTotals(ym(2026, 3), 4L, 40L, 400L),
        )

    @Test
    fun yearToDate_sameYearUpToMonth_excludesPreviousYearAndLater() {
        assertEquals(YearToDate(2026, 30L, 3L, 300L), yearToDateOf(series, ym(2026, 2)))
    }

    @Test
    fun yearToDate_january_isJustJanuary() {
        assertEquals(YearToDate(2026, 10L, 1L, 100L), yearToDateOf(series, ym(2026, 1)))
    }

    @Test
    fun yearToDate_noMonthsOfTheYear_zeros() {
        assertEquals(YearToDate(2027, 0L, 0L, 0L), yearToDateOf(series, ym(2027, 1)))
    }
}
