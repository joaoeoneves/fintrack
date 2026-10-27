package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * The per-month "planned vs actual" [MonthlyPlanUiState.Content.progress] and the month navigation of
 * [MonthlyPlanViewModel]. "Now" is 2026-10-15, so September 2026 is the previous month.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelProgressTest : MonthlyPlanViewModelTestBase() {
    private val october = Instant.parse("2026-10-05T12:00:00Z")
    private val september = Instant.parse("2026-09-15T12:00:00Z")

    private fun MonthlyPlanUiState.Content.progressOf(category: Category) = progress.single { it.category == category }

    private suspend fun spend(
        id: String,
        cents: Long,
        at: Instant,
        category: Category = BuiltInCategories.SHOPPING,
        isRefund: Boolean = false,
    ) = expenseRepo.addExpense(testExpense(id, cents, at, category, isRefund))

    // ---- progress: presence/defaults ----

    @Test
    fun progress_noData_allCategoriesPresentWithNullLimitAndZeroSpent() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals(BuiltInCategories.all.filterNot { it.archived }, content.progress.map { it.category })
            assertTrue(content.progress.all { it.limitCents == null && it.spentCents == 0L && !it.isOverBudget })
        }

    @Test
    fun progress_reflectsPreSeededLimits_forCategoriesWithNoSpend() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L)
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals(10_000L, content.progressOf(BuiltInCategories.SHOPPING).limitCents)
            assertEquals(0L, content.progressOf(BuiltInCategories.SHOPPING).spentCents)
            val others = content.progress.filter { it.category != BuiltInCategories.SHOPPING }
            assertTrue(others.all { it.limitCents == null })
        }

    @Test
    fun progress_listOnlyCategoriesWithLimit_matchesAllocationsWithLimit() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.FOOD.id, 3_000L)
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            val planned = content.progress.filter { it.limitCents != null }
            assertEquals(listOf(BuiltInCategories.FOOD), planned.map { it.category })
            val allocated = content.allocations.filter { it.limitCents != null }
            assertEquals(planned.map { it.category }, allocated.map { it.category })
        }

    // ---- progress: which month is measured ----

    @Test
    fun currentMonth_progressCountsOnlyCurrentMonth() =
        runTest(testDispatcher) {
            spend("sep", 9_000L, september)
            spend("oct", 1_500L, october)
            val vm = viewModel()
            collect(vm)

            assertEquals(1_500L, vm.content().progressOf(BuiltInCategories.SHOPPING).spentCents)
        }

    @Test
    fun currentMonth_boundariesIncluded() =
        runTest(testDispatcher) {
            spend("first", 100L, Instant.parse("2026-10-01T00:00:00Z"))
            spend("last", 200L, Instant.parse("2026-10-31T23:59:59.999Z"))
            spend("sepEnd", 5_000L, Instant.parse("2026-09-30T23:59:59.999Z"))
            spend("novStart", 7_000L, Instant.parse("2026-11-01T00:00:00Z"))
            val vm = viewModel()
            collect(vm)

            assertEquals(300L, vm.content().progressOf(BuiltInCategories.SHOPPING).spentCents)
        }

    @Test
    fun pastMonth_progressFollowsThatMonth_withSameLimits() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 5_000L)
            spend("sep", 9_000L, september)
            spend("oct", 1_500L, october)
            val vm = viewModel()
            collect(vm)

            vm.periodActions.onPrevious()
            advanceUntilIdle()

            val shopping = vm.content().progressOf(BuiltInCategories.SHOPPING)
            assertEquals(9_000L, shopping.spentCents)
            assertEquals(5_000L, shopping.limitCents)
            assertTrue(shopping.isOverBudget)
        }

    @Test
    fun monthWithNoSpend_progressShowsZeroSpent() =
        runTest(testDispatcher) {
            spend("oct", 1_500L, october)
            val vm = viewModel()
            collect(vm)

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 3)))
            advanceUntilIdle()

            assertTrue(vm.content().progress.all { it.spentCents == 0L })
        }

    @Test
    fun fixedSpending_followsSelectedMonth() =
        runTest(testDispatcher) {
            spend("rec_a_20261001", 100L, october)
            spend("rec_a_20260901", 400L, september)
            val vm = viewModel()
            collect(vm)
            assertEquals(100L, vm.content().fixedThisMonthCents)

            vm.periodActions.onPrevious()
            advanceUntilIdle()

            assertEquals(400L, vm.content().fixedThisMonthCents)
        }

    // ---- progress: isOverBudget ----

    @Test
    fun isOverBudget_falseWithoutLimit_regardlessOfSpend() =
        runTest(testDispatcher) {
            spend("big", 1_000_000L, october)
            val vm = viewModel()
            collect(vm)

            val shopping = vm.content().progressOf(BuiltInCategories.SHOPPING)
            assertEquals(1_000_000L, shopping.spentCents)
            assertNull(shopping.limitCents)
            assertFalse(shopping.isOverBudget)
        }

    @Test
    fun isOverBudget_falseAtExactLimit_trueOneCentOver() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L)
            spend("at", 10_000L, october)
            val vm = viewModel()
            collect(vm)
            assertFalse(vm.content().progressOf(BuiltInCategories.SHOPPING).isOverBudget)

            spend("cent", 1L, october)
            advanceUntilIdle()

            assertTrue(vm.content().progressOf(BuiltInCategories.SHOPPING).isOverBudget)
        }

    @Test
    fun isOverBudget_zeroLimit_anyPositiveSpendIsOver_zeroSpendIsNot() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.TRANSFER.id, 0L)
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 0L)
            spend("t", 1L, october, BuiltInCategories.TRANSFER)
            val vm = viewModel()
            collect(vm)

            assertTrue(vm.content().progressOf(BuiltInCategories.TRANSFER).isOverBudget)
            assertFalse(vm.content().progressOf(BuiltInCategories.SHOPPING).isOverBudget)
        }

    @Test
    fun progress_includesInvestmentsAndFood_likeAnyOtherCategory() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.FOOD.id, 3_499L)
            spend("inv", 2_000L, october, BuiltInCategories.INVESTMENTS)
            spend("f1", 2_500L, october, BuiltInCategories.FOOD)
            spend("f2", 1_000L, october, BuiltInCategories.FOOD)
            spend("sepFood", 50_000L, september, BuiltInCategories.FOOD)
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals(2_000L, content.progressOf(BuiltInCategories.INVESTMENTS).spentCents)
            val food = content.progressOf(BuiltInCategories.FOOD)
            assertEquals(3_500L, food.spentCents)
            assertTrue(food.isOverBudget)
        }

    @Test
    fun progress_refundsReduceSpend_andFloorAtZero() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L)
            budgetRepo.setBudget(BuiltInCategories.TRANSFER.id, 10_000L)
            spend("a", 8_000L, october)
            spend("b", 4_000L, october)
            spend("ret", 3_000L, october, isRefund = true)
            spend("t", 1_000L, october, BuiltInCategories.TRANSFER)
            spend("big", 5_000L, october, BuiltInCategories.TRANSFER, isRefund = true)
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals(9_000L, content.progressOf(BuiltInCategories.SHOPPING).spentCents)
            assertFalse(content.progressOf(BuiltInCategories.SHOPPING).isOverBudget)
            assertEquals(0L, content.progressOf(BuiltInCategories.TRANSFER).spentCents)
        }

    @Test
    fun progress_reflectsExpenseAddedAfterCollection() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            spend("late", 2_500L, october)
            advanceUntilIdle()

            assertEquals(2_500L, vm.content().progressOf(BuiltInCategories.SHOPPING).spentCents)
        }

    // ---- month navigation ----

    @Test
    fun defaultPeriod_isCurrentMonth_withoutNext() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(Period.CurrentMonth, vm.content().period)
            assertFalse(vm.content().canGoToNextMonth)
        }

    @Test
    fun previousThenNext_walksBackAndForth_neverPastCurrentMonth() =
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
            assertFalse(vm.content().canGoToNextMonth)

            vm.periodActions.onNext()
            advanceUntilIdle()
            assertEquals(Period.CurrentMonth, vm.content().period)
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
            assertFalse(vm.content().canGoToNextMonth)

            clock.now = Instant.parse("2026-11-02T12:00:00Z")
            vm.onRetry()
            advanceUntilIdle()

            // Still the (re-resolved) current month, so it still cannot go forward.
            assertEquals(Period.CurrentMonth, vm.content().period)
            assertFalse(vm.content().canGoToNextMonth)
            vm.periodActions.onPrevious()
            advanceUntilIdle()
            assertEquals(Period.Month(YearMonth.of(2026, 10)), vm.content().period)
        }
}
