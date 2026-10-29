package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBuiltInCategoryNames
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.usecase.CheckCategoryUsage
import com.joaoeoneves.fintrack.domain.usecase.MoveCategoryItems
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "usually paid with the meal card" flag of a category. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelMealCardTest : CategoriesViewModelTestBase() {
    private fun kotlinx.coroutines.test.TestScope.vm(
        repo: FakeCategoryRepository,
        available: Boolean = true,
    ): CategoriesViewModel {
        val viewModel =
            CategoriesViewModel(
                repo,
                CheckCategoryUsage(expenses, budgets, recurring),
                FakeBuiltInCategoryNames(),
                MoveCategoryItems(expenses, budgets, recurring, testDispatcher),
                SavedStateHandle(),
                flowOf(available),
            )
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun addNew_spending_flagsTheCategory() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = vm(repo)
            viewModel.onAdd()
            viewModel.onSaveEditor(
                CategoryDraft(
                    "Lunch",
                    "home",
                    "teal",
                    CategoryKind.SPENDING,
                    mealCardByDefault = true,
                ),
            )
            advanceUntilIdle()

            assertTrue(repo.docs.single().mealCardByDefault)
        }

    @Test
    fun addNew_withoutFlag_staysOff() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = vm(repo)
            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Lunch", "home", "teal", CategoryKind.SPENDING))
            advanceUntilIdle()

            assertFalse(repo.docs.single().mealCardByDefault)
        }

    @Test
    fun addNew_savingKind_forcesTheFlagOff() =
        runTest(testDispatcher) {
            val repo = FakeCategoryRepository()
            val viewModel = vm(repo)
            viewModel.onAdd()
            viewModel.onSaveEditor(CategoryDraft("Pot", "home", "teal", CategoryKind.SAVING, mealCardByDefault = true))
            advanceUntilIdle()

            assertFalse(repo.docs.single().mealCardByDefault)
        }

    @Test
    fun edit_toSavingKind_clearsTheFlag_andSpendingKeepsIt() =
        runTest(testDispatcher) {
            val lunch = userCategory("lunch", "Lunch").copy(mealCardByDefault = true)
            val repo = FakeCategoryRepository(listOf(lunch))
            val viewModel = vm(repo)
            viewModel.onEdit(lunch.id)
            advanceUntilIdle()
            assertTrue(
                viewModel
                    .content()
                    .editor!!
                    .initial.mealCardByDefault,
            )
            viewModel.onSaveEditor(
                CategoryDraft(
                    "Lunch",
                    "home",
                    "teal",
                    CategoryKind.SPENDING,
                    mealCardByDefault = true,
                ),
            )
            advanceUntilIdle()
            assertTrue(repo.docs.single().mealCardByDefault)

            viewModel.onEdit(lunch.id)
            advanceUntilIdle()
            viewModel.onSaveEditor(
                CategoryDraft(
                    "Lunch",
                    "home",
                    "teal",
                    CategoryKind.SAVING,
                    mealCardByDefault = true,
                ),
            )
            advanceUntilIdle()
            assertFalse(repo.docs.single().mealCardByDefault)
        }

    @Test
    fun editor_reportsAvailability() =
        runTest(testDispatcher) {
            val on = vm(FakeCategoryRepository(), available = true)
            on.onAdd()
            advanceUntilIdle()
            assertEquals(true, on.content().editor?.mealCardAvailable)

            val off = vm(FakeCategoryRepository(), available = false)
            off.onAdd()
            advanceUntilIdle()
            assertEquals(false, off.content().editor?.mealCardAvailable)
        }
}
