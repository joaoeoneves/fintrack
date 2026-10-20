package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.testutil.FakeStringContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Expense loading, current-month filtering, category/kind totals and observeExpenses error-state
 * tests for [DashboardViewModel]. See [DashboardViewModelTestBase] for shared fixtures/setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelExpenseTest : DashboardViewModelTestBase() {
    @Test
    fun initialState_isLoading_beforeCollectionStarts() =
        runTest(testDispatcher) {
            val viewModel = viewModel()

            assertEquals(DashboardUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun emptyRepository_producesContentWithZeroTotalsAndAllCategoriesPresent() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertFalse(content.canGoToNextMonth)
            assertTrue(content.expenses.isEmpty())
            assertTrue(content.recentExpenses.isEmpty())
            assertEquals(0L, content.outflowCents)
            assertEquals(0L, content.spendingCents)
            assertEquals(0L, content.savedCents)
            assertEquals(ExpenseCategory.entries, content.categoryTotals.map { it.category })
            assertTrue(content.categoryTotals.all { it.totalCents == 0L })
        }

    @Test
    fun defaultPeriod_isCurrentMonth_andIncludesOnlyThisCalendarMonth_boundariesIncluded() =
        runTest(testDispatcher) {
            val firstInstant = expense(id = "first", amountCents = 100L, date = Instant.parse("2026-10-01T00:00:00Z"))
            val lastInstant =
                expense(id = "last", amountCents = 200L, date = Instant.parse("2026-10-31T23:59:59.999Z"))
            val lastOfSeptember =
                expense(id = "sep", amountCents = 5_000L, date = Instant.parse("2026-09-30T23:59:59.999Z"))
            val firstOfNovember =
                expense(id = "nov", amountCents = 7_000L, date = Instant.parse("2026-11-01T00:00:00Z"))
            val repo = FakeExpenseRepository(listOf(firstInstant, lastInstant, lastOfSeptember, firstOfNovember))
            val viewModel = viewModel(expenseRepository = repo)
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(setOf(firstInstant, lastInstant), content.expenses.toSet())
            assertEquals(300L, content.outflowCents)
        }

    @Test
    fun categoryTotals_sumPerCategory_andSplitIntoSpendingAndSaved() =
        runTest(testDispatcher) {
            val expenses =
                listOf(
                    expense(id = "s1", amountCents = 1_000L, category = ExpenseCategory.SHOPPING, date = daysAgo(1)),
                    expense(id = "s2", amountCents = 500L, category = ExpenseCategory.SHOPPING, date = daysAgo(2)),
                    expense(id = "t1", amountCents = 2_000L, category = ExpenseCategory.TRANSFER, date = daysAgo(3)),
                    expense(id = "i1", amountCents = 3_000L, category = ExpenseCategory.INVESTMENTS, date = daysAgo(4)),
                    expense(id = "r1", amountCents = 4_000L, category = ExpenseCategory.RECURRING, date = daysAgo(5)),
                )
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(expenses))
            collect(viewModel)

            val content = viewModel.content()
            val totalsByCategory = content.categoryTotals.associate { it.category to it.totalCents }
            assertEquals(1_500L, totalsByCategory.getValue(ExpenseCategory.SHOPPING))
            assertEquals(2_000L, totalsByCategory.getValue(ExpenseCategory.TRANSFER))
            assertEquals(3_000L, totalsByCategory.getValue(ExpenseCategory.INVESTMENTS))
            assertEquals(4_000L, totalsByCategory.getValue(ExpenseCategory.RECURRING))
            assertEquals(7_500L, content.spendingCents)
            assertEquals(3_000L, content.savedCents)
            assertEquals(10_500L, content.outflowCents)
        }

    @Test
    fun onlyInvestments_countsAsSaved_noSpending() =
        runTest(testDispatcher) {
            val investment =
                expense(id = "i1", amountCents = 9_999L, category = ExpenseCategory.INVESTMENTS, date = daysAgo(1))
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(listOf(investment)))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(0L, content.spendingCents)
            assertEquals(9_999L, content.savedCents)
            assertEquals(9_999L, content.outflowCents)
        }

    @Test
    fun zeroAmountExpense_isListedButDoesNotChangeTotals() =
        runTest(testDispatcher) {
            val zero = expense(id = "zero", amountCents = 0L, date = daysAgo(1))
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(listOf(zero)))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(listOf(zero), content.expenses)
            assertEquals(0L, content.outflowCents)
        }

    @Test
    fun recentExpenses_returnsTop5ByDateDescending_whenMoreThan5InRange() =
        runTest(testDispatcher) {
            val all = (1..7).map { expense(id = "e$it", amountCents = 100L, date = daysAgo(it.toLong())) }
            val scrambled = listOf(all[3], all[0], all[6], all[1], all[5], all[2], all[4])
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(scrambled))
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(all.take(5), content.recentExpenses)
            assertEquals(7, content.expenses.size)
        }

    // ---- observeExpenses failure -> Error state ----

    @Test
    fun observeExpensesFailure_surfacesErrorState_withUnderlyingMessage() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = IllegalStateException("Firestore unavailable")
            val viewModel = viewModel(expenseRepository = repo)
            collect(viewModel)

            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)
        }

    @Test
    fun observeExpensesFailure_withNullExceptionMessage_fallsBackToDefaultMessage() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = RuntimeException()
            val viewModel = viewModel(expenseRepository = repo)
            collect(viewModel)

            assertEquals(DashboardUiState.Error("Something went wrong"), viewModel.uiState.value)
        }

    @Test
    fun observeExpensesFailure_withNullExceptionMessage_andRealContext_usesContextGetString() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = RuntimeException()
            val context = FakeStringContext(R.string.error_generic_fallback, "translated generic error")
            val viewModel = viewModel(expenseRepository = repo, context = context)
            collect(viewModel)

            assertEquals(DashboardUiState.Error("translated generic error"), viewModel.uiState.value)
        }

    @Test
    fun onRetry_afterClearingError_transitionsFromErrorBackToContent() =
        runTest(testDispatcher) {
            val existing = expense(id = "e1", amountCents = 1_000L, date = daysAgo(1))
            val repo = FakeExpenseRepository(listOf(existing))
            repo.nextObserveExpensesError = IllegalStateException("Firestore unavailable")
            val viewModel = viewModel(expenseRepository = repo)
            collect(viewModel)
            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)

            // The period hasn't changed, so only the retry trigger forces a re-subscription.
            repo.nextObserveExpensesError = null
            viewModel.onRetry()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(listOf(existing), content.expenses)
            assertEquals(1_000L, content.outflowCents)
        }
}
