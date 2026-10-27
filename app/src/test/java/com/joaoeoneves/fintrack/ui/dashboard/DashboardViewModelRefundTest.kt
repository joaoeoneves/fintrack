package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories.INVESTMENTS
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories.RECURRING
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Refund handling in [DashboardViewModel]: refunds reduce category totals, spending/saved, outflow
 * and budget spend, and raise net. See [DashboardViewModelTestBase] for shared fixtures/setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelRefundTest : DashboardViewModelTestBase() {
    private fun DashboardUiState.Content.total(category: Category): Long {
        val slice = categoryTotals.single { it.category == category }
        return slice.totalCents
    }

    private fun refund(
        id: String,
        amountCents: Long,
        category: Category = BuiltInCategories.SHOPPING,
        date: Instant,
    ) = expense(id = id, amountCents = amountCents, category = category, date = date).copy(isRefund = true)

    private fun salaryRepo(): FakeIncomeRepository {
        val salary = income(id = "salary", amountCents = 100_000L, date = daysAgo(5))
        return FakeIncomeRepository(listOf(salary))
    }

    private fun viewModelWith(
        expenses: List<Expense>,
        budgets: List<Budget> = emptyList(),
        withSalary: Boolean = false,
    ) = viewModel(
        expenseRepository = FakeExpenseRepository(expenses),
        budgetRepository = FakeBudgetRepository(budgets),
        incomeRepository = if (withSalary) salaryRepo() else FakeIncomeRepository(),
    )

    private fun DashboardUiState.Content.budget(category: Category) = budgets.single { it.category == category }

    @Test
    fun spendingRefund_lowersSpendingOutflowAndCategoryTotal_andRaisesNet() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "shoes", amountCents = 10_000L, date = daysAgo(3)),
                    refund(id = "return", amountCents = 4_000L, date = daysAgo(1)),
                    expense(id = "rent", amountCents = 50_000L, category = RECURRING, date = daysAgo(2)),
                )
            val viewModel = viewModelWith(expenses, withSalary = true)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(6_000L, content.total(BuiltInCategories.SHOPPING))
            assertEquals(50_000L, content.total(BuiltInCategories.RECURRING))
            assertEquals(56_000L, content.spendingCents)
            assertEquals(0L, content.savedCents)
            assertEquals(56_000L, content.outflowCents)
            assertEquals(44_000L, content.netCents)
            // The refund is still listed as an entry.
            assertEquals(3, content.expenses.size)
        }

    @Test
    fun investmentsRefund_lowersSavedAndSavingsRate_notSpending() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "etf", amountCents = 30_000L, category = INVESTMENTS, date = daysAgo(4)),
                    refund(id = "sold", amountCents = 10_000L, category = INVESTMENTS, date = daysAgo(1)),
                    expense(id = "coffee", amountCents = 500L, date = daysAgo(1)),
                )
            val viewModel = viewModelWith(expenses, withSalary = true)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(20_000L, content.savedCents)
            assertEquals(500L, content.spendingCents)
            assertEquals(20_500L, content.outflowCents)
            assertEquals(79_500L, content.netCents)
            assertEquals(0.2, content.savingsRate!!, 1e-9)
        }

    @Test
    fun netNegativeCategory_keepsExactNegativeTotal_andChartClampsItToZero() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "small", amountCents = 1_000L, date = daysAgo(3)),
                    refund(id = "bigReturn", amountCents = 3_500L, date = daysAgo(1)),
                    expense(id = "rent", amountCents = 20_000L, category = RECURRING, date = daysAgo(2)),
                )
            val viewModel = viewModelWith(expenses)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(-2_500L, content.total(BuiltInCategories.SHOPPING))
            assertEquals(17_500L, content.spendingCents)
            assertEquals(-17_500L, content.netCents)

            val chart = content.categoryTotals.clampedForChart()
            assertEquals(0L, chart.single { it.category == BuiltInCategories.SHOPPING }.totalCents)
            assertEquals(20_000L, chart.single { it.category == BuiltInCategories.RECURRING }.totalCents)
            assertEquals(content.categoryTotals.map { it.category }, chart.map { it.category })
        }

    @Test
    fun budgetSpent_isReducedByRefunds() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "a", amountCents = 8_000L, date = daysAgo(3)),
                    expense(id = "b", amountCents = 4_000L, date = daysAgo(2)),
                    refund(id = "ret", amountCents = 3_000L, date = daysAgo(1)),
                )
            val viewModel = viewModelWith(expenses, budgets = listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))
            collect(viewModel)

            val budget = viewModel.content().budget(BuiltInCategories.SHOPPING)
            // Gross 12,000 would be over the 10,000 limit; net 9,000 is not.
            assertEquals(9_000L, budget.spentCents)
            assertFalse(budget.isOverBudget)
        }

    @Test
    fun budgetSpent_netNegative_isFlooredAtZero() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "a", amountCents = 1_000L, date = daysAgo(3)),
                    refund(id = "ret", amountCents = 5_000L, date = daysAgo(1)),
                )
            val viewModel = viewModelWith(expenses, budgets = listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))
            collect(viewModel)

            assertEquals(0L, viewModel.content().budget(BuiltInCategories.SHOPPING).spentCents)
        }

    @Test
    fun budgetSpent_refundInOtherCategory_doesNotAffectThisCategory() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "a", amountCents = 6_000L, date = daysAgo(3)),
                    refund(id = "ret", amountCents = 5_000L, category = BuiltInCategories.TRANSFER, date = daysAgo(1)),
                )
            val viewModel = viewModelWith(expenses)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(6_000L, content.budget(BuiltInCategories.SHOPPING).spentCents)
            assertEquals(0L, content.budget(BuiltInCategories.TRANSFER).spentCents)
            assertEquals(-5_000L, content.total(BuiltInCategories.TRANSFER))
        }
}
