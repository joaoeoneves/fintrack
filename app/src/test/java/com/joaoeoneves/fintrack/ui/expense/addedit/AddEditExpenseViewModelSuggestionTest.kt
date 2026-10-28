package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Category suggestion from the expense name in [AddEditExpenseViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelSuggestionTest {
    private val testDispatcher = StandardTestDispatcher()
    private val groceries = userCategory("groceries", "Groceries")
    private val leisure = userCategory("fun", "Fun")
    private var defaultId = CategoryId("")
    private val old = userCategory("old", "Old", archived = true)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun past(
        id: String,
        name: String,
        category: Category,
        daysAgo: Long = 10,
    ) = Expense(id, name, 500L, category, Instant.now().minus(daysAgo, ChronoUnit.DAYS), null)

    private fun TestScope.viewModel(
        expenses: FakeExpenseRepository,
        editId: String? = null,
    ): AddEditExpenseViewModel {
        val vm =
            AddEditExpenseViewModel(
                expenses,
                FakeCategoryRepository(listOf(groceries, leisure, old)),
                deleteTransactionFor(expenses),
                SavedStateHandle(if (editId == null) emptyMap() else mapOf("expenseId" to editId)),
            )
        backgroundScope.launch { vm.categoryOptions.collect {} }
        advanceUntilIdle()
        defaultId = vm.form().category.id
        return vm
    }

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    @Test
    fun matchingName_fillsCategoryAndFlag() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Pingo Doce", groceries))))

            vm.onNameChanged("  pingo   DOCE ")
            advanceUntilIdle()

            assertEquals(groceries.id, vm.form().category.id)
            assertTrue(vm.form().suggestedCategory)
            assertFalse(vm.form().categoryTouched)
        }

    @Test
    fun accentInsensitiveMatch() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Café", leisure))))

            vm.onNameChanged("cafe")

            assertEquals(leisure.id, vm.form().category.id)
        }

    @Test
    fun latestExpenseWinsAcrossHistory() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(past("1", "Shop", groceries, daysAgo = 60), past("2", "Shop", leisure, daysAgo = 5)),
                )
            val vm = viewModel(repo)

            vm.onNameChanged("shop")

            assertEquals(leisure.id, vm.form().category.id)
        }

    @Test
    fun noMatch_keepsDefaultAndNoFlag() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Pingo Doce", groceries))))

            vm.onNameChanged("Unknown place")

            assertEquals(defaultId, vm.form().category.id)
            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun manualPick_blocksLaterSuggestions() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Pingo Doce", groceries))))

            vm.onCategorySelected(leisure)
            vm.onNameChanged("Pingo Doce")

            assertEquals(leisure.id, vm.form().category.id)
            assertTrue(vm.form().categoryTouched)
            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun manualPick_afterSuggestion_clearsFlagAndSticks() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Pingo Doce", groceries))))
            vm.onNameChanged("Pingo Doce")

            vm.onCategorySelected(leisure)
            vm.onNameChanged("")

            assertEquals(leisure.id, vm.form().category.id)
            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun clearingName_revertsToDefault_whenSuggested() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Pingo Doce", groceries))))
            vm.onNameChanged("Pingo Doce")

            vm.onNameChanged("Pingo")

            assertEquals(defaultId, vm.form().category.id)
            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun nonMatchingName_doesNotRevertNonSuggestedDefault() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Pingo Doce", groceries))))

            vm.onNameChanged("abc")
            vm.onNameChanged("abcd")

            assertEquals(defaultId, vm.form().category.id)
            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun archivedCategory_isNotSuggested() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Gym", old))))

            vm.onNameChanged("Gym")

            assertEquals(defaultId, vm.form().category.id)
            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun expensesOlderThanAYear_areNotUsed() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(past("1", "Gym", groceries, daysAgo = 400))))

            vm.onNameChanged("Gym")

            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun editMode_isUnaffected() =
        runTest(testDispatcher) {
            val existing = past("e1", "Pingo Doce", leisure)
            val other = past("e2", "Lidl", groceries)
            val vm = viewModel(FakeExpenseRepository(listOf(existing, other)), editId = "e1")

            vm.onNameChanged("Lidl")

            assertEquals(leisure.id, vm.form().category.id)
            assertFalse(vm.form().suggestedCategory)
        }

    @Test
    fun loadFailure_meansNoSuggestions() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(past("1", "Pingo Doce", groceries)),
                    nextObserveExpensesError = IllegalStateException("boom"),
                )
            val vm = viewModel(repo)

            vm.onNameChanged("Pingo Doce")

            assertTrue(vm.uiState.value is AddEditUiState.Ready)
            assertEquals(defaultId, vm.form().category.id)
            assertFalse(vm.form().suggestedCategory)
        }
}
