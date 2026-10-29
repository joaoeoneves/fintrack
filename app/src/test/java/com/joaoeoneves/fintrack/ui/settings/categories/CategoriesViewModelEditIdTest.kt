package com.joaoeoneves.fintrack.ui.settings.categories

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The `editId` route argument: a detail page's "Edit" opens that category's editor, once. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CategoriesViewModelEditIdTest : CategoriesViewModelTestBase() {
    private val gym = userCategory("custom-1", "Gym", order = 10)

    private fun editor(viewModel: CategoriesViewModel) = (viewModel.uiState.value as CategoriesUiState.Content).editor

    @Test
    fun editId_opensTheEditorForThatCategory() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository(listOf(gym)), editId = gym.id.value)

            assertEquals(gym.id, editor(viewModel)?.editingId)
        }

    @Test
    fun editId_opensOnlyOnce_afterDismissItStaysClosed() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository(listOf(gym)), editId = gym.id.value)
            viewModel.onDismissEditor()
            advanceUntilIdle()
            viewModel.onAdd()
            viewModel.onDismissEditor()
            advanceUntilIdle()

            assertNull(editor(viewModel))
        }

    @Test
    fun unknownEditId_opensNothing() =
        runTest(testDispatcher) {
            val viewModel = start(FakeCategoryRepository(listOf(gym)), editId = "ghost")

            assertNull(editor(viewModel))
        }

    @Test
    fun noEditId_opensNothing() =
        runTest(testDispatcher) {
            assertNull(editor(start(FakeCategoryRepository(listOf(gym)))))
        }
}
