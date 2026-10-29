package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "move a category's items" operations of the guest-mode repositories, on a real temp store. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalCategoryMoveTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val expenses by lazy { LocalExpenseRepository(store, SequenceIds("e")) }
    private val budgets by lazy { LocalBudgetRepository(store) }
    private val recurring by lazy { LocalRecurringRepository(store, SequenceIds("r")) }
    private val food = CategoryId("FOOD")
    private val shopping = CategoryId("SHOPPING")
    private val ghost = CategoryId("ghost")

    private suspend fun revision() = store.data.first().revision

    // ---- expenses ----

    @Test
    fun countExpenses_countsOnlyThatCategory() =
        runStoreTest {
            expenses.addExpense(expense("a", categoryId = "FOOD"))
            expenses.addExpense(expense("b", categoryId = "FOOD"))
            expenses.addExpense(expense("c", categoryId = "SHOPPING"))

            assertEquals(2, expenses.countExpensesWithCategory(food).getOrThrow())
            assertEquals(0, expenses.countExpensesWithCategory(ghost).getOrThrow())
        }

    @Test
    fun moveExpenses_recategorizesAll_preservesIds_singleRevisionBump() =
        runStoreTest {
            expenses.addExpense(expense("a", amountCents = 111, categoryId = "FOOD"))
            expenses.addExpense(expense("b", amountCents = 222, categoryId = "FOOD"))
            expenses.addExpense(expense("c", amountCents = 333, categoryId = "SHOPPING"))
            val before = revision()

            val moved = expenses.moveExpensesToCategory(food, shopping).getOrThrow()

            assertEquals(2, moved)
            assertEquals(before + 1, revision())
            val all = expenses.getAllExpenses().getOrThrow().associateBy { it.id }
            assertEquals(setOf("a", "b", "c"), all.keys)
            assertTrue(all.values.all { it.category.id == shopping })
            assertEquals(111L, all.getValue("a").amountCents)
            assertEquals(0, expenses.countExpensesWithCategory(food).getOrThrow())
        }

    @Test
    fun moveExpenses_nothingToMove_returnsZero_writesNothing() =
        runStoreTest {
            expenses.addExpense(expense("c", categoryId = "SHOPPING"))
            val before = revision()

            assertEquals(0, expenses.moveExpensesToCategory(food, shopping).getOrThrow())
            assertEquals(before, revision())
        }

    @Test
    fun moveExpenses_unknownTarget_fails_leavesDataUntouched() =
        runStoreTest {
            expenses.addExpense(expense("a", categoryId = "FOOD"))
            val before = revision()

            assertTrue(expenses.moveExpensesToCategory(food, ghost).isFailure)
            assertEquals(before, revision())
            assertEquals(1, expenses.countExpensesWithCategory(food).getOrThrow())
        }

    @Test
    fun moveExpenses_toStoredCustomCategory_works() =
        runStoreTest {
            store.seedCategory("gym")
            expenses.addExpense(expense("a", categoryId = "FOOD"))

            assertEquals(1, expenses.moveExpensesToCategory(food, CategoryId("gym")).getOrThrow())
            assertEquals(1, expenses.countExpensesWithCategory(CategoryId("gym")).getOrThrow())
        }

    // ---- recurring ----

    @Test
    fun moveRecurring_repointsMatching_preservesIds_singleRevisionBump() =
        runStoreTest {
            recurring.saveRecurring(recurringItem(id = "r1", categoryId = food))
            recurring.saveRecurring(recurringItem(id = "r2", categoryId = food))
            recurring.saveRecurring(recurringItem(id = "r3", categoryId = shopping))
            recurring.saveRecurring(recurringItem(id = "r4", type = RecurringType.INCOME))
            val before = revision()

            val moved = recurring.moveRecurringCategory(food, shopping).getOrThrow()

            assertEquals(2, moved)
            assertEquals(before + 1, revision())
            val items = recurring.observeRecurring().first().associateBy { it.id }
            assertEquals(setOf("r1", "r2", "r3", "r4"), items.keys)
            assertEquals(shopping, items.getValue("r1").categoryId)
            assertEquals(shopping, items.getValue("r2").categoryId)
            assertEquals(null, items.getValue("r4").categoryId)
        }

    @Test
    fun moveRecurring_nothingToMove_zero_noWrite_andUnknownTargetFails() =
        runStoreTest {
            recurring.saveRecurring(recurringItem(id = "r1", categoryId = shopping))
            val before = revision()

            assertEquals(0, recurring.moveRecurringCategory(food, shopping).getOrThrow())
            assertEquals(before, revision())
            assertTrue(recurring.moveRecurringCategory(shopping, ghost).isFailure)
            assertEquals(before, revision())
        }

    // ---- budgets ----

    @Test
    fun moveBudget_noTargetBudget_movesLimit_returnsTrue_singleBump() =
        runStoreTest {
            budgets.setBudget(food, 5_000)
            budgets.setBudget(CategoryId("TRANSFER"), 1_000)
            val before = revision()

            assertTrue(budgets.moveBudget(food, shopping).getOrThrow())

            assertEquals(before + 1, revision())
            val result =
                budgets
                    .observeBudgets()
                    .first()
                    .untimed()
                    .toSet()
            assertEquals(setOf(Budget(shopping, 5_000), Budget(CategoryId("TRANSFER"), 1_000)), result)
        }

    @Test
    fun moveBudget_targetHasBudget_targetWins_sourceDropped() =
        runStoreTest {
            budgets.setBudget(food, 5_000)
            budgets.setBudget(shopping, 800)

            assertTrue(budgets.moveBudget(food, shopping).getOrThrow())

            assertEquals(listOf(Budget(shopping, 800)), budgets.observeBudgets().first().untimed())
        }

    @Test
    fun moveBudget_sourceWithoutBudget_returnsFalse_writesNothing() =
        runStoreTest {
            budgets.setBudget(shopping, 800)
            val before = revision()

            assertFalse(budgets.moveBudget(food, shopping).getOrThrow())
            assertEquals(before, revision())
            assertEquals(listOf(Budget(shopping, 800)), budgets.observeBudgets().first().untimed())
        }

    @Test
    fun moveBudget_unknownTarget_fails_keepsSourceBudget() =
        runStoreTest {
            budgets.setBudget(food, 5_000)

            assertTrue(budgets.moveBudget(food, ghost).isFailure)
            assertEquals(listOf(Budget(food, 5_000)), budgets.observeBudgets().first().untimed())
        }
}
