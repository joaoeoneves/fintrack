package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.mergeCategories
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelArchiveReorderTest : CategoriesViewModelTestBase() {
    private val gym = userCategory("custom-1", "Gym", order = 10)
    private val old = userCategory("custom-2", "Old", order = 11, archived = true)

    @Test
    fun onToggleArchived_archivesThenRestores() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(listOf(gym))
            val viewModel = start(repo)

            viewModel.onToggleArchived(gym.id)
            advanceUntilIdle()
            assertEquals(listOf(BuiltInCategories.RECURRING.id, gym.id), viewModel.content().archived.map { it.id })
            assertTrue(viewModel.content().active.none { it.id == gym.id })

            viewModel.onToggleArchived(gym.id)
            advanceUntilIdle()
            assertEquals(listOf(BuiltInCategories.RECURRING.id), viewModel.content().archived.map { it.id })
            assertTrue(viewModel.content().active.any { it.id == gym.id })
        }

    @Test
    fun onToggleArchived_lastActive_emitsErrorAndDoesNotWrite() =
        runTest(testDispatcher) {
            val onlyActive = BuiltInCategories.all.drop(1).map { it.copy(archived = true) }
            val repo = FakeCategoryRepository(onlyActive)
            val viewModel = start(repo)
            assertEquals(listOf(BuiltInCategories.TRANSFER.id), viewModel.content().active.map { it.id })

            viewModel.onToggleArchived(BuiltInCategories.TRANSFER.id)
            advanceUntilIdle()

            assertEquals(listOf<UiText>(UiText.StringResource(R.string.error_category_last_active)), errors)
            assertEquals(onlyActive, repo.docs)
        }

    @Test
    fun onToggleArchived_unknownId_doesNothing() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onToggleArchived(CategoryId("nope"))
            advanceUntilIdle()
            assertTrue(repo.docs.isEmpty())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onToggleArchived_failure_emitsError() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(listOf(gym), nextWriteError = RuntimeException("boom"))
            val viewModel = start(repo)
            viewModel.onToggleArchived(gym.id)
            advanceUntilIdle()
            assertEquals(listOf<UiText>(saveFailed), errors)
            assertEquals(listOf(BuiltInCategories.RECURRING.id), viewModel.content().archived.map { it.id })
        }

    @Test
    fun onToggleArchived_clearsDeleteDialog() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository(listOf(gym)))
            budgets.setBudget(gym.id, 1_000L) // in use: the dialog stays open instead of deleting at once
            viewModel.onDeleteRequested(gym.id)
            advanceUntilIdle()
            assertTrue(viewModel.content().deleteDialog != null)

            viewModel.onToggleArchived(gym.id)
            advanceUntilIdle()
            assertNull(viewModel.content().deleteDialog)
        }

    @Test
    fun onMove_down_persistsSwappedOrderWithArchivedAppended() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(listOf(gym, old))
            val viewModel = start(repo)

            viewModel.onMove(BuiltInCategories.TRANSFER.id, MoveDirection.DOWN)
            advanceUntilIdle()

            val expected =
                listOf(
                    BuiltInCategories.INVESTMENTS.id,
                    BuiltInCategories.TRANSFER.id,
                    BuiltInCategories.SHOPPING.id,
                    BuiltInCategories.FOOD.id,
                    gym.id,
                    BuiltInCategories.RECURRING.id,
                    old.id,
                )
            assertEquals(expected, mergeCategories(repo.docs).map { it.id })
            assertEquals(expected.indices.toList(), mergeCategories(repo.docs).map { it.order })
            assertEquals(listOf(BuiltInCategories.RECURRING.id, old.id), viewModel.content().archived.map { it.id })
        }

    @Test
    fun onMove_atEnd_writesNothing() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onMove(BuiltInCategories.TRANSFER.id, MoveDirection.UP)
            viewModel.onMove(BuiltInCategories.FOOD.id, MoveDirection.DOWN)
            viewModel.onMove(CategoryId("nope"), MoveDirection.UP)
            advanceUntilIdle()
            assertTrue(repo.docs.isEmpty())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onMove_failure_emitsError() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(nextWriteError = RuntimeException("boom"))
            val viewModel = start(repo)
            viewModel.onMove(BuiltInCategories.TRANSFER.id, MoveDirection.DOWN)
            advanceUntilIdle()
            assertEquals(listOf<UiText>(saveFailed), errors)
            assertEquals(
                BuiltInCategories.TRANSFER.id,
                viewModel
                    .content()
                    .active
                    .first()
                    .id,
            )
        }
}
