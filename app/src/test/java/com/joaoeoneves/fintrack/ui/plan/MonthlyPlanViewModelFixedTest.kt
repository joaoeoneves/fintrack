package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** The "fixed this month" figure of [MonthlyPlanViewModel] (current month's recurring spending). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelFixedTest : MonthlyPlanViewModelTestBase() {
    private val inMonth = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun noExpenses_fixedIsZero() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            assertEquals(0L, vm.content().fixedThisMonthCents)
        }

    @Test
    fun sumsCurrentMonthRecurringSpending_netOfRefunds_ignoringManualSavingAndOtherMonths() =
        runTest(testDispatcher) {
            expenseRepo.addExpense(testExpense("rec_r1_20261001", 50_000L, inMonth))
            expenseRepo.addExpense(testExpense("rec_r2_20261003", 10_000L, inMonth, isRefund = true))
            expenseRepo.addExpense(testExpense("manual", 7_000L, inMonth))
            expenseRepo.addExpense(testExpense("rec_r3_20261001", 99_000L, inMonth, BuiltInCategories.INVESTMENTS))
            expenseRepo.addExpense(testExpense("rec_r1_20250901", 11_000L, Instant.parse("2026-09-30T12:00:00Z")))
            expenseRepo.addExpense(testExpense("rec_r1_20261101", 22_000L, Instant.parse("2026-11-01T12:00:00Z")))
            val vm = viewModel()
            collect(vm)

            assertEquals(40_000L, vm.content().fixedThisMonthCents)
        }

    @Test
    fun monthBoundaries_firstInstantIncluded_firstOfNextMonthExcluded() =
        runTest(testDispatcher) {
            expenseRepo.addExpense(testExpense("rec_a_1", 100L, Instant.parse("2026-10-01T00:00:00Z")))
            expenseRepo.addExpense(testExpense("rec_b_1", 1_000L, Instant.parse("2026-11-01T00:00:00Z")))
            expenseRepo.addExpense(testExpense("rec_c_1", 10_000L, Instant.parse("2026-09-30T23:59:59Z")))
            val vm = viewModel()
            collect(vm)

            assertEquals(100L, vm.content().fixedThisMonthCents)
        }

    @Test
    fun expensesFailure_surfacesErrorState() =
        runTest(testDispatcher) {
            val failing = FakeExpenseRepository(nextObserveExpensesError = IllegalStateException("boom"))
            val vm = viewModel(expenses = failing)
            collect(vm)

            assertTrue(vm.uiState.value is MonthlyPlanUiState.Error)
        }
}
