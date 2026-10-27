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

    private fun List<CategoryBudget>.of(category: Category) = single { it.category == category }

    @Test
    fun everyCategoryPresent_inEnumOrder_withNullLimitsWhenUnset() {
        val progress =
            budgetProgressOf(
                emptyList(),
                listOf(Budget(BuiltInCategories.FOOD.id, 10_000L)),
                BuiltInCategories.all,
            )

        assertEquals(BuiltInCategories.all.toList(), progress.map { it.category })
        assertEquals(10_000L, progress.of(BuiltInCategories.FOOD).limitCents)
        BuiltInCategories.all.filter { it != BuiltInCategories.FOOD }.forEach {
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
                    testExpense("e", 1_000L, date, BuiltInCategories.SHOPPING),
                    testExpense("r", 3_000L, date, BuiltInCategories.SHOPPING, isRefund = true),
                ),
                listOf(Budget(BuiltInCategories.SHOPPING.id, 500L)),
                BuiltInCategories.all,
            )

        assertEquals(0L, progress.of(BuiltInCategories.SHOPPING).spentCents)
        assertFalse(progress.of(BuiltInCategories.SHOPPING).isOverBudget)
    }

    @Test
    fun refundInAnotherCategory_hasNoEffect() {
        val progress =
            budgetProgressOf(
                listOf(
                    testExpense("food", 4_000L, date, BuiltInCategories.FOOD),
                    testExpense("r", 3_000L, date, BuiltInCategories.SHOPPING, isRefund = true),
                ),
                emptyList(),
                BuiltInCategories.all,
            )

        assertEquals(4_000L, progress.of(BuiltInCategories.FOOD).spentCents)
        assertEquals(0L, progress.of(BuiltInCategories.SHOPPING).spentCents)
    }

    @Test
    fun spentExactlyAtLimit_isNotOver_oneCentAbove_isOver() {
        val budgets = listOf(Budget(BuiltInCategories.FOOD.id, 10_000L))

        val atLimit =
            budgetProgressOf(
                listOf(testExpense("a", 10_000L, date, BuiltInCategories.FOOD)),
                budgets,
                BuiltInCategories.all,
            )
        val above =
            budgetProgressOf(
                listOf(testExpense("a", 10_001L, date, BuiltInCategories.FOOD)),
                budgets,
                BuiltInCategories.all,
            )

        assertFalse(atLimit.of(BuiltInCategories.FOOD).isOverBudget)
        assertTrue(above.of(BuiltInCategories.FOOD).isOverBudget)
    }

    @Test
    fun zeroLimit_overWithAnySpend_notOverWhenNothingSpent() {
        val budgets = listOf(Budget(BuiltInCategories.RECURRING.id, 0L))

        assertFalse(
            budgetProgressOf(
                emptyList(),
                budgets,
                BuiltInCategories.all,
            ).of(BuiltInCategories.RECURRING).isOverBudget,
        )
        assertTrue(
            budgetProgressOf(
                listOf(testExpense("a", 1L, date, BuiltInCategories.RECURRING)),
                budgets,
                BuiltInCategories.all,
            ).of(BuiltInCategories.RECURRING)
                .isOverBudget,
        )
    }
}
