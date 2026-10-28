package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.optionsIncluding
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.userCategory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelArchivedOptionsTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun expenseIn(category: com.joaoeoneves.fintrack.domain.model.Category) =
        Expense("e1", "Thing", 500L, category, Instant.parse("2026-02-01T10:00:00Z"), null)

    @Test
    fun editing_archivedCurrentCategory_isOfferedInPickerOptions() =
        runTest(testDispatcher) {
            val archived = userCategory("old", "Old", archived = true)
            val vm =
                AddEditExpenseViewModel(
                    FakeExpenseRepository(listOf(expenseIn(archived))),
                    FakeCategoryRepository(listOf(archived)),
                    deleteTransactionFor(),
                    SavedStateHandle(mapOf("expenseId" to "e1")),
                )
            backgroundScope.launch { vm.categoryOptions.collect {} }
            advanceUntilIdle()

            val form = (vm.uiState.value as AddEditUiState.Ready).form
            val options = vm.categoryOptions.value.optionsIncluding(form.category)

            assertEquals(archived.id, form.category.id)
            assertTrue(options.any { it.id == archived.id })
            assertEquals(1, options.count { it.id == archived.id })
        }

    @Test
    fun newExpense_archivedCategory_isNotOffered() =
        runTest(testDispatcher) {
            val archived = userCategory("old", "Old", archived = true)
            val vm =
                AddEditExpenseViewModel(
                    FakeExpenseRepository(),
                    FakeCategoryRepository(listOf(archived)),
                    deleteTransactionFor(),
                    SavedStateHandle(),
                )
            backgroundScope.launch { vm.categoryOptions.collect {} }
            advanceUntilIdle()

            val form = (vm.uiState.value as AddEditUiState.Ready).form

            val options = vm.categoryOptions.value.optionsIncluding(form.category)

            assertFalse(options.any { it.id == archived.id })
        }
}
