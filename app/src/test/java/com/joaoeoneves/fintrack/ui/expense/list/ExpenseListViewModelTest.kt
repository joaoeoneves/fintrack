package com.joaoeoneves.fintrack.ui.expense.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.testutil.FakeStringContext
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
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [ExpenseListViewModel].
 *
 * [ExpenseListViewModel]'s constructor eagerly calls `savedStateHandle.toRoute<ExpenseList>()`,
 * and `ExpenseList.timeRange` has no default value, so *any* construction (not just edit-mode-like
 * paths) requires a populated [SavedStateHandle]. Decoding that requires routing the stored enum
 * value through `androidx.core.os.BundleKt.bundleOf` -> a real `android.os.Bundle`, which isn't
 * stubbed on a plain JVM unit test ("Method putCharSequence in android.os.Bundle not mocked").
 * Running under [RobolectricTestRunner] provides a real (shadowed) `Bundle` implementation, which
 * resolves this: `SavedStateHandle(mapOf("timeRange" to TimeRange.X))` round-trips correctly
 * through `toRoute<ExpenseList>()`, verified by construction succeeding and
 * `uiState`/`onTimeRangeSelected` reflecting the seeded/subsequent value.
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

    private val now = Instant.now()

    private fun expense(
        id: String,
        name: String = id,
        amountCents: Long,
        category: ExpenseCategory = ExpenseCategory.SHOPPING,
        date: Instant,
        note: String? = null,
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    private fun viewModel(
        repository: com.joaoeoneves.fintrack.domain.repository.ExpenseRepository,
        initialTimeRange: TimeRange = TimeRange.ONE_MONTH,
    ) = ExpenseListViewModel(repository, SavedStateHandle(mapOf("timeRange" to initialTimeRange)))

    // ---- construction / initial timeRange from route ----

    @Test
    fun construction_seedsTimeRangeFromRoute() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val vm = viewModel(repo, initialTimeRange = TimeRange.ONE_WEEK)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(TimeRange.ONE_WEEK, content.timeRange)

            job.cancel()
        }

    @Test
    fun construction_withEachTimeRangeValue_decodesCorrectly() =
        runTest(testDispatcher) {
            for (range in TimeRange.entries) {
                val repo = FakeExpenseRepository()
                val vm = viewModel(repo, initialTimeRange = range)
                val job = launch { vm.uiState.collect {} }
                advanceUntilIdle()

                val content = vm.uiState.value as ExpenseListUiState.Content
                assertEquals(range, content.timeRange)

                job.cancel()
            }
        }

    // ---- onTimeRangeSelected ----

    @Test
    fun onTimeRangeSelected_changesFilterAndReQueriesRepository() =
        runTest(testDispatcher) {
            val withinWeek = expense(id = "within-week", amountCents = 1_000L, date = now.minus(2, ChronoUnit.DAYS))
            val withinMonthNotWeek = expense(id = "within-month", amountCents = 2_000L, date = now.minus(20, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(withinWeek, withinMonthNotWeek))
            val vm = viewModel(repo, initialTimeRange = TimeRange.ONE_MONTH)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val monthContent = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(2, monthContent.expenses.size)

            vm.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val weekContent = vm.uiState.value as ExpenseListUiState.Content
            assertEquals(TimeRange.ONE_WEEK, weekContent.timeRange)
            assertEquals(listOf(withinWeek), weekContent.expenses)

            job.cancel()
        }

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

            assertEquals(ExpenseListUiState.Error("Firestore unavailable"), vm.uiState.value)

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

            assertEquals(ExpenseListUiState.Error("Something went wrong"), vm.uiState.value)

            job.cancel()
        }

    @Test
    fun observeExpensesFailure_withNullExceptionMessage_andRealContext_usesContextGetString_notHardcodedFallback() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = RuntimeException()
            val context = FakeStringContext(R.string.error_generic_fallback, "translated generic error")
            val vm =
                ExpenseListViewModel(
                    repo,
                    SavedStateHandle(mapOf("timeRange" to TimeRange.ONE_MONTH)),
                    context,
                )

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(ExpenseListUiState.Error("translated generic error"), vm.uiState.value)

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
            assertEquals(ExpenseListUiState.Error("Firestore unavailable"), vm.uiState.value)

            // Clear the error on the underlying fake, then retry. Since timeRange/query/sortOption
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
    fun onRetry_doesNotResetCurrentQuerySortOptionOrTimeRangeSelections() =
        runTest(testDispatcher) {
            val coffee = expense(id = "1", name = "Morning Coffee", amountCents = 500L, date = now.minus(2, ChronoUnit.DAYS))
            val coffeeOld = expense(id = "2", name = "Old Coffee", amountCents = 100L, date = now.minus(20, ChronoUnit.DAYS))
            val groceries = expense(id = "3", name = "Groceries", amountCents = 3_000L, date = now.minus(2, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(coffee, coffeeOld, groceries))
            val vm = viewModel(repo, initialTimeRange = TimeRange.ONE_MONTH)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("coffee")
            vm.onSortSelected(SortOption.AMOUNT_ASC)
            vm.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val beforeRetry = vm.uiState.value as ExpenseListUiState.Content
            assertEquals("coffee", beforeRetry.query)
            assertEquals(SortOption.AMOUNT_ASC, beforeRetry.sortOption)
            assertEquals(TimeRange.ONE_WEEK, beforeRetry.timeRange)
            // within one week: only "coffee" (2 days ago) matches the query; coffeeOld (20 days ago)
            // and groceries (doesn't match query) are excluded.
            assertEquals(listOf(coffee), beforeRetry.expenses)

            vm.onRetry()
            advanceUntilIdle()

            val afterRetry = vm.uiState.value as ExpenseListUiState.Content
            assertEquals("coffee", afterRetry.query)
            assertEquals(SortOption.AMOUNT_ASC, afterRetry.sortOption)
            assertEquals(TimeRange.ONE_WEEK, afterRetry.timeRange)
            assertEquals(listOf(coffee), afterRetry.expenses)

            job.cancel()
        }

    // ---- process death: query/sortOption now live in SavedStateHandle, same as timeRange already did ----

    @Test
    fun processDeathSimulation_queryAndSortOption_surviveRecreationViaSameSavedStateHandle() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val savedStateHandle = SavedStateHandle(mapOf("timeRange" to TimeRange.ONE_MONTH))
            val original = ExpenseListViewModel(repo, savedStateHandle)

            val originalJob = launch { original.uiState.collect {} }
            advanceUntilIdle()
            original.onQueryChanged("foo")
            original.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()
            originalJob.cancel()

            // Simulate process death + recreation: construct a *new* ViewModel instance with the
            // *same* SavedStateHandle object, exactly as the real Android framework hands back a
            // restored SavedStateHandle after process death, instead of a fresh empty one.
            val recreated = ExpenseListViewModel(repo, savedStateHandle)
            val recreatedJob = launch { recreated.uiState.collect {} }
            advanceUntilIdle()

            val content = recreated.uiState.value as ExpenseListUiState.Content
            assertEquals("foo", content.query)
            assertEquals(SortOption.AMOUNT_ASC, content.sortOption)
            assertEquals(TimeRange.ONE_MONTH, content.timeRange)

            recreatedJob.cancel()
        }

    @Test
    fun processDeathSimulation_defaultQueryAndSortOption_whenNeverChanged_remainAtDefaults() =
        runTest(testDispatcher) {
            // Regression guard for the opposite direction: a fresh SavedStateHandle that never had
            // onQueryChanged/onSortSelected called against it must still default to ""/DATE_DESC,
            // not crash or throw on the getStateFlow(key, default) lookup.
            val repo = FakeExpenseRepository()
            val savedStateHandle = SavedStateHandle(mapOf("timeRange" to TimeRange.ONE_MONTH))
            val vm = ExpenseListViewModel(repo, savedStateHandle)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as ExpenseListUiState.Content
            assertEquals("", content.query)
            assertEquals(SortOption.DATE_DESC, content.sortOption)

            job.cancel()
        }
}
