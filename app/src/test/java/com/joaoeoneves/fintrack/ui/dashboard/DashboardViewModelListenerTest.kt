package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import com.joaoeoneves.fintrack.testutil.genericErrorText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Listener lifecycle and error tests for [DashboardViewModel]: switching periods must tear
 * down the previous period's listeners, and month periods share a single expense listener between
 * the summary and the history. See [DashboardViewModelTestBase] for shared fixtures/setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelListenerTest : DashboardViewModelTestBase() {
    private val expenses = CountingExpenseRepository()
    private val income = CountingIncomeRepository()

    private fun assertActive(
        expenseListeners: Int,
        incomeListeners: Int = expenseListeners, // the month's income shares the expense listener layout
    ) {
        assertEquals("active expense listeners", expenseListeners, expenses.listeners.active)
        assertEquals("active income listeners", incomeListeners, income.listeners.active)
    }

    private fun countingViewModel(): DashboardViewModel {
        val vm = viewModel(expenseRepository = expenses, incomeRepository = income)
        return vm
    }

    private fun failingExpenses(error: Throwable) = FakeExpenseRepository(nextObserveExpensesError = error)

    @Test
    fun currentMonth_usesTheMonthAndMonthBeforeListenersPerRepository() =
        runTest(testDispatcher) {
            val viewModel = countingViewModel()
            collect(viewModel)

            assertActive(expenseListeners = 2)
            assertEquals(2, expenses.listeners.observedRanges.size)
        }

    @Test
    fun switchingMonths_keepsOnlyTheLatestPeriodsListenersActive_andRollingIsCoerced() =
        runTest(testDispatcher) {
            val viewModel = countingViewModel()
            collect(viewModel)

            // Home is month-only: rolling/custom selections coerce to the already-selected current month.
            viewModel.onPeriodSelected(Period.Rolling(TimeRange.SIX_MONTHS))
            advanceUntilIdle()
            assertActive(expenseListeners = 2)
            assertEquals(2, expenses.listeners.observedRanges.size)

            viewModel.onPreviousMonth()
            advanceUntilIdle()
            assertActive(expenseListeners = 2)
            assertEquals(4, expenses.listeners.observedRanges.size)

            viewModel.onPeriodSelected(Period.Rolling(TimeRange.ONE_YEAR))
            advanceUntilIdle()
            assertActive(expenseListeners = 2)
            assertEquals(currentMonth, viewModel.content().budgetMonth)
            // CurrentMonth, Month(previous), CurrentMonth again: two listeners each.
            assertEquals(6, expenses.listeners.observedRanges.size)
        }

    @Test
    fun observeExpensesError_surfacesErrorState() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(
                    expenseRepository = failingExpenses(IllegalStateException("expenses down")),
                )
            collect(viewModel)

            assertEquals(DashboardUiState.Error(genericErrorText), viewModel.uiState.value)
        }

    @Test
    fun observeExpensesError_withoutMessage_usesGenericFallback() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = failingExpenses(RuntimeException()))
            collect(viewModel)

            assertEquals(
                DashboardUiState.Error(genericErrorText),
                viewModel.uiState.value,
            )
        }

    @Test
    fun observeExpensesError_onRollingPeriod_surfacesErrorState() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(
                    expenseRepository = failingExpenses(IllegalStateException("expenses down")),
                )
            viewModel.onPeriodSelected(Period.Rolling(TimeRange.THREE_MONTHS))
            collect(viewModel)

            assertEquals(DashboardUiState.Error(genericErrorText), viewModel.uiState.value)
        }
}
