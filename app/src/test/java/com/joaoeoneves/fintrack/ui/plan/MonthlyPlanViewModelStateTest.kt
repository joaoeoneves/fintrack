package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.MAX_PLANNED_INCOME_LINES
import com.joaoeoneves.fintrack.testutil.genericErrorText
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

/** State derivation, loading/error/retry and the line cap of [MonthlyPlanViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MonthlyPlanViewModelStateTest : MonthlyPlanViewModelTestBase() {
    @Test
    fun initialState_isLoading() =
        runTest(testDispatcher) {
            assertEquals(MonthlyPlanUiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun empty_contentHasNoLinesAndUnconfiguredSummary() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertTrue(content.incomeLines.isEmpty())
            assertFalse(content.summary.isConfigured)
            assertTrue(content.canAddIncomeLine)
            assertEquals(null, content.editor)
        }

    @Test
    fun content_summaryReflectsIncomeLinesAndAllocations() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", amountCents = 100_000L))
            incomeRepo.savePlannedIncome(line("b", amountCents = 20_000L, order = 1))
            budgetRepo.setBudget(BuiltInCategories.SHOPPING.id, 30_000L)
            budgetRepo.setBudget(BuiltInCategories.INVESTMENTS.id, 10_000L)
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals(listOf("a", "b"), content.incomeLines.map { it.id })
            assertEquals(120_000L, content.summary.expectedIncomeCents)
            assertEquals(30_000L, content.summary.plannedSpendingCents)
            assertEquals(10_000L, content.summary.plannedSavingCents)
            assertEquals(80_000L, content.summary.plannedLeftoverCents)
            assertEquals(0L, content.summary.actualIncomeCents)
        }

    @Test
    fun observeFailure_emitsErrorState() =
        runTest(testDispatcher) {
            incomeRepo.nextObserveError = IllegalStateException("boom")
            val vm = viewModel()
            collect(vm)

            assertEquals(
                MonthlyPlanUiState.Error(genericErrorText),
                vm.uiState.value,
            )
        }

    @Test
    fun retry_afterError_recoversToContent() =
        runTest(testDispatcher) {
            incomeRepo.nextObserveError = IllegalStateException("boom")
            val vm = viewModel()
            collect(vm)
            assertTrue(vm.uiState.value is MonthlyPlanUiState.Error)

            incomeRepo.nextObserveError = null
            vm.onRetry()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is MonthlyPlanUiState.Content)
        }

    @Test
    fun budgetObserveFailure_emitsErrorState() =
        runTest(testDispatcher) {
            budgetRepo.nextObserveBudgetsError = IllegalStateException("boom")
            val vm = viewModel()
            collect(vm)

            assertTrue(vm.uiState.value is MonthlyPlanUiState.Error)
        }

    @Test
    fun canAddIncomeLine_falseAtTwentyLines() =
        runTest(testDispatcher) {
            repeat(MAX_PLANNED_INCOME_LINES - 1) { incomeRepo.savePlannedIncome(line("l$it", order = it)) }
            val vm = viewModel()
            collect(vm)
            assertTrue(vm.content().canAddIncomeLine)

            incomeRepo.savePlannedIncome(line("last", order = MAX_PLANNED_INCOME_LINES))
            advanceUntilIdle()

            assertFalse(vm.content().canAddIncomeLine)
        }

    @Test
    fun allocationsAndIncomeChanges_reEmit() =
        runTest(testDispatcher) {
            val vm = viewModel()
            collect(vm)

            budgetRepo.setBudget(BuiltInCategories.FOOD.id, 5_000L)
            incomeRepo.savePlannedIncome(line("a"))
            advanceUntilIdle()

            assertEquals(5_000L, vm.content().summary.plannedSpendingCents)
            assertEquals(1, vm.content().incomeLines.size)
        }
}
