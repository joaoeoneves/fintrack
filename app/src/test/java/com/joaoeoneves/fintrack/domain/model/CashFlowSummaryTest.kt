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

    private fun CashFlowSummary.total(category: ExpenseCategory): Long {
        val entry = categoryTotals.single { it.category == category }
        return entry.totalCents
    }

    @Test
    fun empty_isAllZeros_withNullSavingsRate() {
        val summary = cashFlowSummaryOf(emptyList(), emptyList())

        assertEquals(0L, summary.incomeCents)
        assertEquals(0L, summary.spendingCents)
        assertEquals(0L, summary.savedCents)
        assertEquals(0L, summary.outflowCents)
        assertEquals(0L, summary.netCents)
        assertNull(summary.savingsRate)
        assertEquals(ExpenseCategory.entries.map { CategoryTotal(it, 0L) }, summary.categoryTotals)
    }

    @Test
    fun spendingRefund_reducesSpending_notSaved() {
        val summary =
            cashFlowSummaryOf(
                listOf(
                    testExpense("e", 5_000L, date, ExpenseCategory.SHOPPING),
                    testExpense("r", 2_000L, date, ExpenseCategory.SHOPPING, isRefund = true),
                ),
                emptyList(),
            )

        assertEquals(3_000L, summary.spendingCents)
        assertEquals(0L, summary.savedCents)
        assertEquals(3_000L, summary.total(ExpenseCategory.SHOPPING))
    }

    @Test
    fun investmentsRefund_reducesSaved_notSpending() {
        val summary =
            cashFlowSummaryOf(
                listOf(
                    testExpense("e", 10_000L, date, ExpenseCategory.INVESTMENTS),
                    testExpense("r", 4_000L, date, ExpenseCategory.INVESTMENTS, isRefund = true),
                ),
                emptyList(),
            )

        assertEquals(6_000L, summary.savedCents)
        assertEquals(0L, summary.spendingCents)
        assertEquals(6_000L, summary.outflowCents)
    }

    @Test
    fun refundOnlyCategory_staysNegative_andNetCanExceedIncome() {
        val summary =
            cashFlowSummaryOf(
                listOf(testExpense("r", 2_500L, date, ExpenseCategory.TRANSFER, isRefund = true)),
                listOf(testIncome("i", 1_000L, date)),
            )

        assertEquals(-2_500L, summary.total(ExpenseCategory.TRANSFER))
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
            )

        assertEquals(-2_000L, summary.netCents)
    }

    @Test
    fun food_countsAsSpending() {
        val summary = cashFlowSummaryOf(listOf(testExpense("f", 1_234L, date, ExpenseCategory.FOOD)), emptyList())

        assertEquals(1_234L, summary.spendingCents)
        assertEquals(0L, summary.savedCents)
        assertEquals(1_234L, summary.total(ExpenseCategory.FOOD))
    }

    @Test
    fun savingsRate_isSavedOverIncome() {
        val summary =
            cashFlowSummaryOf(
                listOf(
                    testExpense("inv", 20_000L, date, ExpenseCategory.INVESTMENTS),
                    testExpense("shop", 50_000L, date, ExpenseCategory.SHOPPING),
                ),
                listOf(testIncome("a", 60_000L, date), testIncome("b", 40_000L, date)),
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
                ExpenseCategory.entries.reversed().map { testExpense(it.name, 100L, date, it) },
                emptyList(),
            )

        assertEquals(ExpenseCategory.entries.toList(), summary.categoryTotals.map { it.category })
        summary.categoryTotals.forEach { assertEquals(100L, it.totalCents) }
    }
}
