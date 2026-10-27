package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.testutil.MutableClock
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * Period selection/navigation tests for [DashboardViewModel]: previous/next month, rolling and
 * custom periods, SavedStateHandle persistence and month rollover. See [DashboardViewModelTestBase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelPeriodTest : DashboardViewModelTestBase() {
    private val sepExpense = expense(id = "sep", amountCents = 3_000L, date = noonOn(YearMonth.of(2026, 9), 10))
    private val octExpense = expense(id = "oct", amountCents = 1_000L, date = noonOn(YearMonth.of(2026, 10), 10))
    private val augExpense = expense(id = "aug", amountCents = 7_000L, date = noonOn(YearMonth.of(2026, 8), 10))
    private val sepIncome = income(id = "sep-in", amountCents = 50_000L, date = noonOn(YearMonth.of(2026, 9), 28))

    private fun expenses() = FakeExpenseRepository(listOf(augExpense, sepExpense, octExpense))

    private fun incomes() = FakeIncomeRepository(listOf(sepIncome))

    @Test
    fun onPreviousMonth_loadsPreviousMonthsExpensesAndIncome_andEnablesNext() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = expenses(), incomeRepository = incomes())
            collect(viewModel)
            assertEquals(listOf(octExpense), viewModel.content().expenses)

            viewModel.onPreviousMonth()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.Month(previousMonth), content.period)
            assertTrue(content.canGoToNextMonth)
            assertEquals(listOf(sepExpense), content.expenses)
            assertEquals(50_000L, content.incomeCents)
        }

    @Test
    fun onNextMonth_fromPreviousMonth_returnsToCurrentMonth_andDisablesNext() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = expenses())
            collect(viewModel)
            viewModel.onPreviousMonth()
            advanceUntilIdle()

            viewModel.onNextMonth()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertFalse(content.canGoToNextMonth)
            assertEquals(listOf(octExpense), content.expenses)
        }

    @Test
    fun onNextMonth_onCurrentMonth_isNoOp() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = expenses())
            collect(viewModel)

            viewModel.onNextMonth()
            advanceUntilIdle()

            assertEquals(Period.CurrentMonth, viewModel.content().period)
            assertEquals(listOf(octExpense), viewModel.content().expenses)
        }

    @Test
    fun twoPreviousMonths_thenNext_walksBackAndForward() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = expenses())
            collect(viewModel)

            viewModel.onPreviousMonth()
            viewModel.onPreviousMonth()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 8)), viewModel.content().period)
            assertEquals(listOf(augExpense), viewModel.content().expenses)

            viewModel.onNextMonth()
            advanceUntilIdle()
            assertEquals(Period.Month(previousMonth), viewModel.content().period)
            assertTrue(viewModel.content().canGoToNextMonth)
        }

    @Test
    fun onPeriodSelected_monthOfCurrentMonth_isNormalizedToCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = expenses())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Month(currentMonth))
            advanceUntilIdle()

            assertEquals(Period.CurrentMonth, viewModel.content().period)
        }

    @Test
    fun onPeriodSelected_rolling_isCoercedToCurrentMonth_andMonthArrowsStayOnMonths() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = expenses())
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Rolling(TimeRange.THREE_MONTHS))
            advanceUntilIdle()
            viewModel.onPreviousMonth()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.Month(previousMonth), content.period)
            assertTrue(content.canGoToNextMonth)
            assertEquals(setOf(sepExpense), content.expenses.toSet())
        }

    @Test
    fun onPeriodSelected_custom_isCoercedToCurrentMonth() =
        runTest(testDispatcher) {
            val viewModel = viewModel(expenseRepository = FakeExpenseRepository(listOf(sepExpense, octExpense)))
            collect(viewModel)

            viewModel.onPeriodSelected(Period.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(setOf(octExpense), content.expenses.toSet())
            assertFalse(content.canGoToNextMonth)
        }

    @Test
    fun selectedPeriod_isStoredInSavedStateHandle_andSurvivesRecreation() =
        runTest(testDispatcher) {
            val handle = SavedStateHandle()
            val original = viewModel(expenseRepository = expenses(), savedStateHandle = handle)
            collect(original)
            original.onPreviousMonth()
            advanceUntilIdle()
            assertEquals("month:2026-09", handle.get<String>("period"))

            val recreated = viewModel(expenseRepository = expenses(), savedStateHandle = handle)
            collect(recreated)

            assertEquals(Period.Month(previousMonth), recreated.content().period)
            assertEquals(listOf(sepExpense), recreated.content().expenses)
        }

    @Test
    fun seededHandle_withGarbageToken_fallsBackToCurrentMonth() =
        runTest(testDispatcher) {
            val handle = SavedStateHandle(mapOf("period" to "month:banana"))
            val viewModel = viewModel(expenseRepository = expenses(), savedStateHandle = handle)
            collect(viewModel)

            assertEquals(Period.CurrentMonth, viewModel.content().period)
        }

    @Test
    fun currentMonth_afterRollover_andRetry_resolvesToNewMonth() =
        runTest(testDispatcher) {
            val mutableClock = MutableClock(now, zone)
            val novExpense = expense(id = "nov", amountCents = 4_000L, date = Instant.parse("2026-11-01T08:00:00Z"))
            val repo = FakeExpenseRepository(listOf(octExpense, novExpense))
            val viewModel = viewModel(expenseRepository = repo, clock = mutableClock)
            collect(viewModel)
            assertEquals(listOf(octExpense), viewModel.content().expenses)
            assertEquals(currentMonth, viewModel.content().budgetMonth)

            mutableClock.now = Instant.parse("2026-11-01T09:00:00Z")
            viewModel.onRetry()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.CurrentMonth, content.period)
            assertEquals(listOf(novExpense), content.expenses)
            assertEquals(YearMonth.of(2026, 11), content.budgetMonth)
            assertTrue(content.budgetMonthIsCurrent)
        }

    @Test
    fun pastMonth_afterRolloverIntoFollowingMonth_becomesNavigableAndNotCurrent() =
        runTest(testDispatcher) {
            val mutableClock = MutableClock(now, zone)
            val viewModel = viewModel(expenseRepository = expenses(), clock = mutableClock)
            collect(viewModel)
            viewModel.onPreviousMonth()
            advanceUntilIdle()

            mutableClock.now = Instant.parse("2026-11-02T00:00:00Z")
            viewModel.onRetry()
            advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(Period.Month(previousMonth), content.period)
            assertTrue(content.canGoToNextMonth)
            assertFalse(content.budgetMonthIsCurrent)

            viewModel.onNextMonth()
            advanceUntilIdle()
            assertEquals(Period.Month(currentMonth), viewModel.content().period)
            assertTrue(viewModel.content().canGoToNextMonth)
        }

    @Test
    fun content_incomeAndRange_matchSelectedPeriod_includingAfterSwitching() =
        runTest(testDispatcher) {
            val octIncome = income(id = "oct-in", amountCents = 9_000L, date = noonOn(YearMonth.of(2026, 10), 12))
            val incomeRepository = FakeIncomeRepository(listOf(sepIncome, octIncome))
            val viewModel = viewModel(expenseRepository = expenses(), incomeRepository = incomeRepository)
            collect(viewModel)

            assertEquals(listOf(octIncome), viewModel.content().income)
            assertEquals(Period.CurrentMonth.toInstantRange(now, zone), viewModel.content().range)

            viewModel.onPreviousMonth()
            advanceUntilIdle()
            assertEquals(listOf(sepIncome), viewModel.content().income)
            assertEquals(Period.Month(previousMonth).toInstantRange(now, zone), viewModel.content().range)

            // Rolling and custom selections coerce to the current month on Home.
            viewModel.onPeriodSelected(Period.Rolling(TimeRange.THREE_MONTHS))
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, viewModel.content().period)
            assertEquals(listOf(octIncome), viewModel.content().income)
            assertEquals(Period.CurrentMonth.toInstantRange(now, zone), viewModel.content().range)

            viewModel.onPeriodSelected(Period.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, viewModel.content().period)
            assertEquals(listOf(octIncome), viewModel.content().income)
        }
}
