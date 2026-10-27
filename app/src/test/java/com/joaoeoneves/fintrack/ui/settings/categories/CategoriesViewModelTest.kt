package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest : CategoriesViewModelTestBase() {
    private val gym = userCategory("custom-1", "Gym", order = 10)
    private val old = userCategory("custom-2", "Old", order = 11, archived = true)

    @Test
    fun uiState_startsLoading_thenSplitsActiveAndArchivedInOrder() =
        runTest(testDispatcher) {
            val viewModel = newViewModel(FakeCategoryRepository(listOf(gym, old)))
            assertEquals(CategoriesUiState.Loading, viewModel.uiState.value)

            val started = start(FakeCategoryRepository(listOf(gym, old)))
            val content = started.content()
            assertEquals(
                BuiltInCategories.all.filterNot { it.archived }.map { it.id } + gym.id,
                content.active.map { it.id },
            )
            assertEquals(listOf(BuiltInCategories.RECURRING.id, old.id), content.archived.map { it.id })
            assertNull(content.editor)
            assertNull(content.deleteDialog)
        }

    @Test
    fun onAdd_opensEditorWithDefaults() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository())
            viewModel.onAdd()
            advanceUntilIdle()

            val editor = viewModel.content().editor
            assertNotNull(editor)
            assertNull(editor!!.editingId)
            assertEquals(false, editor.isBuiltIn)
            assertEquals("", editor.initial.name)
            assertEquals("label", editor.initial.iconKey)
            assertEquals(CategoryKind.SPENDING, editor.initial.kind)
            assertNull(editor.nameError)
        }

    @Test
    fun onSaveEditor_validNew_addsCategoryAndClosesEditor() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("  Gym ", "home", "teal", CategoryKind.SAVING))
            advanceUntilIdle()

            val added = repo.docs.single()
            assertEquals(CategoryId("custom-1"), added.id)
            assertEquals("Gym", added.customName)
            assertEquals("home", added.iconKey)
            assertEquals("teal", added.colorKey)
            assertEquals(CategoryKind.SAVING, added.kind)
            assertNull(viewModel.content().editor)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onSaveEditor_invalid_setsNameErrorAndDoesNotWrite() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(listOf(gym))
            val viewModel = start(repo)
            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("gym", "label", "blue", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertEquals(listOf(gym), repo.docs)
            assertEquals(
                UiText.StringResource(R.string.error_category_name_duplicate),
                viewModel.content().editor?.nameError,
            )
            viewModel.onSaveEditor(CategoryDraft("", "label", "blue", CategoryKind.SPENDING))
            advanceUntilIdle()
            assertEquals(
                UiText.StringResource(R.string.error_category_name_required),
                viewModel.content().editor?.nameError,
            )
        }

    @Test
    fun onSaveEditor_withoutOpenEditor_doesNothing() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onSaveEditor(CategoryDraft("Gym", "label", "blue", CategoryKind.SPENDING))
            advanceUntilIdle()
            assertTrue(repo.docs.isEmpty())
        }

    @Test
    fun onEdit_builtIn_prefillsEmptyName() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository())
            viewModel.onEdit(BuiltInCategories.FOOD.id)
            advanceUntilIdle()

            val editor = viewModel.content().editor!!
            assertEquals(BuiltInCategories.FOOD.id, editor.editingId)
            assertTrue(editor.isBuiltIn)
            assertEquals("", editor.initial.name)
            assertEquals("restaurant", editor.initial.iconKey)
        }

    @Test
    fun onEdit_unknownId_opensNothing() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository())
            viewModel.onEdit(CategoryId("nope"))
            advanceUntilIdle()
            assertNull(viewModel.content().editor)
        }

    @Test
    fun onSaveEditor_builtInBlank_writesOverrideWithNullCustomName() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onEdit(BuiltInCategories.FOOD.id)
            viewModel.onSaveEditor(CategoryDraft("", "home", "pink", CategoryKind.SAVING))
            advanceUntilIdle()

            val stored = repo.docs.single()
            assertEquals(BuiltInCategories.FOOD.id, stored.id)
            assertNull(stored.customName)
            assertEquals("home", stored.iconKey)
            assertEquals("pink", stored.colorKey)
            assertTrue(stored.builtIn)
            assertNull(viewModel.content().editor)
        }

    @Test
    fun onSaveEditor_editUserCategory_updatesFieldsKeepsOrderAndArchived() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(listOf(gym))
            val viewModel = start(repo)
            viewModel.onEdit(gym.id)
            viewModel.onSaveEditor(CategoryDraft(" Fitness ", "restaurant", "red", CategoryKind.SAVING))
            advanceUntilIdle()

            val stored = repo.docs.single()
            val expected =
                gym.copy(customName = "Fitness", iconKey = "restaurant", colorKey = "red", kind = CategoryKind.SAVING)
            assertEquals(expected, stored)
            assertNull(viewModel.content().editor)
        }

    @Test
    fun onSaveEditor_writeFailure_emitsErrorAndKeepsEditorOpen() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(nextWriteError = RuntimeException("boom"))
            val viewModel = start(repo)
            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Gym", "label", "blue", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertEquals(listOf<UiText>(saveFailed), errors)
            assertNotNull(viewModel.content().editor)
            assertTrue(repo.docs.isEmpty())
        }

    @Test
    fun onDismissEditor_closesEditor() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository())
            viewModel.onAdd()
            advanceUntilIdle()
            viewModel.onDismissEditor()
            advanceUntilIdle()
            assertNull(viewModel.content().editor)
        }
}
