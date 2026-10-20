package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ExpenseCategory.FOOD] on the dashboard: it is a SPENDING category (never counted as saved),
 * gets its own category total, and its budget is measured only against FOOD expenses. See
 * [DashboardViewModelTestBase] for shared fixtures/setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelFoodCategoryTest : DashboardViewModelTestBase() {
    private fun DashboardUiState.Content.foodBudget() = budgets.single { it.category == ExpenseCategory.FOOD }

    private fun DashboardUiState.Content.totalFor(category: ExpenseCategory): Long {
        val total = categoryTotals.single { it.category == category }
        return total.totalCents
    }

    /** One expense per non-FOOD category plus two FOOD expenses, all in the current month. */
    private fun mixedRepo() =
        FakeExpenseRepository(
            listOf(
                expense(id = "f1", amountCents = 2_500L, category = ExpenseCategory.FOOD, date = daysAgo(1)),
                expense(id = "f2", amountCents = 1_000L, category = ExpenseCategory.FOOD, date = daysAgo(2)),
                expense(id = "s1", amountCents = 7_000L, category = ExpenseCategory.SHOPPING, date = daysAgo(3)),
                expense(id = "t1", amountCents = 300L, category = ExpenseCategory.TRANSFER, date = daysAgo(4)),
                expense(id = "r1", amountCents = 9_000L, category = ExpenseCategory.RECURRING, date = daysAgo(5)),
                expense(id = "i1", amountCents = 50_000L, category = ExpenseCategory.INVESTMENTS, date = daysAgo(6)),
            ),
        )

    @Test
    fun foodExpense_countsAsSpending_notSaved() =
        runTest(testDispatcher) {
            val food = expense(id = "f1", amountCents = 1_234L, category = ExpenseCategory.FOOD, date = daysAgo(1))
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(listOf(food)))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(1_234L, content.spendingCents)
            assertEquals(0L, content.savedCents)
            assertEquals(1_234L, content.outflowCents)
        }

    @Test
    fun foodExpense_appearsInCategoryTotals_onlyUnderFood() =
        runTest(testDispatcher) {
            val food = expense(id = "f1", amountCents = 1_234L, category = ExpenseCategory.FOOD, date = daysAgo(1))
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(listOf(food)))
            collect(viewModel)

            val content = viewModel.content()
            assertTrue(content.categoryTotals.any { it.category == ExpenseCategory.FOOD })
            assertEquals(1_234L, content.totalFor(ExpenseCategory.FOOD))
            val others = content.categoryTotals.filter { it.category != ExpenseCategory.FOOD }
            assertTrue(others.all { it.totalCents == 0L })
        }

    @Test
    fun mixedCategories_foodSummedSeparately_andAddedToSpending() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = mixedRepo())
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(3_500L, content.totalFor(ExpenseCategory.FOOD))
            // FOOD + SHOPPING + TRANSFER + RECURRING
            assertEquals(3_500L + 7_000L + 300L + 9_000L, content.spendingCents)
            assertEquals(50_000L, content.savedCents)
        }

    @Test
    fun foodBudget_spentComesOnlyFromFoodExpenses_underLimit() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository(listOf(Budget(ExpenseCategory.FOOD, 4_000L)))
            val viewModel = viewModel(expenseRepository = mixedRepo(), budgetRepository = budgets)
            collect(viewModel)

            val food = viewModel.content().foodBudget()
            assertEquals(4_000L, food.limitCents)
            assertEquals(3_500L, food.spentCents)
            assertFalse(food.isOverBudget)
        }

    @Test
    fun foodBudget_overLimit_onlyFromFoodSpend() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository(listOf(Budget(ExpenseCategory.FOOD, 3_499L)))
            val viewModel = viewModel(expenseRepository = mixedRepo(), budgetRepository = budgets)
            collect(viewModel)

            val food = viewModel.content().foodBudget()
            assertEquals(3_500L, food.spentCents)
            assertTrue(food.isOverBudget)
        }

    @Test
    fun foodBudget_spentExactlyAtLimit_isNotOverBudget() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository(listOf(Budget(ExpenseCategory.FOOD, 3_500L)))
            val viewModel = viewModel(expenseRepository = mixedRepo(), budgetRepository = budgets)
            collect(viewModel)

            assertFalse(viewModel.content().foodBudget().isOverBudget)
        }

    @Test
    fun foodBudget_noFoodExpenses_otherCategoriesOverSpent_staysAtZero() =
        runTest(testDispatcher) {
            val shopping =
                expense(id = "s1", amountCents = 99_000L, category = ExpenseCategory.SHOPPING, date = daysAgo(1))
            val budgets = FakeBudgetRepository(listOf(Budget(ExpenseCategory.FOOD, 1L)))
            val viewModel =
                viewModel(expenseRepository = FakeExpenseRepository(listOf(shopping)), budgetRepository = budgets)
            collect(viewModel)

            val food = viewModel.content().foodBudget()
            assertEquals(0L, food.spentCents)
            assertFalse(food.isOverBudget)
        }

    @Test
    fun foodBudget_previousMonthFoodSpend_doesNotCountTowardCurrentMonth() =
        runTest(testDispatcher) {
            val septemberFood =
                expense(
                    id = "sep",
                    amountCents = 10_000L,
                    category = ExpenseCategory.FOOD,
                    date = noonOn(previousMonth, 15),
                )
            val budgets = FakeBudgetRepository(listOf(Budget(ExpenseCategory.FOOD, 5_000L)))
            val viewModel =
                viewModel(expenseRepository = FakeExpenseRepository(listOf(septemberFood)), budgetRepository = budgets)
            collect(viewModel)

            val food = viewModel.content().foodBudget()
            assertEquals(0L, food.spentCents)
            assertFalse(food.isOverBudget)
        }
}
