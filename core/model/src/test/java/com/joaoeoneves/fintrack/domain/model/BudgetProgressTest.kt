package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

/** [budgetProgressOf] (spend per category, planned from recurring items only) and [CategoryBudget.isOverBudget]. */
class BudgetProgressTest {
    private val date = Instant.parse("2026-10-10T12:00:00Z")
    private val october = YearMonth.of(2026, 10)

    private fun List<CategoryBudget>.of(category: Category) = single { it.category == category }

    @Test
    fun everyCategoryPresent_inCatalogOrder_withNothingPlannedWithoutMonth() {
        val progress =
            budgetProgressOf(
                emptyList(),
                BuiltInCategories.all,
                listOf(recurringItem(categoryId = BuiltInCategories.FOOD.id)),
            )

        assertEquals(BuiltInCategories.all.filterNot { it.archived }, progress.map { it.category })
        progress.forEach {
            assertNull(it.planned)
            assertEquals(PlannedSource.NONE, it.plannedSource)
            assertEquals(0L, it.spentCents)
        }
    }

    @Test
    fun plannedComesFromTheCategorysRecurringItems_summed() {
        val items =
            listOf(
                recurringItem("a", amountCents = 10_000L, categoryId = BuiltInCategories.FOOD.id),
                recurringItem("b", amountCents = 2_500L, categoryId = BuiltInCategories.FOOD.id),
                recurringItem("c", amountCents = 700L, categoryId = BuiltInCategories.SHOPPING.id),
            )

        val progress = budgetProgressOf(emptyList(), BuiltInCategories.all, items, october)

        assertEquals(12_500L, progress.of(BuiltInCategories.FOOD).planned)
        assertEquals(PlannedSource.RECURRING, progress.of(BuiltInCategories.FOOD).plannedSource)
        assertEquals(700L, progress.of(BuiltInCategories.SHOPPING).planned)
        assertNull(progress.of(BuiltInCategories.INVESTMENTS).planned)
    }

    @Test
    fun spentIsNet_clampedAtZero() {
        val progress =
            budgetProgressOf(
                listOf(
                    testExpense("e", 1_000L, date, BuiltInCategories.SHOPPING),
                    testExpense("r", 3_000L, date, BuiltInCategories.SHOPPING, isRefund = true),
                ),
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
                BuiltInCategories.all,
            )

        assertEquals(4_000L, progress.of(BuiltInCategories.FOOD).spentCents)
        assertEquals(0L, progress.of(BuiltInCategories.SHOPPING).spentCents)
    }

    @Test
    fun spentExactlyAtPlanned_isNotOver_oneCentAbove_isOver() {
        val items = listOf(recurringItem(amountCents = 10_000L, categoryId = BuiltInCategories.FOOD.id))

        fun food(spent: Long) =
            budgetProgressOf(
                listOf(testExpense("a", spent, date, BuiltInCategories.FOOD)),
                BuiltInCategories.all,
                items,
                october,
            ).of(BuiltInCategories.FOOD)

        assertFalse(food(10_000L).isOverBudget)
        assertTrue(food(10_001L).isOverBudget)
    }

    @Test
    fun pausedOrOtherMonthItems_planNothing() {
        val paused = recurringItem("p", active = false, categoryId = BuiltInCategories.FOOD.id)
        val yearly =
            recurringItem(
                "y",
                frequency = RecurringFrequency.YEARLY,
                monthOfYear = 3,
                categoryId = BuiltInCategories.SHOPPING.id,
            )

        val progress = budgetProgressOf(emptyList(), BuiltInCategories.all, listOf(paused, yearly), october)

        assertNull(progress.of(BuiltInCategories.FOOD).planned)
        assertNull(progress.of(BuiltInCategories.SHOPPING).planned)
    }
}
