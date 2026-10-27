package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** [cashFlowSummaryOf] and the derived [CashFlowSummary] properties. */
class CashFlowSummaryTest {
    private val date = Instant.parse("2026-10-10T12:00:00Z")

    private fun CashFlowSummary.total(category: Category): Long {
        val entry = categoryTotals.single { it.category == category }
        return entry.totalCents
    }

    @Test
    fun empty_isAllZeros_withNullSavingsRate() {
        val summary = cashFlowSummaryOf(emptyList(), emptyList(), BuiltInCategories.all)

        assertEquals(0L, summary.incomeCents)
        assertEquals(0L, summary.spendingCents)
        assertEquals(0L, summary.savedCents)
        assertEquals(0L, summary.outflowCents)
        assertEquals(0L, summary.netCents)
        assertNull(summary.savingsRate)
        assertEquals(BuiltInCategories.all.map { CategoryTotal(it, 0L) }, summary.categoryTotals)
    }

    @Test
    fun spendingRefund_reducesSpending_notSaved() {
        val summary =
            cashFlowSummaryOf(
                listOf(
                    testExpense("e", 5_000L, date, BuiltInCategories.SHOPPING),
                    testExpense("r", 2_000L, date, BuiltInCategories.SHOPPING, isRefund = true),
                ),
                emptyList(),
                BuiltInCategories.all,
            )

        assertEquals(3_000L, summary.spendingCents)
        assertEquals(0L, summary.savedCents)
        assertEquals(3_000L, summary.total(BuiltInCategories.SHOPPING))
    }

    @Test
    fun investmentsRefund_reducesSaved_notSpending() {
        val summary =
            cashFlowSummaryOf(
                listOf(
                    testExpense("e", 10_000L, date, BuiltInCategories.INVESTMENTS),
                    testExpense("r", 4_000L, date, BuiltInCategories.INVESTMENTS, isRefund = true),
                ),
                emptyList(),
                BuiltInCategories.all,
            )

        assertEquals(6_000L, summary.savedCents)
        assertEquals(0L, summary.spendingCents)
        assertEquals(6_000L, summary.outflowCents)
    }

    @Test
    fun refundOnlyCategory_staysNegative_andNetCanExceedIncome() {
        val summary =
            cashFlowSummaryOf(
                listOf(testExpense("r", 2_500L, date, BuiltInCategories.TRANSFER, isRefund = true)),
                listOf(testIncome("i", 1_000L, date)),
                BuiltInCategories.all,
            )

        assertEquals(-2_500L, summary.total(BuiltInCategories.TRANSFER))
        assertEquals(-2_500L, summary.spendingCents)
        assertEquals(-2_500L, summary.outflowCents)
        assertEquals(3_500L, summary.netCents)
    }

    @Test
    fun outflowAboveIncome_givesNegativeNet() {
        val summary =
            cashFlowSummaryOf(
                listOf(testExpense("e", 7_000L, date)),
                listOf(testIncome("i", 5_000L, date)),
                BuiltInCategories.all,
            )

        assertEquals(-2_000L, summary.netCents)
    }

    @Test
    fun food_countsAsSpending() {
        val summary =
            cashFlowSummaryOf(
                listOf(testExpense("f", 1_234L, date, BuiltInCategories.FOOD)),
                emptyList(),
                BuiltInCategories.all,
            )

        assertEquals(1_234L, summary.spendingCents)
        assertEquals(0L, summary.savedCents)
        assertEquals(1_234L, summary.total(BuiltInCategories.FOOD))
    }

    @Test
    fun savingsRate_isSavedOverIncome() {
        val summary =
            cashFlowSummaryOf(
                listOf(
                    testExpense("inv", 20_000L, date, BuiltInCategories.INVESTMENTS),
                    testExpense("shop", 50_000L, date, BuiltInCategories.SHOPPING),
                ),
                listOf(testIncome("a", 60_000L, date), testIncome("b", 40_000L, date)),
                BuiltInCategories.all,
            )

        assertEquals(100_000L, summary.incomeCents)
        assertEquals(70_000L, summary.outflowCents)
        assertEquals(30_000L, summary.netCents)
        assertEquals(0.2, summary.savingsRate!!, 1e-9)
    }

    @Test
    fun categoryTotals_areInEnumOrder_regardlessOfInputOrder() {
        val summary =
            cashFlowSummaryOf(
                BuiltInCategories.all.reversed().map { testExpense(it.id.value, 100L, date, it) },
                emptyList(),
                BuiltInCategories.all,
            )

        assertEquals(BuiltInCategories.all.toList(), summary.categoryTotals.map { it.category })
        summary.categoryTotals.forEach { assertEquals(100L, it.totalCents) }
    }
}
