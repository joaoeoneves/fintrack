package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deleting income lines and setting/clearing category allocations through [MonthlyPlanViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelWriteTest : MonthlyPlanViewModelTestBase() {
    private val failed = UiText.StringResource(R.string.error_plan_save_failed)

    @Test
    fun deleteIncomeLine_removesLine_andClosesEditor() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine("a")
            advanceUntilIdle()

            vm.onDeleteIncomeLine("a")
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), incomeRepo.lines)
            assertNull(vm.content().editor)
        }

    @Test
    fun deleteIncomeLine_failure_keepsEditorAndEmitsError() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a"))
            val vm = viewModel()
            collect(vm)
            vm.onEditIncomeLine("a")
            advanceUntilIdle()
            incomeRepo.nextDeleteError = IllegalStateException("nope")

            vm.onDeleteIncomeLine("a")
            advanceUntilIdle()

            assertEquals(1, incomeRepo.lines.size)
            assertNotNull(vm.content().editor)
            assertEquals(failed, vm.errorEvent.first())
        }

    @Test
    fun setAllocation_updatesBudgetAndSummary() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            vm.onSetAllocation(BuiltInCategories.SHOPPING, 25_000L)
            advanceUntilIdle()

            assertEquals(25_000L, vm.content().summary.plannedSpendingCents)
        }

    @Test
    fun setAllocation_failure_emitsError() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            budgetRepo.nextSetBudgetError = IllegalStateException("nope")

            vm.onSetAllocation(BuiltInCategories.SHOPPING, 25_000L)
            advanceUntilIdle()

            assertEquals(failed, vm.errorEvent.first())
            assertEquals(0L, vm.content().summary.plannedSpendingCents)
        }

    @Test
    fun clearAllocation_removesBudget() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.FOOD.id, 9_000L)
            val vm = viewModel()
            collect(vm)
            assertEquals(9_000L, vm.content().summary.plannedSpendingCents)

            vm.onClearAllocation(BuiltInCategories.FOOD)
            advanceUntilIdle()

            assertEquals(0L, vm.content().summary.plannedSpendingCents)
        }

    @Test
    fun clearAllocation_failure_emitsError() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.FOOD.id, 9_000L)
            val vm = viewModel()
            collect(vm)
            budgetRepo.nextClearBudgetError = IllegalStateException("nope")

            vm.onClearAllocation(BuiltInCategories.FOOD)
            advanceUntilIdle()

            assertEquals(failed, vm.errorEvent.first())
            assertEquals(9_000L, vm.content().summary.plannedSpendingCents)
        }
}
