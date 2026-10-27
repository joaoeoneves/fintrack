package com.joaoeoneves.fintrack.ui.expense.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.testutil.genericErrorText
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [ExpenseListViewModel].
 *
 * The initial period is read from the "period" key of the [SavedStateHandle] (absent means the current
 * month). Robolectric supplies the real `android.os.Bundle` that [SavedStateHandle] needs.
 * Period-specific behavior lives in [ExpenseListViewModelPeriodTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpenseListViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Fixed "now" (2026-10-20 12:00 UTC); every fixture in this class lands inside October 2026
    // unless deliberately placed 20+ days back (in September).
    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun expense(
        id: String,
        name: String = id,
        amountCents: Long,
        category: Category = BuiltInCategories.SHOPPING,
        date: Instant,
        note: String? = null,
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    private fun viewModel(
        repository: com.joaoeoneves.fintrack.domain.repository.ExpenseRepository,
        initialPeriod: String = "current",
    ) = ExpenseListViewModel(
        repository,
        FakeCategoryRepository(),
        SavedStateHandle(mapOf("period" to initialPeriod)),
        clock,
    )

    // ---- search / query ----

    @Test
    fun onQueryChanged_caseInsensitivePartialMatch_filtersExpenses() =
        runTest(testDispatcher) {
            val coffee = expense(id = "1", name = "Morning Coffee", amountCents = 500L, date = now)
            val groceries = expense(id = "2", name = "Groceries", amountCents = 3_000L, date = now)
            val repo = FakeExpenseRepository(listOf(coffee, groceries))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("COFFEE")
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(listOf(coffee), content.expenses)
            assertEquals("COFFEE", content.query)

            job.cancel()
        }

    @Test
    fun onQueryChanged_partialMatch_matchesSubstringAnywhereInName() =
        runTest(testDispatcher) {
            val expense1 = expense(id = "1", name = "Weekly Groceries Run", amountCents = 500L, date = now)
            val repo = FakeExpenseRepository(listOf(expense1))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("rocer")
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(listOf(expense1), content.expenses)

            job.cancel()
        }

    @Test
    fun onQueryChanged_noMatch_producesEmptyList() =
        runTest(testDispatcher) {
            val coffee = expense(id = "1", name = "Morning Coffee", amountCents = 500L, date = now)
            val repo = FakeExpenseRepository(listOf(coffee))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("nonexistent-xyz")
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertTrue(content.expenses.isEmpty())

            job.cancel()
        }

    @Test
    fun onQueryChanged_blankQuery_isEquivalentToNoFilter() =
        runTest(testDispatcher) {
            val coffee = expense(id = "1", name = "Morning Coffee", amountCents = 500L, date = now)
            val groceries = expense(id = "2", name = "Groceries", amountCents = 3_000L, date = now)
            val repo = FakeExpenseRepository(listOf(coffee, groceries))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("coffee")
            advanceUntilIdle()
            vm.onQueryChanged("   ")
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(2, content.expenses.size)

            job.cancel()
        }

    // ---- sorting ----

    @Test
    fun onSortSelected_dateDesc_isDefault_andSortsNewestFirst() =
        runTest(testDispatcher) {
            val older = expense(id = "older", amountCents = 100L, date = now.minus(5, ChronoUnit.DAYS))
            val newer = expense(id = "newer", amountCents = 200L, date = now.minus(1, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(older, newer))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(SortOption.DATE_DESC, content.sortOption)
            assertEquals(listOf(newer, older), content.expenses)

            job.cancel()
        }

    @Test
    fun onSortSelected_dateAsc_sortsOldestFirst() =
        runTest(testDispatcher) {
            val older = expense(id = "older", amountCents = 100L, date = now.minus(5, ChronoUnit.DAYS))
            val newer = expense(id = "newer", amountCents = 200L, date = now.minus(1, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(older, newer))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onSortSelected(SortOption.DATE_ASC)
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(SortOption.DATE_ASC, content.sortOption)
            assertEquals(listOf(older, newer), content.expenses)

            job.cancel()
        }

    @Test
    fun onSortSelected_amountDesc_sortsHighestFirst() =
        runTest(testDispatcher) {
            val cheap = expense(id = "cheap", amountCents = 100L, date = now.minus(1, ChronoUnit.DAYS))
            val expensive = expense(id = "expensive", amountCents = 9_999L, date = now.minus(5, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(cheap, expensive))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onSortSelected(SortOption.AMOUNT_DESC)
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(SortOption.AMOUNT_DESC, content.sortOption)
            assertEquals(listOf(expensive, cheap), content.expenses)

            job.cancel()
        }

    @Test
    fun onSortSelected_amountAsc_sortsLowestFirst() =
        runTest(testDispatcher) {
            val cheap = expense(id = "cheap", amountCents = 100L, date = now.minus(1, ChronoUnit.DAYS))
            val expensive = expense(id = "expensive", amountCents = 9_999L, date = now.minus(5, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(cheap, expensive))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(SortOption.AMOUNT_ASC, content.sortOption)
            assertEquals(listOf(cheap, expensive), content.expenses)

            job.cancel()
        }

    @Test
    fun sorting_appliesAfterQueryFilter() =
        runTest(testDispatcher) {
            val coffeeCheap = expense(id = "c1", name = "Coffee small", amountCents = 300L, date = now)
            val coffeeExpensive = expense(id = "c2", name = "Coffee large", amountCents = 700L, date = now)
            val groceries = expense(id = "g1", name = "Groceries", amountCents = 5_000L, date = now)
            val repo = FakeExpenseRepository(listOf(coffeeCheap, coffeeExpensive, groceries))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("coffee")
            vm.onSortSelected(SortOption.AMOUNT_DESC)
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(listOf(coffeeExpensive, coffeeCheap), content.expenses)

            job.cancel()
        }

    // ---- delete ----

    @Test
    fun onDeleteExpense_removesExpenseFromRepository_andUiState() =
        runTest(testDispatcher) {
            val toKeep = expense(id = "keep", amountCents = 100L, date = now)
            val toDelete = expense(id = "delete-me", amountCents = 200L, date = now)
            val repo = FakeExpenseRepository(listOf(toKeep, toDelete))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteExpense("delete-me")
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(listOf(toKeep), content.expenses)
            assertNull(repo.getExpense("delete-me").getOrThrow())
            assertEquals(toKeep, repo.getExpense("keep").getOrThrow())

            job.cancel()
        }

    @Test
    fun onDeleteExpense_unknownId_doesNotThrow_andLeavesStateUnchanged() =
        runTest(testDispatcher) {
            val toKeep = expense(id = "keep", amountCents = 100L, date = now)
            val repo = FakeExpenseRepository(listOf(toKeep))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteExpense("does-not-exist")
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(listOf(toKeep), content.expenses)

            job.cancel()
        }

    @Test
    fun onDeleteExpense_knownId_emitsDeletedExpenseOnUndoEvent() =
        runTest(testDispatcher) {
            val toDelete = expense(id = "delete-me", name = "Groceries", amountCents = 200L, date = now)
            val repo = FakeExpenseRepository(listOf(toDelete))
            val vm = viewModel(repo)

            val undoEvents = mutableListOf<Expense>()
            val undoJob = launch { vm.undoEvent.collect { undoEvents.add(it) } }
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteExpense("delete-me")
            advanceUntilIdle()

            assertEquals(listOf(toDelete), undoEvents)

            job.cancel()
            undoJob.cancel()
        }

    @Test
    fun onDeleteExpense_unknownId_doesNotEmitOnUndoEvent() =
        runTest(testDispatcher) {
            val toKeep = expense(id = "keep", amountCents = 100L, date = now)
            val repo = FakeExpenseRepository(listOf(toKeep))
            val vm = viewModel(repo)

            val undoEvents = mutableListOf<Expense>()
            val undoJob = launch { vm.undoEvent.collect { undoEvents.add(it) } }
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteExpense("does-not-exist")
            advanceUntilIdle()

            assertTrue(undoEvents.isEmpty())

            job.cancel()
            undoJob.cancel()
        }

    @Test
    fun onUndoDelete_reAddsExpense_soItReappearsInUiState() =
        runTest(testDispatcher) {
            val toKeep = expense(id = "keep", amountCents = 100L, date = now)
            val toRestore = expense(id = "restore-me", name = "Coffee", amountCents = 500L, date = now)
            val repo = FakeExpenseRepository(listOf(toKeep))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onUndoDelete(toRestore)
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertTrue(content.expenses.contains(toRestore))
            assertEquals(toRestore, repo.getExpense("restore-me").getOrThrow())

            job.cancel()
        }

    @Test
    fun deleteThenUndo_roundTrip_expenseReappearsInUiState() =
        runTest(testDispatcher) {
            val toKeep = expense(id = "keep", amountCents = 100L, date = now)
            val toDelete = expense(id = "delete-me", name = "Groceries", amountCents = 200L, date = now)
            val repo = FakeExpenseRepository(listOf(toKeep, toDelete))
            val vm = viewModel(repo)

            val undoEvents = mutableListOf<Expense>()
            val undoJob = launch { vm.undoEvent.collect { undoEvents.add(it) } }
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteExpense("delete-me")
            advanceUntilIdle()

            val afterDelete = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(listOf(toKeep), afterDelete.expenses)
            assertEquals(1, undoEvents.size)

            vm.onUndoDelete(undoEvents.single())
            advanceUntilIdle()

            val afterUndo = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(2, afterUndo.expenses.size)
            assertTrue(afterUndo.expenses.contains(toDelete))
            assertTrue(afterUndo.expenses.contains(toKeep))

            job.cancel()
            undoJob.cancel()
        }

    // ---- observeExpenses failure -> Error state ----

    @Test
    fun observeExpensesFailure_beforeFirstCollection_surfacesErrorState_withUnderlyingMessage() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = IllegalStateException("Firestore unavailable")
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(ExpenseListUiState.Error(genericErrorText), vm.uiState.value)

            job.cancel()
        }

    @Test
    fun observeExpensesFailure_withNullExceptionMessage_fallsBackToDefaultMessage() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = RuntimeException()
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(
                ExpenseListUiState.Error(genericErrorText),
                vm.uiState.value,
            )

            job.cancel()
        }

    @Test
    fun onRetry_afterClearingError_transitionsFromErrorBackToContent() =
        runTest(testDispatcher) {
            val existing = expense(id = "e1", amountCents = 1_000L, date = now.minus(1, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(existing))
            repo.nextObserveExpensesError = IllegalStateException("Firestore unavailable")
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(ExpenseListUiState.Error(genericErrorText), vm.uiState.value)

            // Clear the error on the underlying fake, then retry. Since period/query/sortOption
            // haven't changed, only the retry-trigger bump forces flatMapLatest to re-subscribe --
            // proving onRetry() isn't a no-op swallowed by the combined key's equality-based dedup.
            repo.nextObserveExpensesError = null
            vm.onRetry()
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(listOf(existing), content.expenses)

            job.cancel()
        }

    @Test
    fun onRetry_doesNotResetCurrentQuerySortOptionOrPeriodSelections() =
        runTest(testDispatcher) {
            val coffee =
                expense(id = "1", name = "Morning Coffee", amountCents = 500L, date = now.minus(2, ChronoUnit.DAYS))
            val coffeeOld =
                expense(id = "2", name = "Old Coffee", amountCents = 100L, date = now.minus(20, ChronoUnit.DAYS))
            val groceries =
                expense(id = "3", name = "Groceries", amountCents = 3_000L, date = now.minus(2, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(coffee, coffeeOld, groceries))
            val vm = viewModel(repo, initialPeriod = "rolling:THREE_MONTHS")

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("coffee")
            vm.onSortSelected(SortOption.AMOUNT_ASC)
            vm.onPeriodSelected(Period.CurrentMonth)
            advanceUntilIdle()

            val beforeRetry = vm.uiState.value as ExpenseListUiState.Content
            assertEquals("coffee", beforeRetry.query)
            assertEquals(SortOption.AMOUNT_ASC, beforeRetry.sortOption)
            assertEquals(Period.CurrentMonth, beforeRetry.period)
            // within the current month: only "coffee" (2 days ago) matches the query; coffeeOld
            // (20 days ago, i.e. September) and groceries (doesn't match query) are excluded.
            assertEquals(listOf(coffee), beforeRetry.expenses)

            vm.onRetry()
            advanceUntilIdle()

            val afterRetry = vm.uiState.value as ExpenseListUiState.Content
            assertEquals("coffee", afterRetry.query)
            assertEquals(SortOption.AMOUNT_ASC, afterRetry.sortOption)
            assertEquals(Period.CurrentMonth, afterRetry.period)
            assertEquals(listOf(coffee), afterRetry.expenses)

            job.cancel()
        }

    // ---- process death: query/sortOption now live in SavedStateHandle, same as the period selection ----

    @Test
    fun processDeathSimulation_queryAndSortOption_surviveRecreationViaSameSavedStateHandle() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val savedStateHandle = SavedStateHandle(mapOf("period" to "current"))
            val original = ExpenseListViewModel(repo, FakeCategoryRepository(), savedStateHandle, clock)

            val originalJob = launch { original.uiState.collect {} }
            advanceUntilIdle()
            original.onQueryChanged("foo")
            original.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()
            originalJob.cancel()

            // Simulate process death + recreation: construct a *new* ViewModel instance with the
            // *same* SavedStateHandle object, exactly as the real Android framework hands back a
            // restored SavedStateHandle after process death, instead of a fresh empty one.
            val recreated = ExpenseListViewModel(repo, FakeCategoryRepository(), savedStateHandle, clock)
            val recreatedJob = launch { recreated.uiState.collect {} }
            advanceUntilIdle()

            val content = recreated.uiState.value as ExpenseListUiState.Content
            assertEquals("foo", content.query)
            assertEquals(SortOption.AMOUNT_ASC, content.sortOption)
            assertEquals(Period.CurrentMonth, content.period)

            recreatedJob.cancel()
        }

    @Test
    fun processDeathSimulation_defaultQueryAndSortOption_whenNeverChanged_remainAtDefaults() =
        runTest(testDispatcher) {
            // Regression guard for the opposite direction: a fresh SavedStateHandle that never had
            // onQueryChanged/onSortSelected called against it must still default to ""/DATE_DESC,
            // not crash or throw on the getStateFlow(key, default) lookup.
            val repo = FakeExpenseRepository()
            val savedStateHandle = SavedStateHandle(mapOf("period" to "current"))
            val vm = ExpenseListViewModel(repo, FakeCategoryRepository(), savedStateHandle, clock)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals("", content.query)
            assertEquals(SortOption.DATE_DESC, content.sortOption)

            job.cancel()
        }
}
