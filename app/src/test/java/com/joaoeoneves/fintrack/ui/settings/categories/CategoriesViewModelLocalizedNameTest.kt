package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelLocalizedNameTest : CategoriesViewModelTestBase() {
    @Test
    fun onSaveEditor_newCategoryWithLocalizedBuiltInName_setsNameErrorAndDoesNotWrite() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Compras", "label", "blue", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertTrue(repo.docs.isEmpty())
            assertEquals(
                UiText.StringResource(R.string.error_category_name_duplicate),
                viewModel.content().editor?.nameError,
            )
        }

    @Test
    fun onSaveEditor_builtInWithOwnLocalizedName_writesOverrideWithNullCustomName() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onEdit(BuiltInCategories.SHOPPING.id)
            viewModel.onSaveEditor(CategoryDraft("Compras", "label", "blue", CategoryKind.SPENDING))
            advanceUntilIdle()

            val written = repo.docs.single()
            assertEquals(BuiltInCategories.SHOPPING.id, written.id)
            assertNull(written.customName)
            assertNull(viewModel.content().editor)
        }

    @Test
    fun onSaveEditor_builtInWithOtherBuiltInsLocalizedName_isRejected() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            viewModel.onEdit(BuiltInCategories.SHOPPING.id)
            viewModel.onSaveEditor(CategoryDraft("Alimentação", "label", "blue", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertTrue(repo.docs.isEmpty())
            assertEquals(
                UiText.StringResource(R.string.error_category_name_duplicate),
                viewModel.content().editor?.nameError,
            )
        }
}
