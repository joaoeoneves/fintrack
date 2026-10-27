package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.domain.usecase.CheckCategoryUsage
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CheckCategoryUsage] and its recurring-item check. */
class CheckCategoryUsageRecurringTest {
    private val gym = CategoryId("gym")

    private fun usage(recurring: FakeRecurringRepository) =
        CheckCategoryUsage(
            expenseRepository = FakeExpenseRepository(),
            budgetRepository = FakeBudgetRepository(),
            recurringRepository = recurring,
        )

    @Test
    fun recurringExpenseUsingTheCategory_isInUse() =
        runBlocking {
            val result = usage(FakeRecurringRepository(listOf(recurringItem(categoryId = gym))))(gym).getOrThrow()

            assertEquals(CategoryUsage(hasExpenses = false, hasBudget = false, hasRecurring = true), result)
            assertTrue(result.inUse)
        }

    @Test
    fun recurringItemsOfOtherCategoriesOrIncome_dontCount() =
        runBlocking {
            val items = listOf(recurringItem(id = "a"), recurringItem(id = "b", type = RecurringType.INCOME))

            val result = usage(FakeRecurringRepository(items))(gym).getOrThrow()

            assertFalse(result.hasRecurring)
            assertFalse(result.inUse)
        }

    @Test
    fun hasRecurringDefaultsToFalse() {
        assertFalse(CategoryUsage(hasExpenses = false, hasBudget = false).inUse)
        assertTrue(CategoryUsage(hasExpenses = false, hasBudget = false, hasRecurring = true).inUse)
    }

    @Test
    fun recurringObserveFailure_isAFailure() =
        runBlocking {
            val error = IllegalStateException("recurring down")

            val result = usage(FakeRecurringRepository(nextObserveError = error))(gym)

            assertEquals(error, result.exceptionOrNull())
        }
}
