package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import com.joaoeoneves.fintrack.testutil.userCategory
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
        val active = BuiltInCategories.all.filterNot { it.archived }
        assertEquals(active.map { CategoryTotal(it, 0L) }, summary.categoryTotals)
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
                BuiltInCategories.all
                    .filterNot { it.archived }
                    .reversed()
                    .map { testExpense(it.id.value, 100L, date, it) },
                emptyList(),
                BuiltInCategories.all,
            )

        assertEquals(BuiltInCategories.all.filterNot { it.archived }, summary.categoryTotals.map { it.category })
        summary.categoryTotals.forEach { assertEquals(100L, it.totalCents) }
    }

    // ---- fixed spending ----

    private val rec = "rec_r1_20261001"

    @Test
    fun fixedSpending_countsOnlyRecurringOccurrences_ofSpendingKind() {
        val expenses =
            listOf(
                testExpense(rec, 50_000L, date, BuiltInCategories.SHOPPING),
                testExpense("manual", 7_000L, date, BuiltInCategories.SHOPPING),
                testExpense("rec_r2_20261001", 30_000L, date, BuiltInCategories.INVESTMENTS),
            )

        assertEquals(50_000L, fixedSpendingCentsOf(expenses, BuiltInCategories.all))
        val summary = cashFlowSummaryOf(expenses, emptyList(), BuiltInCategories.all)
        assertEquals(50_000L, summary.fixedSpendingCents)
        assertEquals(57_000L, summary.spendingCents)
        assertEquals(7_000L, summary.variableSpendingCents)
        assertEquals(30_000L, summary.savedCents)
    }

    @Test
    fun fixedSpending_netsRefundsOfRecurringOccurrences() {
        val expenses =
            listOf(
                testExpense(rec, 50_000L, date, BuiltInCategories.SHOPPING),
                testExpense("rec_r1_20261101", 10_000L, date, BuiltInCategories.SHOPPING, isRefund = true),
            )

        assertEquals(40_000L, fixedSpendingCentsOf(expenses, BuiltInCategories.all))
    }

    @Test
    fun fixedSpending_emptyOrNoRecurring_isZero_andUnknownCategoryCountsAsSpending() {
        assertEquals(0L, fixedSpendingCentsOf(emptyList(), BuiltInCategories.all))
        assertEquals(0L, fixedSpendingCentsOf(listOf(testExpense("a", 100L, date)), BuiltInCategories.all))
        val ghost = userCategory("ghost")
        assertEquals(100L, fixedSpendingCentsOf(listOf(testExpense(rec, 100L, date, ghost)), BuiltInCategories.all))
    }

    @Test
    fun fixedSpending_kindComesFromCatalog_notFromTheExpensesCategoryCopy() {
        val savingNow = BuiltInCategories.SHOPPING.copy(kind = CategoryKind.SAVING)
        val expenses = listOf(testExpense(rec, 100L, date, BuiltInCategories.SHOPPING))

        assertEquals(0L, fixedSpendingCentsOf(expenses, listOf(savingNow)))
    }
}
