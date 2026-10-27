package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

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

    @Test
    fun clearAllocation_dropsOnlyThatCategory_andProgressLimitFollows() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 10_000L)
            budgetRepo.setBudget(BuiltInCategories.TRANSFER.id, 4_000L)
            val vm = viewModel()
            collect(vm)

            vm.onClearAllocation(BuiltInCategories.SHOPPING)
            advanceUntilIdle()

            assertEquals(listOf(Budget(BuiltInCategories.TRANSFER.id, 4_000L)), budgetRepo.observeBudgets().first())
            val limits = vm.content().progress.associate { it.category to it.limitCents }
            assertNull(limits.getValue(BuiltInCategories.SHOPPING))
            assertEquals(4_000L, limits.getValue(BuiltInCategories.TRANSFER))
        }

    @Test
    fun clearAllocation_categoryWithoutLimit_isNoOp_emitsNoError() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)
            val errors = mutableListOf<UiText>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { errors.add(it) } }

            vm.onClearAllocation(BuiltInCategories.SHOPPING)
            advanceUntilIdle()

            assertNull(
                vm
                    .content()
                    .progress
                    .single { it.category == BuiltInCategories.SHOPPING }
                    .limitCents,
            )
            assertEquals(emptyList<UiText>(), errors)
        }

    @Test
    fun setAllocation_updatingExistingLimit_replacesRatherThanDuplicates() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 5_000L)
            val vm = viewModel()
            collect(vm)

            vm.onSetAllocation(BuiltInCategories.SHOPPING, 7_500L)
            advanceUntilIdle()

            assertEquals(1, budgetRepo.observeBudgets().first().size)
            val content = vm.content()
            assertEquals(BuiltInCategories.all.filterNot { it.archived }.size, content.progress.size)
            assertEquals(7_500L, content.progress.single { it.category == BuiltInCategories.SHOPPING }.limitCents)
        }

    @Test
    fun setAllocation_whileViewingPastMonth_limitAppliesToThatMonthsSpend() =
        runTest(testDispatcher) {
            expenseRepo.addExpense(testExpense("sep", 9_000L, Instant.parse("2026-09-15T12:00:00Z")))
            val vm = viewModel()
            collect(vm)
            vm.periodActions.onPrevious()
            advanceUntilIdle()

            vm.onSetAllocation(BuiltInCategories.SHOPPING, 8_000L)
            advanceUntilIdle()

            val shopping = vm.content().progress.single { it.category == BuiltInCategories.SHOPPING }
            assertEquals(8_000L, shopping.limitCents)
            assertEquals(9_000L, shopping.spentCents)
            assertTrue(shopping.isOverBudget)
        }

    @Test
    fun setAllocation_failureLeavesLimitUnchanged_thenRetrySucceeds_withoutAnotherError() =
        runTest(testDispatcher) {
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 5_000L)
            val vm = viewModel()
            collect(vm)
            val errors = mutableListOf<UiText>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { errors.add(it) } }
            budgetRepo.nextSetBudgetError = IllegalStateException("transient")

            vm.onSetAllocation(BuiltInCategories.SHOPPING, 7_500L)
            advanceUntilIdle()
            assertEquals(
                5_000L,
                vm
                    .content()
                    .progress
                    .single { it.category == BuiltInCategories.SHOPPING }
                    .limitCents,
            )

            vm.onSetAllocation(BuiltInCategories.SHOPPING, 7_500L)
            advanceUntilIdle()

            assertEquals(listOf(failed), errors)
            assertEquals(
                7_500L,
                vm
                    .content()
                    .progress
                    .single { it.category == BuiltInCategories.SHOPPING }
                    .limitCents,
            )
        }
}
