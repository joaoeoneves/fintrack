package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * Which calendar month Home evaluates the plan against ([DashboardUiState.Content.budgetMonth]),
 * and that Home only ever shows month periods. Per-category budget progress now lives on the Plan
 * screen (see `MonthlyPlanViewModelProgressTest`). See [DashboardViewModelTestBase] for fixtures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelBudgetTest : DashboardViewModelTestBase() {
    /** One shopping expense in the previous month and one in the current month. */
    private fun twoMonthRepo() =
        FakeExpenseRepository(
            listOf(
                expense(id = "sep", amountCents = 9_000L, date = noonOn(previousMonth, 15)),
                expense(id = "oct", amountCents = 1_500L, date = noonOn(currentMonth, 5)),
            ),
        )

    @Test
    fun defaultMonth_isCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            collect(viewModel)

            val content = viewModel.content()
            assertEquals(currentMonth, content.budgetMonth)
            assertTrue(content.budgetMonthIsCurrent)
        }

    @Test
    fun selectedPastMonth_budgetMonthFollowsIt_andIsNotCurrent() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPreviousMonth()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.Month(previousMonth), content.period)
            assertEquals(previousMonth, content.budgetMonth)
            assertFalse(content.budgetMonthIsCurrent)
            assertEquals(9_000L, content.outflowCents)
        }

    @Test
    fun selectedMonthWithNoSpend_isStillThatMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Month(YearMonth.of(2026, 3)))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(YearMonth.of(2026, 3), content.budgetMonth)
            assertEquals(0L, content.outflowCents)
        }

    @Test
    fun rollingPeriod_isCoercedToCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Rolling(TimeRange.ONE_YEAR))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(1_500L, content.outflowCents)
            assertEquals(currentMonth, content.budgetMonth)
            assertTrue(content.budgetMonthIsCurrent)
        }

    @Test
    fun customPeriod_isCoercedToCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20)))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(1_500L, content.outflowCents)
            assertTrue(content.budgetMonthIsCurrent)
        }

    @Test
    fun rollingSelectedWhileOnPastMonth_fallsBackToCurrentMonth_notThePastOne() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)
            viewModel.onPreviousMonth()
            advanceUntilIdle()

            viewModel.onPeriodSelected(Period.Rolling(TimeRange.THREE_MONTHS))
            advanceUntilIdle()

            assertEquals(Period.CurrentMonth, viewModel.content().period)
        }

    @Test
    fun pastMonthSelectionIsKept_whenSelectingAnotherMonthPeriod() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = twoMonthRepo())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Month(previousMonth))
            advanceUntilIdle()

            assertEquals(Period.Month(previousMonth), viewModel.content().period)
        }
}
