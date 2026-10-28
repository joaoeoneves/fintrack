package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** [AddEditExpenseViewModel] behaviour tied to the dynamic category catalog. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelCategoryTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun expenseIn(category: Category) =
        Expense(
            id = "e1",
            name = "Thing",
            amountCents = 500L,
            category = category,
            date = Instant.parse("2026-02-01T10:00:00Z"),
            note = null,
        )

    private fun viewModel(
        expenses: FakeExpenseRepository,
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        editId: String? = null,
    ) = AddEditExpenseViewModel(
        expenses,
        categories,
        deleteTransactionFor(expenses),
        SavedStateHandle(if (editId != null) mapOf("expenseId" to editId) else emptyMap()),
    )

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    @Test
    fun newExpense_defaultsToShopping_orFirstActiveWhenShoppingArchived() =
        runTest(testDispatcher) {
            val normal = viewModel(FakeExpenseRepository())
            val archived =
                viewModel(
                    FakeExpenseRepository(),
                    FakeCategoryRepository(listOf(BuiltInCategories.SHOPPING.copy(archived = true))),
                )
            advanceUntilIdle()

            assertEquals(BuiltInCategories.SHOPPING, normal.form().category)
            assertEquals(BuiltInCategories.TRANSFER, archived.form().category)
        }

    @Test
    fun categoryOptions_initiallyBuiltIns_thenIncludeUserCategories() =
        runTest(testDispatcher) {
            val gym = userCategory("gym", "Gym")
            val vm = viewModel(FakeExpenseRepository(), FakeCategoryRepository(listOf(gym)))
            assertEquals(BuiltInCategories.all, vm.categoryOptions.value)

            val job = launch { vm.categoryOptions.collect {} }
            advanceUntilIdle()

            assertTrue(vm.categoryOptions.value.contains(gym))
            job.cancel()
        }

    @Test
    fun saveWithUnknownCategory_setsError_andDoesNotWrite() =
        runTest(testDispatcher) {
            val ghost = Category.unknown(CategoryId("ghost"))
            val repo = FakeExpenseRepository(listOf(expenseIn(ghost)))
            val vm = viewModel(repo, editId = "e1")
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_category_required), vm.form().categoryError)
            assertEquals(
                ghost,
                repo
                    .getAllExpenses()
                    .getOrThrow()
                    .single()
                    .category,
            )
        }

    @Test
    fun selectingACategory_clearsTheError_andSaveThenSucceeds() =
        runTest(testDispatcher) {
            val ghost = Category.unknown(CategoryId("ghost"))
            val repo = FakeExpenseRepository(listOf(expenseIn(ghost)))
            val vm = viewModel(repo, editId = "e1")
            advanceUntilIdle()
            vm.onSave()
            advanceUntilIdle()

            vm.onCategorySelected(BuiltInCategories.FOOD)
            assertNull(vm.form().categoryError)
            vm.onSave()
            advanceUntilIdle()

            assertEquals(
                BuiltInCategories.FOOD,
                repo
                    .getAllExpenses()
                    .getOrThrow()
                    .single()
                    .category,
            )
        }

    @Test
    fun archivedCurrentCategory_savesFine() =
        runTest(testDispatcher) {
            val archived = userCategory("old", "Old", archived = true)
            val repo = FakeExpenseRepository(listOf(expenseIn(archived)))
            val vm = viewModel(repo, FakeCategoryRepository(listOf(archived)), editId = "e1")
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            assertNull(vm.form().categoryError)
            assertEquals(
                archived.id,
                repo
                    .getAllExpenses()
                    .getOrThrow()
                    .single()
                    .category.id,
            )
        }
}
