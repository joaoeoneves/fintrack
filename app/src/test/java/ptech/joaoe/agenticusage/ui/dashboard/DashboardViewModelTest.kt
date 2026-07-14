package ptech.joaoe.agenticusage.ui.dashboard

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ptech.joaoe.agenticusage.data.FakeExpenseRepository
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.model.TimeRange
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [DashboardViewModel], backed by [FakeExpenseRepository]. All expense fixtures
 * are anchored relative to a real `Instant.now()` (as the ViewModel itself uses `Instant.now()`
 * internally to compute its query range) with margins comfortably inside/outside each
 * [TimeRange] bucket, to avoid boundary flakiness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
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
        category: ExpenseCategory,
        date: Instant,
        note: String? = null,
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    @Test
    fun initialState_isLoading_beforeCollectionStarts() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = DashboardViewModel(repo)

            // Before advanceUntilIdle/collection, the StateFlow built via stateIn(WhileSubscribed)
            // has not yet started collecting upstream, so it should still report its initialValue.
            assertEquals(DashboardUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun emptyRepository_producesContentWithZeroTotalsAndAllCategoriesPresent() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_MONTH, content.timeRange)
            assertTrue(content.expenses.isEmpty())
            assertTrue(content.recentExpenses.isEmpty())
            assertEquals(0L, content.totalCents)
            assertEquals(ExpenseCategory.entries.size, content.categoryTotals.size)
            assertTrue(content.categoryTotals.all { it.totalCents == 0L })
            assertEquals(ExpenseCategory.entries.toSet(), content.categoryTotals.map { it.category }.toSet())

            job.cancel()
        }

    @Test
    fun defaultTimeRange_isOneMonth_andFiltersOutExpensesOutsideIt() =
        runTest(testDispatcher) {
            val insideOneMonth =
                expense(
                    id = "inside",
                    amountCents = 1_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(3, ChronoUnit.DAYS),
                )
            val outsideOneMonth =
                expense(
                    id = "outside",
                    amountCents = 5_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(40, ChronoUnit.DAYS),
                )
            val repo = FakeExpenseRepository(listOf(insideOneMonth, outsideOneMonth))
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(listOf(insideOneMonth), content.expenses)
            assertEquals(1_000L, content.totalCents)

            job.cancel()
        }

    @Test
    fun categoryTotals_sumCorrectlyPerCategory_acrossAllFourCategories() =
        runTest(testDispatcher) {
            val shopping1 =
                expense(id = "s1", amountCents = 1_000L, category = ExpenseCategory.SHOPPING, date = now.minus(1, ChronoUnit.DAYS))
            val shopping2 =
                expense(id = "s2", amountCents = 500L, category = ExpenseCategory.SHOPPING, date = now.minus(2, ChronoUnit.DAYS))
            val transfer =
                expense(id = "t1", amountCents = 2_000L, category = ExpenseCategory.TRANSFER, date = now.minus(3, ChronoUnit.DAYS))
            val investment =
                expense(id = "i1", amountCents = 3_000L, category = ExpenseCategory.INVESTMENTS, date = now.minus(4, ChronoUnit.DAYS))
            val recurring =
                expense(id = "r1", amountCents = 4_000L, category = ExpenseCategory.RECURRING, date = now.minus(5, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(shopping1, shopping2, transfer, investment, recurring))
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            val totalsByCategory = content.categoryTotals.associate { it.category to it.totalCents }
            assertEquals(1_500L, totalsByCategory.getValue(ExpenseCategory.SHOPPING))
            assertEquals(2_000L, totalsByCategory.getValue(ExpenseCategory.TRANSFER))
            assertEquals(3_000L, totalsByCategory.getValue(ExpenseCategory.INVESTMENTS))
            assertEquals(4_000L, totalsByCategory.getValue(ExpenseCategory.RECURRING))
            assertEquals(10_500L, content.totalCents)

            job.cancel()
        }

    @Test
    fun recentExpenses_returnsTop5ByDateDescending_whenMoreThan5InRange() =
        runTest(testDispatcher) {
            // 7 expenses inside the range, at distinct days, in scrambled insertion order.
            val e1 = expense(id = "e1", amountCents = 100L, category = ExpenseCategory.SHOPPING, date = now.minus(1, ChronoUnit.DAYS))
            val e2 = expense(id = "e2", amountCents = 100L, category = ExpenseCategory.SHOPPING, date = now.minus(2, ChronoUnit.DAYS))
            val e3 = expense(id = "e3", amountCents = 100L, category = ExpenseCategory.SHOPPING, date = now.minus(3, ChronoUnit.DAYS))
            val e4 = expense(id = "e4", amountCents = 100L, category = ExpenseCategory.SHOPPING, date = now.minus(4, ChronoUnit.DAYS))
            val e5 = expense(id = "e5", amountCents = 100L, category = ExpenseCategory.SHOPPING, date = now.minus(5, ChronoUnit.DAYS))
            val e6 = expense(id = "e6", amountCents = 100L, category = ExpenseCategory.SHOPPING, date = now.minus(6, ChronoUnit.DAYS))
            val e7 = expense(id = "e7", amountCents = 100L, category = ExpenseCategory.SHOPPING, date = now.minus(7, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(e4, e1, e7, e2, e6, e3, e5))
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(listOf(e1, e2, e3, e4, e5), content.recentExpenses)
            assertEquals(7, content.expenses.size)

            job.cancel()
        }

    @Test
    fun onTimeRangeSelected_switchesFilter_andReQueriesRepository() =
        runTest(testDispatcher) {
            val withinWeek =
                expense(id = "within-week", amountCents = 1_000L, category = ExpenseCategory.SHOPPING, date = now.minus(2, ChronoUnit.DAYS))
            val withinMonthNotWeek =
                expense(
                    id = "within-month",
                    amountCents = 2_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(20, ChronoUnit.DAYS),
                )
            val repo = FakeExpenseRepository(listOf(withinWeek, withinMonthNotWeek))
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val monthContent = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_MONTH, monthContent.timeRange)
            assertEquals(setOf(withinWeek, withinMonthNotWeek), monthContent.expenses.toSet())
            assertEquals(3_000L, monthContent.totalCents)

            viewModel.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val weekContent = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_WEEK, weekContent.timeRange)
            assertEquals(listOf(withinWeek), weekContent.expenses)
            assertEquals(1_000L, weekContent.totalCents)

            job.cancel()
        }

    @Test
    fun onTimeRangeSelected_toRangeWithNoExpenses_producesEmptyContent_notBrokenState() =
        runTest(testDispatcher) {
            val onlyOldExpense =
                expense(id = "old", amountCents = 1_000L, category = ExpenseCategory.SHOPPING, date = now.minus(200, ChronoUnit.DAYS))
            val repo = FakeExpenseRepository(listOf(onlyOldExpense))
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_WEEK, content.timeRange)
            assertTrue(content.expenses.isEmpty())
            assertTrue(content.recentExpenses.isEmpty())
            assertEquals(0L, content.totalCents)
            assertTrue(content.categoryTotals.all { it.totalCents == 0L })

            job.cancel()
        }

    // ---- observeExpenses failure -> Error state ----

    @Test
    fun observeExpensesFailure_beforeFirstCollection_surfacesErrorState_withUnderlyingMessage() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = IllegalStateException("Firestore unavailable")
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)

            job.cancel()
        }

    @Test
    fun observeExpensesFailure_withNullExceptionMessage_fallsBackToDefaultMessage() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            repo.nextObserveExpensesError = RuntimeException()
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(DashboardUiState.Error("Something went wrong"), viewModel.uiState.value)

            job.cancel()
        }

    @Test
    fun onRetry_afterClearingError_transitionsFromErrorBackToContent() =
        runTest(testDispatcher) {
            val existing =
                expense(
                    id = "e1",
                    amountCents = 1_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(1, ChronoUnit.DAYS),
                )
            val repo = FakeExpenseRepository(listOf(existing))
            repo.nextObserveExpensesError = IllegalStateException("Firestore unavailable")
            val viewModel = DashboardViewModel(repo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)

            // Clear the error on the underlying fake, then retry. Since `timeRange` itself hasn't
            // changed, only the retry-trigger bump forces flatMapLatest to re-subscribe -- proving
            // onRetry() isn't a no-op swallowed by StateFlow's equality-based dedup.
            repo.nextObserveExpensesError = null
            viewModel.onRetry()
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(listOf(existing), content.expenses)
            assertEquals(1_000L, content.totalCents)

            job.cancel()
        }
}
