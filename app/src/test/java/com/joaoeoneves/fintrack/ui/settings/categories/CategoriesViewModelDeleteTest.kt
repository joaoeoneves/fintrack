package com.joaoeoneves.fintrack.ui.settings.categories

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CategoriesViewModelDeleteTest : CategoriesViewModelTestBase() {
    private val gym = userCategory("custom-1", "Gym", order = 10)

    private fun repo() = FakeCategoryRepository(listOf(gym))

    @Test
    fun unused_isDeletedAtOnce_andEmittedForUndo() =
        runTest(testDispatcher) {
            val repo = repo()
            val viewModel = start(repo)
            val deleted = mutableListOf<Category>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.deletedEvent.collect { deleted += it }
            }

            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()

            assertTrue(repo.docs.isEmpty())
            assertEquals(listOf(gym), deleted)
            assertNull(viewModel.content().deleteDialog)
        }

    @Test
    fun undoDelete_restoresTheCategory() =
        runTest(testDispatcher) {
            val repo = repo()
            val viewModel = start(repo)
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertTrue(repo.docs.isEmpty())

            viewModel.onUndoDelete(gym)
            advanceUntilIdle()

            assertEquals(listOf(gym), repo.docs)
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
    fun deleteFailure_emitsError_andNoDeletedEvent() =
        runTest(testDispatcher) {
            val repo = repo()
            val viewModel = start(repo)
            val deleted = mutableListOf<Category>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.deletedEvent.collect { deleted += it }
            }
            repo.nextWriteError = RuntimeException("boom")

            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()

            assertEquals(listOf<UiText>(saveFailed), errors)
            assertEquals(listOf(gym), repo.docs)
            assertTrue(deleted.isEmpty())
        }

    @Test
    fun budgetForOtherCategory_doesNotBlockDelete() =
        runTest(testDispatcher) {
            budgets.setBudget(BuiltInCategories.FOOD.id, 500L)
            val repo = repo()
            val viewModel = start(repo)
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertNull(viewModel.content().deleteDialog)
            assertTrue(repo.docs.isEmpty())
        }
}
