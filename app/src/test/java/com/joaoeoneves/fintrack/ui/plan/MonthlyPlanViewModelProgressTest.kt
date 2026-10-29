package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month navigation of
 * [MonthlyPlanViewModel]. "Now" is 2026-10-15, so September 2026 is the previous month.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelProgressTest : MonthlyPlanViewModelTestBase() {
    // ---- month navigation ----

    @Test
    fun defaultPeriod_isCurrentMonth_withNextMonthAllowed() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(Period.CurrentMonth, vm.content().period)
            assertTrue(vm.content().canGoToNextMonth)
        }

    @Test
    fun previousThenNext_walksBackAndForth_neverPastNextMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.periodActions.onPrevious()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 9)), vm.content().period)
            assertTrue(vm.content().canGoToNextMonth)

            vm.periodActions.onPrevious()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 8)), vm.content().period)

            vm.periodActions.onNext()
            vm.periodActions.onNext()
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.content().period)
            assertTrue(vm.content().canGoToNextMonth)

            vm.periodActions.onNext()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 11)), vm.content().period)
            assertFalse(vm.content().canGoToNextMonth)

            vm.periodActions.onNext()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 11)), vm.content().period)
        }

    @Test
    fun yearBoundary_previousFromJanuary_goesToDecember() =
        runTest(testDispatcher) {
            clock.now = Instant.parse("2026-01-10T12:00:00Z")
            val vm = viewModel()
            collect(vm)

            vm.periodActions.onPrevious()
            advanceUntilIdle()

            assertEquals(Period.Month(YearMonth.of(2025, 12)), vm.content().period)
        }

    @Test
    fun onSelect_rollingAndCustom_fallBackToCurrentMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            vm.periodActions.onPrevious()
            advanceUntilIdle()

            vm.periodActions.onSelect(Period.Rolling(TimeRange.SIX_MONTHS))
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.content().period)

            vm.periodActions.onPrevious()
            advanceUntilIdle()
            vm.periodActions.onSelect(Period.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.content().period)
        }

    @Test
    fun onSelect_specificPastMonth_isKept_andCurrentMonthNormalized() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 2)))
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 2)), vm.content().period)

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 10)))
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.content().period)
        }

    @Test
    fun selectedMonth_survivesRecreationWithSameSavedStateHandle() =
        runTest(testDispatcher) {
            val handle = SavedStateHandle()
            val original = viewModel(handle)
            collect(original)
            original.periodActions.onPrevious()
            advanceUntilIdle()

            val recreated = viewModel(handle)
            collect(recreated)

            assertEquals(Period.Month(YearMonth.of(2026, 9)), recreated.content().period)
        }

    @Test
    fun monthNavigation_doesNotChangeThePlanItself() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("salary", 120_000L))
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 24_000L)
            val vm = viewModel()
            collect(vm)
            val before = vm.content().summary

            vm.periodActions.onPrevious()
            advanceUntilIdle()

            assertEquals(before, vm.content().summary)
            assertEquals(listOf("salary"), vm.content().incomeLines.map { it.id })
        }

    @Test
    fun calendarRollover_retryReResolvesCurrentMonth() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            assertTrue(vm.content().canGoToNextMonth)

            clock.now = Instant.parse("2026-11-02T12:00:00Z")
            vm.onRetry()
            advanceUntilIdle()

            // Still the (re-resolved) current month, and the next month stays reachable.
            assertEquals(Period.CurrentMonth, vm.content().period)
            assertTrue(vm.content().canGoToNextMonth)
            vm.periodActions.onPrevious()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 10)), vm.content().period)
        }
}
