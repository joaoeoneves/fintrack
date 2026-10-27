package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budget tests for [DashboardViewModel]: presence/defaults, `isOverBudget`, `onSetBudget`, and
 * which calendar month budget spend is evaluated against for each [Period] variant. See
 * [DashboardViewModelTestBase] for shared fixtures/setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelBudgetTest : DashboardViewModelTestBase() {
    private fun DashboardUiState.Content.budget(category: Category) = budgets.single { it.category == category }

    /** One shopping expense in the previous month and one in the current month. */
    private fun twoMonthRepo() =
        FakeExpenseRepository(
            listOf(
                expense(id = "sep", amountCents = 9_000L, date = noonOn(previousMonth, 15)),
                expense(id = "oct", amountCents = 1_500L, date = noonOn(currentMonth, 5)),
            ),
        )

    // ---- presence/defaults ----

    @Test
    fun budgets_noExpensesNoBudgets_allCategoriesPresentWithNullLimitAndZeroSpent() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(BuiltInCategories.all.filterNot { it.archived }, content.budgets.map { it.category })
            assertTrue(content.budgets.all { it.limitCents == null && it.spentCents == 0L && !it.isOverBudget })
            assertEquals(currentMonth, content.budgetMonth)
            assertTrue(content.budgetMonthIsCurrent)
        }

    @Test
    fun budgets_reflectPreSeededLimits_forCategoriesWithNoSpendYet() =
        runTest(testDispatcher) {
            val budgetRepo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))
            val viewModel = viewModel(budgetRepository = budgetRepo)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(10_000L, content.budget(BuiltInCategories.SHOPPING).limitCents)
            assertEquals(0L, content.budget(BuiltInCategories.SHOPPING).spentCents)
            val others = content.budgets.filter { it.category != BuiltInCategories.SHOPPING }
            assertTrue(others.all { it.limitCents == null })
        }

    // ---- which month budgets are evaluated against ----

    @Test
    fun currentMonth_budgetSpendCountsOnlyCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(1_500L, content.budget(BuiltInCategories.SHOPPING).spentCents)
            assertEquals(currentMonth, content.budgetMonth)
            assertTrue(content.budgetMonthIsCurrent)
        }

    @Test
    fun selectedPastMonth_budgetsFollowThatMonth_andAreNotCurrent() =
        runTest(testDispatcher) {
            val budgetRepo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 5_000L)))
            val viewModel = viewModel(expenseRepository = twoMonthRepo(), budgetRepository = budgetRepo)
            collect(viewModel)

            viewModel.onPreviousMonth()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.Month(previousMonth), content.period)
            assertEquals(previousMonth, content.budgetMonth)
            assertFalse(content.budgetMonthIsCurrent)
            val shopping = content.budget(BuiltInCategories.SHOPPING)
            assertEquals(9_000L, shopping.spentCents)
            assertEquals(5_000L, shopping.limitCents)
            assertTrue(shopping.isOverBudget)
        }

    @Test
    fun selectedMonthWithNoSpend_budgetsShowZeroSpent() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Month(YearMonth.of(2026, 3)))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(YearMonth.of(2026, 3), content.budgetMonth)
            assertTrue(content.budgets.all { it.spentCents == 0L })
        }

    @Test
    fun rollingPeriod_includesBothMonthsInTotals_butBudgetsStayOnCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Rolling(TimeRange.ONE_YEAR))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(10_500L, content.outflowCents)
            assertEquals(currentMonth, content.budgetMonth)
            assertTrue(content.budgetMonthIsCurrent)
            assertEquals(1_500L, content.budget(BuiltInCategories.SHOPPING).spentCents)
        }

    @Test
    fun customPeriodInPastMonth_budgetsStayOnCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20)))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(9_000L, content.outflowCents)
            assertEquals(currentMonth, content.budgetMonth)
            assertTrue(content.budgetMonthIsCurrent)
            assertEquals(1_500L, content.budget(BuiltInCategories.SHOPPING).spentCents)
        }

    @Test
    fun budgetSpend_includesInvestmentsCategory_likeAnyOther() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense(
                            id = "i",
                            amountCents = 2_000L,
                            category = BuiltInCategories.INVESTMENTS,
                            date = now,
                        ),
                    ),
                )
            val viewModel = viewModel(expenseRepository = repo)
            collect(viewModel)

            assertEquals(2_000L, viewModel.content().budget(BuiltInCategories.INVESTMENTS).spentCents)
        }

    // ---- isOverBudget ----

    @Test
    fun isOverBudget_falseWhenNoLimitSet_regardlessOfSpend() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(expense(id = "big", amountCents = 1_000_000L, date = now)))
            val viewModel = viewModel(expenseRepository = repo)
            collect(viewModel)

            val shopping = viewModel.content().budget(BuiltInCategories.SHOPPING)
            assertEquals(1_000_000L, shopping.spentCents)
            assertNull(shopping.limitCents)
            assertFalse(shopping.isOverBudget)
        }

    @Test
    fun isOverBudget_falseAtExactLimit_trueOneCentOver() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(expense(id = "at", amountCents = 10_000L, date = now)))
            val budgetRepo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)))
            val viewModel = viewModel(expenseRepository = repo, budgetRepository = budgetRepo)
            collect(viewModel)
            assertFalse(viewModel.content().budget(BuiltInCategories.SHOPPING).isOverBudget)

            repo.addExpense(expense(id = "cent", amountCents = 1L, date = now))
            advanceUntilIdle()

            assertTrue(viewModel.content().budget(BuiltInCategories.SHOPPING).isOverBudget)
        }

    @Test
    fun isOverBudget_zeroLimit_anyPositiveSpendIsOver_zeroSpendIsNot() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense(
                            id = "t",
                            amountCents = 1L,
                            BuiltInCategories.TRANSFER,
                            now,
                        ),
                    ),
                )
            val budgetRepo =
                FakeBudgetRepository(
                    listOf(
                        Budget(BuiltInCategories.TRANSFER.id, 0L),
                        Budget(BuiltInCategories.SHOPPING.id, 0L),
                    ),
                )
            val viewModel = viewModel(expenseRepository = repo, budgetRepository = budgetRepo)
            collect(viewModel)

            assertTrue(viewModel.content().budget(BuiltInCategories.TRANSFER).isOverBudget)
            assertFalse(viewModel.content().budget(BuiltInCategories.SHOPPING).isOverBudget)
        }

    // ---- onSetBudget ----

    @Test
    fun onSetBudget_callsThroughToBudgetRepository_andUiStateReflectsNewLimit() =
        runTest(testDispatcher) {
            val budgetRepo = FakeBudgetRepository()
            val viewModel = viewModel(budgetRepository = budgetRepo)
            collect(viewModel)
            assertNull(viewModel.content().budget(BuiltInCategories.SHOPPING).limitCents)

            viewModel.onSetBudget(BuiltInCategories.SHOPPING, 10_000L)
            advanceUntilIdle()

            assertEquals(10_000L, viewModel.content().budget(BuiltInCategories.SHOPPING).limitCents)
            assertEquals(listOf(Budget(BuiltInCategories.SHOPPING.id, 10_000L)), budgetRepo.observeBudgets().first())
        }

    @Test
    fun onSetBudget_updatingExistingLimit_replacesRatherThanDuplicates() =
        runTest(testDispatcher) {
            val budgetRepo = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 5_000L)))
            val viewModel = viewModel(budgetRepository = budgetRepo)
            collect(viewModel)

            viewModel.onSetBudget(BuiltInCategories.SHOPPING, 7_500L)
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(BuiltInCategories.all.filterNot { it.archived }.size, content.budgets.size)
            assertEquals(7_500L, content.budget(BuiltInCategories.SHOPPING).limitCents)
        }

    @Test
    fun onSetBudget_whileViewingPastMonth_limitAppliesToThatMonthsSpend() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)
            viewModel.onPreviousMonth()
            advanceUntilIdle()

            viewModel.onSetBudget(BuiltInCategories.SHOPPING, 8_000L)
            advanceUntilIdle()

            val shopping = viewModel.content().budget(BuiltInCategories.SHOPPING)
            assertEquals(8_000L, shopping.limitCents)
            assertEquals(9_000L, shopping.spentCents)
            assertTrue(shopping.isOverBudget)
        }

    @Test
    fun budgetSpend_reflectsExpenseAddedAfterCollection() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = viewModel(expenseRepository = repo)
            collect(viewModel)

            repo.addExpense(expense(id = "late", amountCents = 2_500L, date = now))
            advanceUntilIdle()
            viewModel.onRetry()
            advanceUntilIdle()

            assertEquals(2_500L, viewModel.content().budget(BuiltInCategories.SHOPPING).spentCents)
        }
}
