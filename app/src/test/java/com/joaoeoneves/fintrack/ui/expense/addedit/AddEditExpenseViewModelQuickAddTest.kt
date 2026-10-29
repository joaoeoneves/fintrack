package com.joaoeoneves.fintrack.ui.expense.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.NameSuggestion
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
import kotlinx.serialization.json.Json
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

/** Name chips, route prefill and the review flag on save in [AddEditExpenseViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AddEditExpenseViewModelQuickAddTest {
    private val testDispatcher = StandardTestDispatcher()
    private val groceries = userCategory("groceries", "Groceries")
    private val leisure = userCategory("fun", "Fun")
    private val old = userCategory("old", "Old", archived = true)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS)

    private fun past(
        id: String,
        name: String,
        category: Category,
    ) = Expense(id, name, 500L, category, tenDaysAgo, null, needsReview = true)

    private fun TestScope.viewModel(
        expenses: FakeExpenseRepository,
        route: Map<String, Any?> = emptyMap(),
    ): AddEditExpenseViewModel {
        val vm =
            AddEditExpenseViewModel(
                expenses,
                FakeCategoryRepository(listOf(groceries, leisure, old)),
                deleteTransactionFor(expenses),
                SavedStateHandle(route.filterValues { it != null }),
            )
        backgroundScope.launch { vm.categoryOptions.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun AddEditExpenseViewModel.form() = (uiState.value as AddEditUiState.Ready).form

    private val history =
        listOf(past("1", "Pingo Doce", groceries), past("2", "Pingo Doce Express", leisure))

    @Test
    fun `typing on a new form offers name chips`() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(history))

            vm.onNameChanged("pingo")

            val chips = vm.form().nameSuggestions
            assertEquals(listOf("Pingo Doce", "Pingo Doce Express"), chips.map { it.name })
        }

    @Test
    fun `an edit form never offers name chips`() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(history), mapOf("expenseId" to "1"))

            vm.onNameChanged("pingo")

            assertTrue(vm.form().nameSuggestions.isEmpty())
        }

    @Test
    fun `picking a chip keeps the amount, sets name and category and clears the chips`() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(history))
            vm.onAmountChanged(1_234L)
            vm.onNameChanged("pingo")

            vm.onNameSuggestionPicked(NameSuggestion("Pingo Doce Express", leisure.id))

            val form = vm.form()
            assertEquals("Pingo Doce Express", form.name)
            assertEquals(1_234L, form.amountCents)
            assertEquals(leisure.id, form.category.id)
            assertTrue(form.nameSuggestions.isEmpty())
        }

    @Test
    fun `picking a chip does not override a category the user already chose`() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(history))
            vm.onCategorySelected(groceries)

            vm.onNameSuggestionPicked(NameSuggestion("Pingo Doce Express", leisure.id))

            assertEquals("Pingo Doce Express", vm.form().name)
            assertEquals(groceries.id, vm.form().category.id)
        }

    @Test
    fun `route prefill fills name, amount and an active category`() =
        runTest(testDispatcher) {
            val vm =
                viewModel(
                    FakeExpenseRepository(),
                    mapOf("prefillName" to "Gym", "prefillAmountCents" to 3_000L, "prefillCategoryId" to "fun"),
                )

            assertEquals("Gym", vm.form().name)
            assertEquals(3_000L, vm.form().amountCents)
            assertEquals(leisure.id, vm.form().category.id)
            assertTrue(vm.form().suggestedCategory)
        }

    @Test
    fun `a prefill with an archived category falls back to the last category used for the name`() =
        runTest(testDispatcher) {
            val vm =
                viewModel(
                    FakeExpenseRepository(listOf(past("1", "Gym", groceries))),
                    mapOf("prefillName" to "Gym", "prefillCategoryId" to "old"),
                )

            assertEquals(groceries.id, vm.form().category.id)
        }

    @Test
    fun `a saved snapshot wins over the route prefill`() =
        runTest(testDispatcher) {
            val snapshot =
                ExpenseFormSnapshot(
                    expenseId = null,
                    name = "Typed",
                    amountCents = 99L,
                    categoryId = groceries.id.value,
                    dateEpochMillis = 1_760_000_000_000L,
                    note = "",
                    isRefund = false,
                    paidWithMealCard = false,
                    mealCardTouched = false,
                    categoryTouched = true,
                )
            val vm =
                viewModel(
                    FakeExpenseRepository(),
                    mapOf(
                        "prefillName" to "Gym",
                        "prefillAmountCents" to 3_000L,
                        "expense_form" to Json.encodeToString(ExpenseFormSnapshot.serializer(), snapshot),
                    ),
                )

            assertEquals("Typed", vm.form().name)
            assertEquals(99L, vm.form().amountCents)
        }

    @Test
    fun `saving an edited flagged expense clears its review flag`() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(history)
            val vm = viewModel(repo, mapOf("expenseId" to "1"))

            vm.onSave()
            advanceUntilIdle()

            val saved = repo.getExpense("1").getOrThrow()
            assertFalse(saved!!.needsReview)
        }

    @Test
    fun `saving a new expense stores it as reviewed`() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo)
            vm.onNameChanged("Coffee")
            vm.onAmountChanged(250L)

            vm.onSave()
            advanceUntilIdle()

            val all = repo.getAllExpenses().getOrThrow()
            assertFalse(all.single().needsReview)
        }
}
