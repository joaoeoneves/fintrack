package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryTotal
import com.joaoeoneves.fintrack.domain.model.DailyAllowance
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Monthly-plan tests for [DashboardViewModel]: the plan summary, planned chart slices and the daily
 * allowance. "Now" is 2026-10-20, so the current month has 12 days left (the 20th included).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelPlanTest : DashboardViewModelTestBase() {
    private val salary = PlannedIncome(id = "salary", label = "Salary", amountCents = 120_000L, order = 0)

    private fun planViewModel(
        plannedIncomeRepository: FakePlannedIncomeRepository,
        budgets: List<Budget> = emptyList(),
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
    ) = dashboardViewModel(
        DashboardRepositories(
            expenseRepository = expenses,
            budgetRepository = FakeBudgetRepository(budgets),
            incomeRepository = FakeIncomeRepository(),
            categoryRepository = FakeCategoryRepository(),
            plannedIncomeRepository = plannedIncomeRepository,
        ),
        SavedStateHandle(),
        clock,
    )

    @Test
    fun plan_filledFromPlannedIncomeAndBudgets_andPlannedSlicesListLimitedCategories() =
        runTest(testDispatcher) {
            val budgets =
                listOf(
                    Budget(BuiltInCategories.SHOPPING.id, 24_000L),
                    Budget(BuiltInCategories.INVESTMENTS.id, 10_000L),
                )
            val viewModel = planViewModel(FakePlannedIncomeRepository(listOf(salary)), budgets)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(120_000L, content.plan.expectedIncomeCents)
            assertEquals(24_000L, content.plan.plannedSpendingCents)
            assertEquals(10_000L, content.plan.plannedSavingCents)
            assertEquals(86_000L, content.plan.safeToSpendCents)
            assertEquals(
                listOf(
                    CategoryTotal(BuiltInCategories.INVESTMENTS, 10_000L),
                    CategoryTotal(BuiltInCategories.SHOPPING, 24_000L),
                ),
                content.plannedSlices,
            )
        }

    @Test
    fun plan_nothingConfigured_isUnconfiguredWithNoSlicesAndNoAllowance() =
        runTest(testDispatcher) {
            val viewModel = planViewModel(FakePlannedIncomeRepository())
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(false, content.plan.isConfigured)
            assertEquals(emptyList<CategoryTotal>(), content.plannedSlices)
            assertNull(content.dailyAllowance)
        }

    @Test
    fun dailyAllowance_currentMonth_dividesSafeToSpendByDaysLeftIncludingToday() =
        runTest(testDispatcher) {
            val budgets = listOf(Budget(BuiltInCategories.SHOPPING.id, 24_000L))
            val viewModel = planViewModel(FakePlannedIncomeRepository(listOf(salary)), budgets)
            collect(viewModel)

            // (120000 - 24000) / 12 days (Oct 20..31)
            assertEquals(DailyAllowance(cents = 8_000L, daysLeft = 12), viewModel.content().dailyAllowance)
        }

    @Test
    fun dailyAllowance_pastMonth_isNull() =
        runTest(testDispatcher) {
            val viewModel = planViewModel(FakePlannedIncomeRepository(listOf(salary)))
            collect(viewModel)

            viewModel.onPreviousMonth()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(false, content.budgetMonthIsCurrent)
            assertNull(content.dailyAllowance)
            assertEquals(120_000L, content.plan.safeToSpendCents)
        }

    @Test
    fun dailyAllowance_safeToSpendZero_isNull() =
        runTest(testDispatcher) {
            val budgets = listOf(Budget(BuiltInCategories.SHOPPING.id, 120_000L))
            val viewModel = planViewModel(FakePlannedIncomeRepository(listOf(salary)), budgets)
            collect(viewModel)

            assertEquals(0L, viewModel.content().plan.safeToSpendCents)
            assertNull(viewModel.content().dailyAllowance)
        }

    @Test
    fun dailyAllowance_overCommitted_isNullAndSafeToSpendNegative() =
        runTest(testDispatcher) {
            val budgets = listOf(Budget(BuiltInCategories.SHOPPING.id, 150_000L))
            val viewModel = planViewModel(FakePlannedIncomeRepository(listOf(salary)), budgets)
            collect(viewModel)

            assertEquals(-30_000L, viewModel.content().plan.safeToSpendCents)
            assertNull(viewModel.content().dailyAllowance)
        }

    @Test
    fun plannedIncomeChange_reEmitsPlanAndAllowance() =
        runTest(testDispatcher) {
            val repo = FakePlannedIncomeRepository()
            val viewModel = planViewModel(repo)
            collect(viewModel)
            assertNull(viewModel.content().dailyAllowance)

            repo.savePlannedIncome(salary)
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(120_000L, content.plan.expectedIncomeCents)
            assertEquals(DailyAllowance(cents = 10_000L, daysLeft = 12), content.dailyAllowance)
        }
}
