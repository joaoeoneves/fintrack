package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DashboardViewModel.onClearBudget] and the `budgetErrorEvent` one-shot error channel emitted by
 * [DashboardViewModel.onSetBudget]/[DashboardViewModel.onClearBudget] on failure. Split out of
 * [DashboardViewModelBudgetTest] to keep that class bounded; see [DashboardViewModelTestBase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DashboardViewModelBudgetEditTest : DashboardViewModelTestBase() {
    private fun DashboardViewModel.budgetFor(category: ExpenseCategory) =
        (uiState.value as DashboardUiState.Content).budgets.single { it.category == category }

    /** Starts collecting uiState and budgetErrorEvent; returns the live list of emitted errors. */
    private fun TestScope.collectAll(viewModel: DashboardViewModel): MutableList<UiText> {
        val errors = mutableListOf<UiText>()
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.budgetErrorEvent.collect { errors.add(it) }
        }
        return errors
    }

    // ---- onClearBudget ----

    @Test
    fun onClearBudget_dropsLimit_onlyForThatCategory() =
        runTest(testDispatcher) {
            val budgetRepo =
                FakeBudgetRepository(
                    listOf(Budget(ExpenseCategory.SHOPPING, 10_000L), Budget(ExpenseCategory.TRANSFER, 4_000L)),
                )
            val viewModel = viewModel(budgetRepository = budgetRepo)
            val errors = collectAll(viewModel)
            advanceUntilIdle()
            assertEquals(10_000L, viewModel.budgetFor(ExpenseCategory.SHOPPING).limitCents)

            viewModel.onClearBudget(ExpenseCategory.SHOPPING)
            advanceUntilIdle()

            assertNull(viewModel.budgetFor(ExpenseCategory.SHOPPING).limitCents)
            assertEquals(4_000L, viewModel.budgetFor(ExpenseCategory.TRANSFER).limitCents)
            assertEquals(listOf(Budget(ExpenseCategory.TRANSFER, 4_000L)), budgetRepo.observeBudgets().first())
            assertTrue("success must not emit an error event", errors.isEmpty())
        }

    @Test
    fun onClearBudget_categoryWithoutLimit_isNoOp_emitsNothing() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            val errors = collectAll(viewModel)
            advanceUntilIdle()

            viewModel.onClearBudget(ExpenseCategory.SHOPPING)
            advanceUntilIdle()

            assertNull(viewModel.budgetFor(ExpenseCategory.SHOPPING).limitCents)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onClearBudget_failure_emitsExactlyOneFallbackError_andKeepsLimit() =
        runTest(testDispatcher) {
            val budgetRepo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))
            budgetRepo.nextClearBudgetError = IllegalStateException("raw firestore detail")
            val viewModel = viewModel(budgetRepository = budgetRepo)
            val errors = collectAll(viewModel)
            advanceUntilIdle()

            viewModel.onClearBudget(ExpenseCategory.SHOPPING)
            advanceUntilIdle()

            assertEquals(listOf(UiText.StringResource(R.string.error_budget_remove_failed)), errors)
            assertEquals(10_000L, viewModel.budgetFor(ExpenseCategory.SHOPPING).limitCents)
        }

    // ---- onSetBudget: error event ----

    @Test
    fun onSetBudget_success_emitsNoErrorEvent() =
        runTest(testDispatcher) {
            val viewModel = viewModel()
            val errors = collectAll(viewModel)
            advanceUntilIdle()

            viewModel.onSetBudget(ExpenseCategory.SHOPPING, 10_000L)
            advanceUntilIdle()

            assertEquals(10_000L, viewModel.budgetFor(ExpenseCategory.SHOPPING).limitCents)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onSetBudget_failure_emitsExactlyOneFallbackError_andLeavesLimitUnchanged() =
        runTest(testDispatcher) {
            val budgetRepo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 5_000L)))
            budgetRepo.nextSetBudgetError = IllegalStateException("raw firestore detail")
            val viewModel = viewModel(budgetRepository = budgetRepo)
            val errors = collectAll(viewModel)
            advanceUntilIdle()

            viewModel.onSetBudget(ExpenseCategory.SHOPPING, 7_500L)
            advanceUntilIdle()

            assertEquals(listOf(UiText.StringResource(R.string.error_budget_save_failed)), errors)
            assertEquals(5_000L, viewModel.budgetFor(ExpenseCategory.SHOPPING).limitCents)
        }

    @Test
    fun onSetBudget_failureThenRetry_secondAttemptSucceeds_withoutAnotherErrorEvent() =
        runTest(testDispatcher) {
            val budgetRepo = FakeBudgetRepository(nextSetBudgetError = IllegalStateException("transient"))
            val viewModel = viewModel(budgetRepository = budgetRepo)
            val errors = collectAll(viewModel)
            advanceUntilIdle()

            viewModel.onSetBudget(ExpenseCategory.SHOPPING, 7_500L)
            advanceUntilIdle()
            viewModel.onSetBudget(ExpenseCategory.SHOPPING, 7_500L)
            advanceUntilIdle()

            assertEquals(1, errors.size)
            assertEquals(7_500L, viewModel.budgetFor(ExpenseCategory.SHOPPING).limitCents)
        }
}
