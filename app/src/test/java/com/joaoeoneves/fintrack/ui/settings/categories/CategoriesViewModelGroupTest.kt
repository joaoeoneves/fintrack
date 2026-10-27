package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.data.FakeCategoryGroupRepository
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

/** [CategoriesViewModel]'s group picker data and the group assignment through the editor. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelGroupTest : CategoriesViewModelTestBase() {
    private suspend fun seedGroups() {
        groups.addGroup("Food")
        groups.addGroup("Fun")
    }

    @Test
    fun uiState_exposesGroupsInOrder() =
        runTest(testDispatcher) {
            seedGroups()
            val viewModel = start(FakeCategoryRepository())

            assertEquals(listOf("Food", "Fun"), viewModel.content().groups.map { it.name })
        }

    @Test
    fun uiState_groupsObserveError_fallsBackToNoGroups() =
        runTest(testDispatcher) {
            val failing = FakeCategoryGroupRepository(nextObserveGroupsError = IllegalStateException("down"))
            val viewModel = start(FakeCategoryRepository(), failing)

            assertTrue(viewModel.content().groups.isEmpty())
        }

    @Test
    fun onSaveEditor_newCategoryWithGroup_isFiledUnderIt() =
        runTest(testDispatcher) {
            seedGroups()
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)
            val groupId = groups.groups.first().id

            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Gym", "home", "teal", CategoryKind.SPENDING, groupId))
            advanceUntilIdle()

            assertEquals(groupId, repo.docs.single().groupId)
        }

    @Test
    fun onSaveEditor_newCategoryWithoutGroup_isUngrouped() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)

            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Gym", "home", "teal", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertNull(repo.docs.single().groupId)
        }

    @Test
    fun onEdit_prefillsDraftGroup() =
        runTest(testDispatcher) {
            val gym = userCategory("custom-1", "Gym").copy(groupId = "g-food")
            val viewModel = start(FakeCategoryRepository(listOf(gym)))

            viewModel.onEdit(gym.id)
            advanceUntilIdle()

            assertEquals(
                "g-food",
                viewModel
                    .content()
                    .editor
                    ?.initial
                    ?.groupId,
            )
        }

    @Test
    fun onSaveEditor_changeGroup_updatesCategory_andClearingUngroups() =
        runTest(testDispatcher) {
            val gym = userCategory("custom-1", "Gym").copy(groupId = "g-food")
            val repo = FakeCategoryRepository(listOf(gym))
            val viewModel = start(repo)

            viewModel.onEdit(gym.id)
            viewModel.onSaveEditor(CategoryDraft("Gym", "home", "teal", CategoryKind.SPENDING, "g-fun"))
            advanceUntilIdle()
            assertEquals("g-fun", repo.docs.single().groupId)

            viewModel.onEdit(gym.id)
            viewModel.onSaveEditor(CategoryDraft("Gym", "home", "teal", CategoryKind.SPENDING, null))
            advanceUntilIdle()
            assertNull(repo.docs.single().groupId)
        }

    @Test
    fun onSaveEditor_builtIn_canBeFiledUnderAGroup() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = start(repo)

            viewModel.onEdit(BuiltInCategories.FOOD.id)
            viewModel.onSaveEditor(CategoryDraft("", "restaurant", "violet", CategoryKind.SPENDING, "g-food"))
            advanceUntilIdle()

            assertEquals("g-food", repo.docs.single { it.id == BuiltInCategories.FOOD.id }.groupId)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onSaveEditor_writeFailure_keepsEditorOpenAndReportsError() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository(nextWriteError = IllegalStateException("nope"))
            val viewModel = start(repo)

            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Gym", "home", "teal", CategoryKind.SPENDING, "g-food"))
            advanceUntilIdle()

            assertTrue(repo.docs.isEmpty())
            assertEquals(listOf(saveFailed), errors)
        }
}
