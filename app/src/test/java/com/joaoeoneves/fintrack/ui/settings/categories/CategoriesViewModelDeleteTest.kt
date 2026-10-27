package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelDeleteTest : CategoriesViewModelTestBase() {
    private val gym = userCategory("custom-1", "Gym", order = 10)

    private fun repo() = FakeCategoryRepository(listOf(gym))

    @Test
    fun unused_goesToConfirmDelete_thenConfirmRemoves() =
        runTest(testDispatcher) {
            val repo = repo()
            val viewModel = start(repo)

            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertEquals(DeleteDialogState.ConfirmDelete(gym), viewModel.content().deleteDialog)

            viewModel.onConfirmDelete()
            advanceUntilIdle()
            assertTrue(repo.docs.isEmpty())
            assertNull(viewModel.content().deleteDialog)
        }

    @Test
    fun usedByExpense_isInUseWithExpenses() =
        runTest(testDispatcher) {
            val viewModel = start(repo())
            expenses.addExpense(testExpense("e1", 500L, Instant.parse("2025-01-01T00:00:00Z"), gym))
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertEquals(
                DeleteDialogState.InUse(gym, CategoryUsage(hasExpenses = true, hasBudget = false)),
                viewModel.content().deleteDialog,
            )
        }

    @Test
    fun usedByBudget_isInUseWithBudget() =
        runTest(testDispatcher) {
            budgets.setBudget(gym.id, 1_000L)
            val viewModel = start(repo())
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertEquals(
                DeleteDialogState.InUse(gym, CategoryUsage(hasExpenses = false, hasBudget = true)),
                viewModel.content().deleteDialog,
            )
        }

    @Test
    fun usageCheckFailure_isCannotVerify() =
        runTest(testDispatcher) {
            expenses.nextHasExpenseWithCategoryError = RuntimeException("offline")
            val viewModel = start(repo())
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertEquals(DeleteDialogState.CannotVerify(gym), viewModel.content().deleteDialog)
        }

    @Test
    fun builtIn_isIgnored() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository())
            viewModel.onDeleteRequested(BuiltInCategories.FOOD.id)
            advanceUntilIdle()
            assertNull(viewModel.content().deleteDialog)
        }

    @Test
    fun dismissWhileChecking_lateResultDoesNotReopen() =
        runTest(testDispatcher) {
            val viewModel = start(repo())
            viewModel.onDeleteRequested(gym.id)
            viewModel.onDismissDelete()
            advanceUntilIdle()
            assertNull(viewModel.content().deleteDialog)
        }

    @Test
    fun confirmWithoutConfirmState_doesNothing() =
        runTest(testDispatcher) {
            val repo = repo()
            val viewModel = start(repo)
            viewModel.onConfirmDelete()
            viewModel.onDeleteRequested(gym.id)
            viewModel.onConfirmDelete() // still Checking
            advanceUntilIdle()
            assertEquals(listOf(gym), repo.docs)
        }

    @Test
    fun deleteFailure_emitsError() =
        runTest(testDispatcher) {
            val repo = repo()
            val viewModel = start(repo)
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            repo.nextWriteError = RuntimeException("boom")
            viewModel.onConfirmDelete()
            advanceUntilIdle()
            assertEquals(listOf<UiText>(saveFailed), errors)
            assertEquals(listOf(gym), repo.docs)
        }

    @Test
    fun budgetForOtherCategory_doesNotBlockDelete() =
        runTest(testDispatcher) {
            budgets.setBudget(BuiltInCategories.FOOD.id, 500L)
            val viewModel = start(repo())
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertEquals(DeleteDialogState.ConfirmDelete(gym), viewModel.content().deleteDialog)
        }
}
