package com.joaoeoneves.fintrack.ui.income.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
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
 * Unit tests for [IncomeListViewModel], mirroring [com.joaoeoneves.fintrack.ui.expense.list.ExpenseListViewModelTest]'s
 * coverage/conventions (minus query/sort, which [IncomeListViewModel] doesn't have).
 *
 * Like `ExpenseListViewModel`, the constructor eagerly calls
 * `savedStateHandle.toRoute<IncomeList>()`, which requires a real (shadowed) `android.os.Bundle`
 * to decode the `TimeRange` enum arg -- hence [RobolectricTestRunner].
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

    private val now = Instant.now()

    private fun income(
        id: String,
        source: String = id,
        amountCents: Long,
        date: Instant,
        note: String? = null,
    ) = Income(id = id, source = source, amountCents = amountCents, date = date, note = note)

    private fun viewModel(
        repository: IncomeRepository,
        initialTimeRange: TimeRange = TimeRange.ONE_MONTH,
    ) = IncomeListViewModel(repository, SavedStateHandle(mapOf("timeRange" to initialTimeRange)))

    // ---- construction / initial timeRange from route ----

    @Test
    fun construction_seedsTimeRangeFromRoute() =
        runTest(testDispatcher) {
            val repo = FakeIncomeRepository()
            val vm = viewModel(repo, initialTimeRange = TimeRange.ONE_WEEK)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(TimeRange.ONE_WEEK, content.timeRange)

            job.cancel()
        }

    @Test
    fun construction_withEachTimeRangeValue_decodesCorrectly() =
        runTest(testDispatcher) {
            for (range in TimeRange.entries) {
                val repo = FakeIncomeRepository()
                val vm = viewModel(repo, initialTimeRange = range)
                val job = launch { vm.uiState.collect {} }
                advanceUntilIdle()

                val content = vm.uiState.value as IncomeListUiState.Content
                assertEquals(range, content.timeRange)

                job.cancel()
            }
        }

    // ---- onTimeRangeSelected ----

    @Test
    fun onTimeRangeSelected_changesFilterAndReQueriesRepository() =
        runTest(testDispatcher) {
            val withinWeek = income(id = "within-week", amountCents = 1_000L, date = now.minus(2, ChronoUnit.DAYS))
            val withinMonthNotWeek =
                income(id = "within-month", amountCents = 2_000L, date = now.minus(20, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(withinWeek, withinMonthNotWeek))
            val vm = viewModel(repo, initialTimeRange = TimeRange.ONE_MONTH)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val monthContent = vm.uiState.value as IncomeListUiState.Content
            assertEquals(2, monthContent.income.size)

            vm.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val weekContent = vm.uiState.value as IncomeListUiState.Content
            assertEquals(TimeRange.ONE_WEEK, weekContent.timeRange)
            assertEquals(listOf(withinWeek), weekContent.income)

            job.cancel()
        }

    @Test
    fun onTimeRangeSelected_toRangeWithNoIncome_producesEmptyContent_notBrokenState() =
        runTest(testDispatcher) {
            val onlyOldIncome = income(id = "old", amountCents = 1_000L, date = now.minus(200, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(onlyOldIncome))
            val vm = viewModel(repo, initialTimeRange = TimeRange.ONE_MONTH)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertTrue(content.income.isEmpty())

            job.cancel()
        }

    // ---- sorting: income is always sorted date-descending ----

    @Test
    fun uiState_sortsIncomeByDateDescending() =
        runTest(testDispatcher) {
            val older = income(id = "older", amountCents = 100L, date = now.minus(5, ChronoUnit.DAYS))
            val newer = income(id = "newer", amountCents = 200L, date = now.minus(1, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(older, newer))
            val vm = viewModel(repo)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val content = vm.uiState.value as IncomeListUiState.Content
            assertEquals(listOf(newer, older), content.income)

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
            assertNull(repo.getIncome("delete-me"))
            assertEquals(toKeep, repo.getIncome("keep"))

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
            assertEquals(toRestore, repo.getIncome("restore-me"))

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

            assertEquals(IncomeListUiState.Error("Firestore unavailable"), vm.uiState.value)

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

            assertEquals(IncomeListUiState.Error("Something went wrong"), vm.uiState.value)

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
            assertEquals(IncomeListUiState.Error("Firestore unavailable"), vm.uiState.value)

            // Clear the error on the underlying fake, then retry. Since timeRange itself hasn't
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
    fun onRetry_doesNotResetCurrentTimeRangeSelection() =
        runTest(testDispatcher) {
            val withinWeek = income(id = "within-week", amountCents = 1_000L, date = now.minus(2, ChronoUnit.DAYS))
            val withinMonthNotWeek =
                income(id = "within-month", amountCents = 2_000L, date = now.minus(20, ChronoUnit.DAYS))
            val repo = FakeIncomeRepository(listOf(withinWeek, withinMonthNotWeek))
            val vm = viewModel(repo, initialTimeRange = TimeRange.ONE_MONTH)

            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val beforeRetry = vm.uiState.value as IncomeListUiState.Content
            assertEquals(TimeRange.ONE_WEEK, beforeRetry.timeRange)
            assertEquals(listOf(withinWeek), beforeRetry.income)

            vm.onRetry()
            advanceUntilIdle()

            val afterRetry = vm.uiState.value as IncomeListUiState.Content
            assertEquals(TimeRange.ONE_WEEK, afterRetry.timeRange)
            assertEquals(listOf(withinWeek), afterRetry.income)

            job.cancel()
        }
}
