package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The category editor never touches an envelope: new categories are unfiled, edits keep the existing one. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelGroupTest : CategoriesViewModelTestBase() {
    @Test
    fun onSaveEditor_newCategory_isUnfiled() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)

            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Gym", "home", "teal", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertNull(repo.docs.single().groupId)
        }

    @Test
    fun onSaveEditor_editingFiledCategory_keepsItsEnvelope() =
        runTest(testDispatcher) {
            val gym = userCategory("custom-1", "Gym").copy(groupId = "g-food")
            val repo = FakeCategoryRepository(listOf(gym))
            val viewModel = start(repo)

            viewModel.onEdit(gym.id)
            viewModel.onSaveEditor(CategoryDraft("Gym 2", "home", "teal", CategoryKind.SPENDING))
            advanceUntilIdle()

            val saved = repo.docs.single()
            assertEquals("Gym 2", saved.customName)
            assertEquals("g-food", saved.groupId)
        }

    @Test
    fun onSaveEditor_editingFiledBuiltIn_keepsItsEnvelope() =
        runTest(testDispatcher) {
            val food = BuiltInCategories.FOOD.copy(groupId = "g-food")
            val repo = FakeCategoryRepository(listOf(food))
            val viewModel = start(repo)

            viewModel.onEdit(food.id)
            viewModel.onSaveEditor(CategoryDraft("", "restaurant", "violet", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertEquals("g-food", repo.docs.single { it.id == food.id }.groupId)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onSaveEditor_writeFailure_reportsError() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(nextWriteError = IllegalStateException("nope"))
            val viewModel = start(repo)

            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Gym", "home", "teal", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertTrue(repo.docs.isEmpty())
            assertEquals(listOf(saveFailed), errors)
        }
}
