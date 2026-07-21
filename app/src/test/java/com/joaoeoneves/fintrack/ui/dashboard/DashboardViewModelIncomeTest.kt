package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.TimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.temporal.ChronoUnit

/**
 * Income/net-balance tests for [DashboardViewModel], plus observeIncome's error-state handling
 * (mirrors observeExpenses' error-state handling). See [DashboardViewModelTestBase] for shared
 * fixtures/setup, and [DashboardViewModelExpenseTest]/[DashboardViewModelBudgetTest] for the rest of
 * the coverage that used to live in one large class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelIncomeTest : DashboardViewModelTestBase() {
    // ---- income / net balance: scoped to the currently-selected TimeRange (not the calendar-month budget range) ----

    @Test
    fun incomeCents_sumsIncomeWithinSelectedTimeRange_excludesIncomeOutsideIt() =
        runTest(testDispatcher) {
            val insideOneMonth = income(id = "inside", amountCents = 250_000L, date = now.minus(3, ChronoUnit.DAYS))
            val outsideOneMonth = income(id = "outside", amountCents = 999_999L, date = now.minus(40, ChronoUnit.DAYS))
            val expenseRepo = FakeExpenseRepository()
            val incomeRepo = FakeIncomeRepository(listOf(insideOneMonth, outsideOneMonth))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_MONTH, content.timeRange)
            assertEquals(250_000L, content.incomeCents)
            assertEquals(listOf(insideOneMonth), content.recentIncome)

            job.cancel()
        }

    @Test
    fun incomeCents_emptyIncomeRepository_isZero() =
        runTest(testDispatcher) {
            val expenseRepo = FakeExpenseRepository()
            val incomeRepo = FakeIncomeRepository()
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(0L, content.incomeCents)
            assertTrue(content.recentIncome.isEmpty())

            job.cancel()
        }

    @Test
    fun netCents_equalsIncomeMinusExpenses_forSelectedTimeRange() =
        runTest(testDispatcher) {
            val expenseThisMonth =
                expense(
                    id = "e1",
                    amountCents = 12_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(2, ChronoUnit.DAYS),
                )
            val incomeThisMonth = income(id = "i1", amountCents = 300_000L, date = now.minus(1, ChronoUnit.DAYS))
            val expenseRepo = FakeExpenseRepository(listOf(expenseThisMonth))
            val incomeRepo = FakeIncomeRepository(listOf(incomeThisMonth))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(300_000L, content.incomeCents)
            assertEquals(12_000L, content.totalCents)
            assertEquals(288_000L, content.netCents)

            job.cancel()
        }

    @Test
    fun netCents_negativeWhenExpensesExceedIncome() =
        runTest(testDispatcher) {
            val bigExpense = expense(id = "e1", amountCents = 50_000L, category = ExpenseCategory.SHOPPING, date = now)
            val smallIncome = income(id = "i1", amountCents = 10_000L, date = now)
            val expenseRepo = FakeExpenseRepository(listOf(bigExpense))
            val incomeRepo = FakeIncomeRepository(listOf(smallIncome))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(-40_000L, content.netCents)

            job.cancel()
        }

    @Test
    fun netCents_zeroWhenNoIncomeAndNoExpenses() =
        runTest(testDispatcher) {
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(0L, content.incomeCents)
            assertEquals(0L, content.totalCents)
            assertEquals(0L, content.netCents)

            job.cancel()
        }

    @Test
    fun recentIncome_returnsTop5ByDateDescending_whenMoreThan5InRange() =
        runTest(testDispatcher) {
            // 7 income entries inside the range, at distinct days, in scrambled insertion order.
            val i1 = income(id = "i1", amountCents = 100L, date = now.minus(1, ChronoUnit.DAYS))
            val i2 = income(id = "i2", amountCents = 100L, date = now.minus(2, ChronoUnit.DAYS))
            val i3 = income(id = "i3", amountCents = 100L, date = now.minus(3, ChronoUnit.DAYS))
            val i4 = income(id = "i4", amountCents = 100L, date = now.minus(4, ChronoUnit.DAYS))
            val i5 = income(id = "i5", amountCents = 100L, date = now.minus(5, ChronoUnit.DAYS))
            val i6 = income(id = "i6", amountCents = 100L, date = now.minus(6, ChronoUnit.DAYS))
            val i7 = income(id = "i7", amountCents = 100L, date = now.minus(7, ChronoUnit.DAYS))
            val incomeRepo = FakeIncomeRepository(listOf(i4, i1, i7, i2, i6, i3, i5))
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(listOf(i1, i2, i3, i4, i5), content.recentIncome)
            assertEquals(700L, content.incomeCents)

            job.cancel()
        }

    @Test
    fun income_inDifferentTimeRangeBucketThanExpenses_isFilteredIndependently() =
        runTest(testDispatcher) {
            // Income lands within the current month but *outside* the current week; the expense is
            // the reverse (inside the week, and therefore also inside the month). Selecting
            // ONE_WEEK should include the expense but exclude the income; selecting ONE_MONTH
            // should include both.
            val incomeOutsideWeekInsideMonth =
                income(id = "income-month-only", amountCents = 400_000L, date = now.minus(20, ChronoUnit.DAYS))
            val expenseInsideWeek =
                expense(
                    id = "expense-week",
                    amountCents = 5_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(1, ChronoUnit.DAYS),
                )
            val expenseRepo = FakeExpenseRepository(listOf(expenseInsideWeek))
            val incomeRepo = FakeIncomeRepository(listOf(incomeOutsideWeekInsideMonth))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val monthContent = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_MONTH, monthContent.timeRange)
            assertEquals(400_000L, monthContent.incomeCents)
            assertEquals(5_000L, monthContent.totalCents)
            assertEquals(395_000L, monthContent.netCents)

            viewModel.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val weekContent = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_WEEK, weekContent.timeRange)
            // Income from 20 days ago is excluded from the one-week bucket...
            assertEquals(0L, weekContent.incomeCents)
            assertTrue(weekContent.recentIncome.isEmpty())
            // ...while the expense (1 day ago) is still included.
            assertEquals(5_000L, weekContent.totalCents)
            assertEquals(-5_000L, weekContent.netCents)

            job.cancel()
        }

    // ---- observeIncome failure -> Error state (mirrors observeExpenses failure handling) ----

    @Test
    fun observeIncomeFailure_beforeFirstCollection_surfacesErrorState_withUnderlyingMessage() =
        runTest(testDispatcher) {
            val incomeRepo = FakeIncomeRepository()
            incomeRepo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)

            job.cancel()
        }

    @Test
    fun observeIncomeFailure_withNullExceptionMessage_fallsBackToDefaultMessage() =
        runTest(testDispatcher) {
            val incomeRepo = FakeIncomeRepository()
            incomeRepo.nextObserveIncomeError = RuntimeException()
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(DashboardUiState.Error("Something went wrong"), viewModel.uiState.value)

            job.cancel()
        }

    @Test
    fun onRetry_afterClearingObserveIncomeError_transitionsFromErrorBackToContent() =
        runTest(testDispatcher) {
            val existingIncome = income(id = "i1", amountCents = 1_000L, date = now.minus(1, ChronoUnit.DAYS))
            val incomeRepo = FakeIncomeRepository(listOf(existingIncome))
            incomeRepo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)

            incomeRepo.nextObserveIncomeError = null
            viewModel.onRetry()
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(1_000L, content.incomeCents)

            job.cancel()
        }
}
