package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [budgetProgressOf] and [CategoryBudget.isOverBudget]. */
class BudgetProgressTest {
    private val date = Instant.parse("2026-10-10T12:00:00Z")

    private fun List<CategoryBudget>.of(category: ExpenseCategory) = single { it.category == category }

    @Test
    fun everyCategoryPresent_inEnumOrder_withNullLimitsWhenUnset() {
        val progress = budgetProgressOf(emptyList(), listOf(Budget(ExpenseCategory.FOOD, 10_000L)))

        assertEquals(ExpenseCategory.entries.toList(), progress.map { it.category })
        assertEquals(10_000L, progress.of(ExpenseCategory.FOOD).limitCents)
        ExpenseCategory.entries.filter { it != ExpenseCategory.FOOD }.forEach {
            assertNull(progress.of(it).limitCents)
            assertFalse(progress.of(it).isOverBudget)
        }
        progress.forEach { assertEquals(0L, it.spentCents) }
    }

    @Test
    fun spentIsNet_clampedAtZero() {
        val progress =
            budgetProgressOf(
                listOf(
                    testExpense("e", 1_000L, date, ExpenseCategory.SHOPPING),
                    testExpense("r", 3_000L, date, ExpenseCategory.SHOPPING, isRefund = true),
                ),
                listOf(Budget(ExpenseCategory.SHOPPING, 500L)),
            )

        assertEquals(0L, progress.of(ExpenseCategory.SHOPPING).spentCents)
        assertFalse(progress.of(ExpenseCategory.SHOPPING).isOverBudget)
    }

    @Test
    fun refundInAnotherCategory_hasNoEffect() {
        val progress =
            budgetProgressOf(
                listOf(
                    testExpense("food", 4_000L, date, ExpenseCategory.FOOD),
                    testExpense("r", 3_000L, date, ExpenseCategory.SHOPPING, isRefund = true),
                ),
                emptyList(),
            )

        assertEquals(4_000L, progress.of(ExpenseCategory.FOOD).spentCents)
        assertEquals(0L, progress.of(ExpenseCategory.SHOPPING).spentCents)
    }

    @Test
    fun spentExactlyAtLimit_isNotOver_oneCentAbove_isOver() {
        val budgets = listOf(Budget(ExpenseCategory.FOOD, 10_000L))

        val atLimit = budgetProgressOf(listOf(testExpense("a", 10_000L, date, ExpenseCategory.FOOD)), budgets)
        val above = budgetProgressOf(listOf(testExpense("a", 10_001L, date, ExpenseCategory.FOOD)), budgets)

        assertFalse(atLimit.of(ExpenseCategory.FOOD).isOverBudget)
        assertTrue(above.of(ExpenseCategory.FOOD).isOverBudget)
    }

    @Test
    fun zeroLimit_overWithAnySpend_notOverWhenNothingSpent() {
        val budgets = listOf(Budget(ExpenseCategory.RECURRING, 0L))

        assertFalse(budgetProgressOf(emptyList(), budgets).of(ExpenseCategory.RECURRING).isOverBudget)
        assertTrue(
            budgetProgressOf(listOf(testExpense("a", 1L, date, ExpenseCategory.RECURRING)), budgets)
                .of(ExpenseCategory.RECURRING)
                .isOverBudget,
        )
    }
}
