package com.joaoeoneves.fintrack.ui.expense.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory.RECURRING
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * [ExpenseListViewModel] category filter, extended query matching (note/label/amount), refund
 * sorting/undo, and the guarantee that only period/retry changes re-subscribe the repository.
 * Robolectric for the same `toRoute` / `Bundle` reason documented on [ExpenseListViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpenseListViewModelFilterTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val coffee =
        Expense(
            id = "coffee",
            name = "Morning Coffee",
            amountCents = 450L,
            category = ExpenseCategory.SHOPPING,
            date = now.minus(1, ChronoUnit.DAYS),
            note = "oat milk",
        )
    private val shoes = coffee.copy(id = "shoes", name = "Shoes", amountCents = 8_000L, note = null)
    private val rent =
        coffee.copy(id = "rent", name = "Rent", amountCents = 90_000L, category = RECURRING, note = null)
    private val coffeeSub =
        coffee.copy(id = "sub", name = "Coffee subscription", amountCents = 1_500L, category = RECURRING)

    /** Delegating repository that counts `observeExpenses` subscriptions. */
    private class CountingExpenseRepository(
        private val delegate: FakeExpenseRepository,
    ) : ExpenseRepository by delegate {
        var observeCalls = 0

        override fun observeExpenses(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Expense>> {
            observeCalls++
            return delegate.observeExpenses(startInclusive, endExclusive)
        }
    }

    private fun viewModel(
        repository: ExpenseRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("period" to "current")),
    ) = ExpenseListViewModel(repository, savedStateHandle, clock)

    private fun TestScope.collect(vm: ExpenseListViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun ExpenseListViewModel.content() = uiState.value as ExpenseListUiState.Content

    // ---- category filter ----

    @Test
    fun categoryFilter_defaultsToNull_showingAllCategories() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, rent)))
            collect(vm)

            assertNull(vm.categoryFilter.value)
            assertNull(vm.content().categoryFilter)
            assertEquals(2, vm.content().expenses.size)
        }

    @Test
    fun onCategorySelected_filtersToThatCategory_andNullRestoresAll() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, shoes, rent)))
            collect(vm)

            vm.onCategorySelected(ExpenseCategory.SHOPPING)
            advanceUntilIdle()
            assertEquals(ExpenseCategory.SHOPPING, vm.categoryFilter.value)
            assertEquals(ExpenseCategory.SHOPPING, vm.content().categoryFilter)
            assertEquals(setOf(coffee, shoes), vm.content().expenses.toSet())

            vm.onCategorySelected(null)
            advanceUntilIdle()
            assertNull(vm.content().categoryFilter)
            assertEquals(setOf(coffee, shoes, rent), vm.content().expenses.toSet())
        }

    @Test
    fun onCategorySelected_categoryWithNoEntries_isEmptyContent() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, rent)))
            collect(vm)

            vm.onCategorySelected(ExpenseCategory.INVESTMENTS)
            advanceUntilIdle()

            assertTrue(vm.content().expenses.isEmpty())
        }

    @Test
    fun categoryFilter_isPersistedUnderCategoryFilterKey_andSurvivesRecreation() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository(listOf(coffee, rent))
            val handle = SavedStateHandle(mapOf("period" to "current"))
            val original = viewModel(repo, handle)
            original.onCategorySelected(ExpenseCategory.RECURRING)

            assertEquals(ExpenseCategory.RECURRING, handle.get<ExpenseCategory>("categoryFilter"))

            val recreated = viewModel(repo, handle)
            collect(recreated)
            assertEquals(ExpenseCategory.RECURRING, recreated.categoryFilter.value)
            assertEquals(listOf(rent), recreated.content().expenses)
        }

    @Test
    fun categoryFilter_andQuery_areAnded() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, coffeeSub, rent)))
            collect(vm)

            vm.onCategorySelected(ExpenseCategory.RECURRING)
            vm.onQueryChanged("coffee")
            advanceUntilIdle()

            assertEquals(listOf(coffeeSub), vm.content().expenses)
        }

    // ---- extended query matching ----

    @Test
    fun query_matchesNote() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, shoes)))
            collect(vm)

            vm.onQueryChanged("OAT")
            advanceUntilIdle()

            assertEquals(listOf(coffee), vm.content().expenses)
        }

    @Test
    fun query_matchesLocalizedCategoryLabel_pushedByScreen() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, rent)))
            collect(vm)

            vm.onCategoryLabelsChanged(mapOf(ExpenseCategory.RECURRING to "Recorrente"))
            vm.onQueryChanged("orrent")
            advanceUntilIdle()

            assertEquals(listOf(rent), vm.content().expenses)
        }

    @Test
    fun query_matchesCategoryEnumName_whenNoLabelsPushed() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, rent)))
            collect(vm)

            vm.onQueryChanged("shopping")
            advanceUntilIdle()

            assertEquals(listOf(coffee), vm.content().expenses)
        }

    @Test
    fun query_matchesAmount_withCommaSeparator() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, shoes, rent)))
            collect(vm)

            vm.onQueryChanged("4,50")
            advanceUntilIdle()

            assertEquals(listOf(coffee), vm.content().expenses)
        }

    // ---- refunds ----

    @Test
    fun amountSort_withRefund_ordersBySignedValue() =
        runTest(testDispatcher) {
            val refund = shoes.copy(id = "refund", amountCents = 100_000L, isRefund = true)
            val vm = viewModel(FakeExpenseRepository(listOf(coffee, refund, rent)))
            collect(vm)

            vm.onSortSelected(SortOption.AMOUNT_DESC)
            advanceUntilIdle()
            assertEquals(listOf(rent, coffee, refund), vm.content().expenses)

            vm.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()
            assertEquals(listOf(refund, coffee, rent), vm.content().expenses)
        }

    @Test
    fun deleteThenUndo_refund_isReAddedStillMarkedAsRefund() =
        runTest(testDispatcher) {
            val refund = shoes.copy(id = "refund", isRefund = true)
            val repo = FakeExpenseRepository(listOf(coffee, refund))
            val vm = viewModel(repo)
            val undoEvents = mutableListOf<Expense>()
            backgroundScope.launch { vm.undoEvent.collect { undoEvents += it } }
            collect(vm)

            vm.onDeleteExpense("refund")
            advanceUntilIdle()
            assertEquals(listOf(coffee), vm.content().expenses)
            assertTrue(undoEvents.single().isRefund)

            vm.onUndoDelete(undoEvents.single())
            advanceUntilIdle()

            val restored = repo.getExpense("refund").getOrThrow()
            assertEquals(true, restored?.isRefund)
            assertEquals(8_000L, restored?.amountCents)
            assertTrue(vm.content().expenses.any { it.id == "refund" && it.isRefund })
        }

    // ---- subscription stability ----

    @Test
    fun changingQueryFilterSortOrLabels_doesNotReSubscribeRepository() =
        runTest(testDispatcher) {
            val repo = CountingExpenseRepository(FakeExpenseRepository(listOf(coffee, rent)))
            val vm = viewModel(repo)
            collect(vm)
            assertEquals(1, repo.observeCalls)

            vm.onQueryChanged("c")
            advanceUntilIdle()
            vm.onQueryChanged("co")
            advanceUntilIdle()
            vm.onCategorySelected(ExpenseCategory.SHOPPING)
            advanceUntilIdle()
            vm.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()
            vm.onCategoryLabelsChanged(mapOf(ExpenseCategory.SHOPPING to "Compras"))
            advanceUntilIdle()

            assertEquals(1, repo.observeCalls)
            assertEquals(listOf(coffee), vm.content().expenses)
        }

    @Test
    fun changingPeriodOrRetrying_doesReSubscribeRepository() =
        runTest(testDispatcher) {
            val repo = CountingExpenseRepository(FakeExpenseRepository(listOf(coffee)))
            val vm = viewModel(repo)
            collect(vm)

            vm.onPreviousMonth()
            advanceUntilIdle()
            assertEquals(2, repo.observeCalls)
            assertTrue(vm.content().period is Period.Month)

            vm.onRetry()
            advanceUntilIdle()
            assertEquals(3, repo.observeCalls)
        }
}
