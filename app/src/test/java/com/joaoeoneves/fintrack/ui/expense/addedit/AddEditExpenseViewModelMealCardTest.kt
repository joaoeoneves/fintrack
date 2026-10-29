package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.deleteTransactionFor
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The "paid with the meal card" switch of [AddEditExpenseViewModel]: availability, prefill, touch and save. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelMealCardTest {
    private val testDispatcher = StandardTestDispatcher()
    private val lunch = userCategory("lunch", "Lunch").copy(mealCardByDefault = true)
    private val other = userCategory("other", "Other")

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(
        repo: FakeExpenseRepository = FakeExpenseRepository(),
        available: Flow<Boolean> = MutableStateFlow(true),
        expenseId: String? = null,
    ): AddEditExpenseViewModel {
        val viewModel =
            AddEditExpenseViewModel(
                repo,
                FakeCategoryRepository(listOf(lunch, other)),
                deleteTransactionFor(repo),
                SavedStateHandle(if (expenseId == null) emptyMap() else mapOf("expenseId" to expenseId)),
                available,
            )
        backgroundScope.launch { viewModel.categoryOptions.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    private fun stored(
        card: Boolean,
        category: Category = other,
        refund: Boolean = false,
    ) = Expense("e1", "Lunch", 500L, category, Instant.parse("2026-10-01T10:00:00Z"), null, refund, paidWithMealCard = card)

    @Test
    fun unavailable_hidesTheSwitch_andNeverPrefills() =
        runTest(testDispatcher) {
            val viewModel = vm(available = MutableStateFlow(false))

            viewModel.onCategorySelected(lunch)

            assertFalse(viewModel.form().mealCardAvailable)
            assertFalse(viewModel.form().paidWithMealCard)
        }

    @Test
    fun categoryChange_prefillsFromTheCategory() =
        runTest(testDispatcher) {
            val viewModel = vm()

            viewModel.onCategorySelected(lunch)
            assertTrue(viewModel.form().paidWithMealCard)

            viewModel.onCategorySelected(other)
            assertFalse(viewModel.form().paidWithMealCard)
        }

    @Test
    fun nameSuggestion_prefills() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(stored(false, lunch).copy(id = "old", name = "Menu do dia")))
            val viewModel = vm(repo)

            viewModel.onNameChanged("menu do dia")
            advanceUntilIdle()

            assertEquals(lunch.id, viewModel.form().category.id)
            assertTrue(viewModel.form().paidWithMealCard)
        }

    @Test
    fun refundToggle_turnsItOff_andBackOnForUntouched() =
        runTest(testDispatcher) {
            val viewModel = vm()
            viewModel.onCategorySelected(lunch)

            viewModel.onRefundChanged(true)
            assertFalse(viewModel.form().paidWithMealCard)

            viewModel.onRefundChanged(false)
            assertTrue(viewModel.form().paidWithMealCard)
        }

    @Test
    fun touched_stopsPrefill() =
        runTest(testDispatcher) {
            val viewModel = vm()
            viewModel.onMealCardChanged(false)

            viewModel.onCategorySelected(lunch)
            viewModel.onRefundChanged(true)
            viewModel.onRefundChanged(false)

            assertFalse(viewModel.form().paidWithMealCard)
            assertTrue(viewModel.form().mealCardTouched)
        }

    @Test
    fun editMode_keepsTheStoredValue() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(stored(card = false, category = other)))
            val viewModel = vm(repo, expenseId = "e1")

            viewModel.onCategorySelected(lunch)

            assertFalse(viewModel.form().paidWithMealCard)
            assertTrue(vm(FakeExpenseRepository(listOf(stored(true))), expenseId = "e1").form().paidWithMealCard)
        }

    @Test
    fun save_carriesTheFlag() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = vm(repo)
            viewModel.onNameChanged("Almoço")
            viewModel.onAmountChanged(850L)
            viewModel.onCategorySelected(lunch)
            viewModel.onSave()
            advanceUntilIdle()

            assertTrue(
                repo
                    .getAllExpenses()
                    .getOrThrow()
                    .single()
                    .paidWithMealCard,
            )
        }

    @Test
    fun save_afterSwitchingOff_storesFalse() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = vm(repo)
            viewModel.onNameChanged("Almoço")
            viewModel.onAmountChanged(850L)
            viewModel.onCategorySelected(BuiltInCategories.FOOD.copy(mealCardByDefault = true))
            viewModel.onMealCardChanged(false)
            viewModel.onSave()
            advanceUntilIdle()

            assertFalse(
                repo
                    .getAllExpenses()
                    .getOrThrow()
                    .single()
                    .paidWithMealCard,
            )
        }
}
