package com.joaoeoneves.fintrack.ui.income.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.testutil.genericErrorText
import com.joaoeoneves.fintrack.ui.expense.list.SortOption
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
 * Unit tests for [IncomeListViewModel], mirroring [com.joaoeoneves.fintrack.ui.expense.list.ExpenseListViewModelTest]'s
 * coverage/conventions, including query/sort support.
 *
 * Like `ExpenseListViewModel`, the constructor eagerly calls
 * `savedStateHandle.toRoute<IncomeList>()`, which requires a real (shadowed) `android.os.Bundle`
 * to decode the `period` token arg -- hence [RobolectricTestRunner]. Period-specific behavior lives in
 * [IncomeListViewModelPeriodTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IncomeListViewModelTest {
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

    private fun income(
        id: String,
        source: String = id,
        amountCents: Long,
        date: Instant,
        note: String? = null,
    ) = Income(id = id, source = source, amountCents = amountCents, date = date, note = note)

    private fun viewModel(
        repository: IncomeRepository,
        initialPeriod: String = "current",
    ) = IncomeListViewModel(repository, SavedStateHandle(mapOf("period" to initialPeriod)), clock)

    // ---- search / query ----

    @Test
    fun onQueryChanged_caseInsensitivePartialMatch_filtersIncome() =
        runTest(testDispatcher) {
            val salary = income(id = "1", source = "Salary", amountCents = 500_000L, date = now)
            val freelance = income(id = "2", source = "Freelance", amountCents = 3_000L, date = now)
            val repo = FakeIncomeRepository(listOf(salary, freelance))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("SAL")
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(salary), content.income)
            assertEquals("SAL", content.query)

            job.cancel()
        }

    @Test
    fun onQueryChanged_partialMatch_matchesSubstringAnywhereInSource() =
        runTest(testDispatcher) {
            val income1 = income(id = "1", source = "Monthly Salary Payment", amountCents = 500L, date = now)
            val repo = FakeIncomeRepository(listOf(income1))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("y Sal")
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(income1), content.income)

            job.cancel()
        }

    @Test
    fun onQueryChanged_noMatch_producesEmptyList() =
        runTest(testDispatcher) {
            val salary = income(id = "1", source = "Salary", amountCents = 500L, date = now)
            val repo = FakeIncomeRepository(listOf(salary))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("nonexistent-xyz")
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertTrue(content.income.isEmpty())

            job.cancel()
        }

    @Test
    fun onQueryChanged_blankQuery_isEquivalentToNoFilter() =
        runTest(testDispatcher) {
            val salary = income(id = "1", source = "Salary", amountCents = 500L, date = now)
            val freelance = income(id = "2", source = "Freelance", amountCents = 3_000L, date = now)
            val repo = FakeIncomeRepository(listOf(salary, freelance))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("salary")
            advanceUntilIdle()
            vm.onQueryChanged("   ")
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(2, content.income.size)

            job.cancel()
        }

    // ---- sorting ----

    @Test
    fun onSortSelected_dateDesc_isDefault_andSortsNewestFirst() =
        runTest(testDispatcher) {
            val older = income(id = "older", amountCents = 100L, date = now.minus(5, ChronoUnit.DAYS))
            val newer = income(id = "newer", amountCents = 200L, date = now.minus(1, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(older, newer))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(SortOption.DATE_DESC, content.sortOption)
            assertEquals(listOf(newer, older), content.income)

            job.cancel()
        }

    @Test
    fun onSortSelected_dateAsc_sortsOldestFirst() =
        runTest(testDispatcher) {
            val older = income(id = "older", amountCents = 100L, date = now.minus(5, ChronoUnit.DAYS))
            val newer = income(id = "newer", amountCents = 200L, date = now.minus(1, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(older, newer))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onSortSelected(SortOption.DATE_ASC)
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(SortOption.DATE_ASC, content.sortOption)
            assertEquals(listOf(older, newer), content.income)

            job.cancel()
        }

    @Test
    fun onSortSelected_amountDesc_sortsHighestFirst() =
        runTest(testDispatcher) {
            val small = income(id = "small", amountCents = 100L, date = now.minus(1, ChronoUnit.DAYS))
            val large = income(id = "large", amountCents = 500_000L, date = now.minus(5, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(small, large))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onSortSelected(SortOption.AMOUNT_DESC)
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(SortOption.AMOUNT_DESC, content.sortOption)
            assertEquals(listOf(large, small), content.income)

            job.cancel()
        }

    @Test
    fun onSortSelected_amountAsc_sortsLowestFirst() =
        runTest(testDispatcher) {
            val small = income(id = "small", amountCents = 100L, date = now.minus(1, ChronoUnit.DAYS))
            val large = income(id = "large", amountCents = 500_000L, date = now.minus(5, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(small, large))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(SortOption.AMOUNT_ASC, content.sortOption)
            assertEquals(listOf(small, large), content.income)

            job.cancel()
        }

    @Test
    fun sorting_appliesAfterQueryFilter() =
        runTest(testDispatcher) {
            val salarySmall = income(id = "s1", source = "Salary small", amountCents = 300L, date = now)
            val salaryLarge = income(id = "s2", source = "Salary large", amountCents = 700L, date = now)
            val bonus = income(id = "b1", source = "Bonus", amountCents = 5_000L, date = now)
            val repo = FakeIncomeRepository(listOf(salarySmall, salaryLarge, bonus))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("salary")
            vm.onSortSelected(SortOption.AMOUNT_DESC)
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(salaryLarge, salarySmall), content.income)

            job.cancel()
        }

    // ---- delete ----

    @Test
    fun onDeleteIncome_removesIncomeFromRepository_andUiState() =
        runTest(testDispatcher) {
            val toKeep = income(id = "keep", amountCents = 100L, date = now)
            val toDelete = income(id = "delete-me", amountCents = 200L, date = now)
            val repo = FakeIncomeRepository(listOf(toKeep, toDelete))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteIncome("delete-me")
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(toKeep), content.income)
            assertNull(repo.getIncome("delete-me").getOrThrow())
            assertEquals(toKeep, repo.getIncome("keep").getOrThrow())

            job.cancel()
        }

    @Test
    fun onDeleteIncome_unknownId_doesNotThrow_andLeavesStateUnchanged() =
        runTest(testDispatcher) {
            val toKeep = income(id = "keep", amountCents = 100L, date = now)
            val repo = FakeIncomeRepository(listOf(toKeep))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteIncome("does-not-exist")
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(toKeep), content.income)

            job.cancel()
        }

    @Test
    fun onDeleteIncome_knownId_emitsDeletedIncomeOnUndoEvent() =
        runTest(testDispatcher) {
            val toDelete = income(id = "delete-me", source = "Freelance", amountCents = 200L, date = now)
            val repo = FakeIncomeRepository(listOf(toDelete))
            val vm = viewModel(repo)

            val undoEvents = mutableListOf<Income>()
            val undoJob = launch { vm.undoEvent.collect { undoEvents.add(it) } }
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteIncome("delete-me")
            advanceUntilIdle()

            assertEquals(listOf(toDelete), undoEvents)

            job.cancel()
            undoJob.cancel()
        }

    @Test
    fun onDeleteIncome_unknownId_doesNotEmitOnUndoEvent() =
        runTest(testDispatcher) {
            val toKeep = income(id = "keep", amountCents = 100L, date = now)
            val repo = FakeIncomeRepository(listOf(toKeep))
            val vm = viewModel(repo)

            val undoEvents = mutableListOf<Income>()
            val undoJob = launch { vm.undoEvent.collect { undoEvents.add(it) } }
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteIncome("does-not-exist")
            advanceUntilIdle()

            assertTrue(undoEvents.isEmpty())

            job.cancel()
            undoJob.cancel()
        }

    @Test
    fun onUndoDelete_reAddsIncome_soItReappearsInUiState() =
        runTest(testDispatcher) {
            val toKeep = income(id = "keep", amountCents = 100L, date = now)
            val toRestore = income(id = "restore-me", source = "Bonus", amountCents = 500L, date = now)
            val repo = FakeIncomeRepository(listOf(toKeep))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onUndoDelete(toRestore)
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertTrue(content.income.contains(toRestore))
            assertEquals(toRestore, repo.getIncome("restore-me").getOrThrow())

            job.cancel()
        }

    @Test
    fun deleteThenUndo_roundTrip_incomeReappearsInUiState() =
        runTest(testDispatcher) {
            val toKeep = income(id = "keep", amountCents = 100L, date = now)
            val toDelete = income(id = "delete-me", source = "Freelance", amountCents = 200L, date = now)
            val repo = FakeIncomeRepository(listOf(toKeep, toDelete))
            val vm = viewModel(repo)

            val undoEvents = mutableListOf<Income>()
            val undoJob = launch { vm.undoEvent.collect { undoEvents.add(it) } }
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onDeleteIncome("delete-me")
            advanceUntilIdle()

            val afterDelete = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(toKeep), afterDelete.income)
            assertEquals(1, undoEvents.size)

            vm.onUndoDelete(undoEvents.single())
            advanceUntilIdle()

            val afterUndo = vm.uiState.value as IncomeListUiState.Content
            assertEquals(2, afterUndo.income.size)
            assertTrue(afterUndo.income.contains(toDelete))
            assertTrue(afterUndo.income.contains(toKeep))

            job.cancel()
            undoJob.cancel()
        }

    // ---- observeIncome failure -> Error state ----

    @Test
    fun observeIncomeFailure_beforeFirstCollection_surfacesErrorState_withUnderlyingMessage() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            repo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(IncomeListUiState.Error(genericErrorText), vm.uiState.value)

            job.cancel()
        }

    @Test
    fun observeIncomeFailure_withNullExceptionMessage_fallsBackToDefaultMessage() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            repo.nextObserveIncomeError = RuntimeException()
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(
                IncomeListUiState.Error(genericErrorText),
                vm.uiState.value,
            )

            job.cancel()
        }

    @Test
    fun onRetry_afterClearingError_transitionsFromErrorBackToContent() =
        runTest(testDispatcher) {
            val existing = income(id = "i1", amountCents = 1_000L, date = now.minus(1, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(existing))
            repo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(IncomeListUiState.Error(genericErrorText), vm.uiState.value)

            // Clear the error on the underlying fake, then retry. Since the period itself hasn't
            // changed, only the retry-trigger bump forces flatMapLatest to re-subscribe -- proving
            // onRetry() isn't a no-op swallowed by StateFlow's equality-based dedup.
            repo.nextObserveIncomeError = null
            vm.onRetry()
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(existing), content.income)

            job.cancel()
        }

    @Test
    fun onRetry_doesNotResetCurrentQuerySortOptionOrPeriodSelections() =
        runTest(testDispatcher) {
            val salary =
                income(id = "1", source = "Monthly Salary", amountCents = 500L, date = now.minus(2, ChronoUnit.DAYS))
            val salaryOld =
                income(id = "2", source = "Old Salary", amountCents = 100L, date = now.minus(20, ChronoUnit.DAYS))
            val bonus = income(id = "3", source = "Bonus", amountCents = 3_000L, date = now.minus(2, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(salary, salaryOld, bonus))
            val vm = viewModel(repo, initialPeriod = "rolling:THREE_MONTHS")

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onQueryChanged("salary")
            vm.onSortSelected(SortOption.AMOUNT_ASC)
            vm.onPeriodSelected(Period.CurrentMonth)
            advanceUntilIdle()

            val beforeRetry = vm.uiState.value as IncomeListUiState.Content
            assertEquals("salary", beforeRetry.query)
            assertEquals(SortOption.AMOUNT_ASC, beforeRetry.sortOption)
            assertEquals(Period.CurrentMonth, beforeRetry.period)
            // within the current month: only "salary" (2 days ago) matches the query; salaryOld
            // (20 days ago, i.e. September) and bonus (doesn't match query) are excluded.
            assertEquals(listOf(salary), beforeRetry.income)

            vm.onRetry()
            advanceUntilIdle()

            val afterRetry = vm.uiState.value as IncomeListUiState.Content
            assertEquals("salary", afterRetry.query)
            assertEquals(SortOption.AMOUNT_ASC, afterRetry.sortOption)
            assertEquals(Period.CurrentMonth, afterRetry.period)
            assertEquals(listOf(salary), afterRetry.income)

            job.cancel()
        }

    // ---- process death: query/sortOption live in SavedStateHandle, same as the period selection ----

    @Test
    fun processDeathSimulation_queryAndSortOption_surviveRecreationViaSameSavedStateHandle() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val savedStateHandle = SavedStateHandle(mapOf("period" to "current"))
            val original = IncomeListViewModel(repo, savedStateHandle, clock)

            val originalJob = launch { original.uiState.collect {} }
            advanceUntilIdle()
            original.onQueryChanged("foo")
            original.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()
            originalJob.cancel()

            // Simulate process death + recreation: construct a *new* ViewModel instance with the
            // *same* SavedStateHandle object, exactly as the real Android framework hands back a
            // restored SavedStateHandle after process death, instead of a fresh empty one.
            val recreated = IncomeListViewModel(repo, savedStateHandle, clock)
            val recreatedJob = launch { recreated.uiState.collect {} }
            advanceUntilIdle()

            val content = recreated.uiState.value as IncomeListUiState.Content
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
            val repo = FakeIncomeRepository()
            val savedStateHandle = SavedStateHandle(mapOf("period" to "current"))
            val vm = IncomeListViewModel(repo, savedStateHandle, clock)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals("", content.query)
            assertEquals(SortOption.DATE_DESC, content.sortOption)

            job.cancel()
        }
}
