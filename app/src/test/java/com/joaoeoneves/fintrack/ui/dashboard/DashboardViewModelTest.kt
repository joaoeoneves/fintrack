package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [DashboardViewModel], backed by [FakeExpenseRepository] and
 * [FakeBudgetRepository]. All expense fixtures are anchored relative to a real `Instant.now()`
 * (as the ViewModel itself uses `Instant.now()` internally to compute its query ranges) with
 * margins comfortably inside/outside each [TimeRange] bucket, to avoid boundary flakiness.
 *
 * Budget-related fixtures additionally need to land inside/outside the *current calendar month*
 * (via [com.joaoeoneves.fintrack.domain.model.currentCalendarMonthRange], which -- like
 * [TimeRange.toInstantRange] -- is called with real defaults inside the ViewModel and has no
 * test seam to override "now"). Where a fixture would need to be both "more than a week old" and
 * "still within the current calendar month" -- which is impossible during the first several days
 * of a month -- the test uses [assumeTrue] to skip itself gracefully rather than risk flakiness.
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
    private val zone: ZoneId = ZoneId.systemDefault()
    private val currentMonth: YearMonth = YearMonth.from(now.atZone(zone))

    private fun expense(
        id: String,
        name: String = id,
        amountCents: Long,
        category: ExpenseCategory,
        date: Instant,
        note: String? = null,
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    private fun income(
        id: String,
        source: String = id,
        amountCents: Long,
        date: Instant,
        note: String? = null,
    ) = Income(id = id, source = source, amountCents = amountCents, date = date, note = note)

    /** Noon on [day] of the given [yearMonth], safely away from midnight rollover edges. */
    private fun noonOn(
        yearMonth: YearMonth,
        day: Int,
    ): Instant =
        yearMonth
            .atDay(day)
            .atTime(LocalTime.NOON)
            .atZone(zone)
            .toInstant()

    @Test
    fun initialState_isLoading_beforeCollectionStarts() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

            // Before advanceUntilIdle/collection, the StateFlow built via stateIn(WhileSubscribed)
            // has not yet started collecting upstream, so it should still report its initialValue.
            assertEquals(DashboardUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun emptyRepository_producesContentWithZeroTotalsAndAllCategoriesPresent() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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
            val viewModel = DashboardViewModel(repo, FakeBudgetRepository(), FakeIncomeRepository())

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

    // ---- budgets: presence/defaults ----

    @Test
    fun budgets_noExpensesNoBudgets_allCategoriesPresentWithNullLimitAndZeroSpent() =
        runTest(testDispatcher) {
            val expenseRepo = FakeExpenseRepository()
            val budgetRepo = FakeBudgetRepository()
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(ExpenseCategory.entries.size, content.budgets.size)
            assertEquals(ExpenseCategory.entries.toSet(), content.budgets.map { it.category }.toSet())
            assertTrue(content.budgets.all { it.limitCents == null })
            assertTrue(content.budgets.all { it.spentCents == 0L })
            assertTrue(content.budgets.none { it.isOverBudget })

            job.cancel()
        }

    @Test
    fun budgets_reflectsPreSeededBudgetLimits_forCategoriesWithNoSpendYet() =
        runTest(testDispatcher) {
            val expenseRepo = FakeExpenseRepository()
            val budgetRepo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            val shoppingBudget = content.budgets.single { it.category == ExpenseCategory.SHOPPING }
            assertEquals(10_000L, shoppingBudget.limitCents)
            assertEquals(0L, shoppingBudget.spentCents)
            assertTrue(!shoppingBudget.isOverBudget)
            // Other categories remain unset.
            assertTrue(
                content.budgets.filter { it.category != ExpenseCategory.SHOPPING }.all { it.limitCents == null },
            )

            job.cancel()
        }

    // ---- budgets: spend is scoped to the current calendar month, independent of the selected TimeRange ----

    @Test
    fun budgetSpend_includesCurrentMonthExpense_evenWhenExcludedByNarrowerSelectedTimeRange() =
        runTest(testDispatcher) {
            // This fixture needs an expense that is simultaneously (a) inside the current calendar
            // month and (b) more than a week old, which is only constructible if "today" is at
            // least ~8 days into the month. Skip gracefully otherwise rather than risk flakiness.
            val today = now.atZone(zone).toLocalDate()
            assumeTrue(
                "requires at least 10 days elapsed in the current month to safely construct a " +
                    "fixture outside the ONE_WEEK range but inside the current calendar month",
                today.dayOfMonth > 10,
            )
            val overAWeekAgoButThisMonth = now.minus(10, ChronoUnit.DAYS)

            val oldButThisMonthExpense =
                expense(
                    id = "old-but-this-month",
                    amountCents = 4_321L,
                    category = ExpenseCategory.SHOPPING,
                    date = overAWeekAgoButThisMonth,
                )
            val expenseRepo = FakeExpenseRepository(listOf(oldButThisMonthExpense))
            val budgetRepo = FakeBudgetRepository()
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            viewModel.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            // The selected-range expense list excludes it (it's more than a week old)...
            assertTrue(content.expenses.isEmpty())
            // ...but the budget spend, being calendar-month-scoped, still includes it.
            val shoppingBudget = content.budgets.single { it.category == ExpenseCategory.SHOPPING }
            assertEquals(4_321L, shoppingBudget.spentCents)

            job.cancel()
        }

    @Test
    fun budgetSpend_excludesExpenseFromPreviousCalendarMonth_evenWhenIncludedBySelectedTimeRange() =
        runTest(testDispatcher) {
            val previousMonth = currentMonth.minusMonths(1)
            val previousMonthExpense =
                expense(
                    id = "previous-month",
                    amountCents = 9_000L,
                    category = ExpenseCategory.INVESTMENTS,
                    date = noonOn(previousMonth, 15),
                )
            val thisMonthExpense =
                expense(
                    id = "this-month",
                    amountCents = 1_500L,
                    category = ExpenseCategory.INVESTMENTS,
                    date = now,
                )
            val expenseRepo = FakeExpenseRepository(listOf(previousMonthExpense, thisMonthExpense))
            val budgetRepo = FakeBudgetRepository()
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            viewModel.onTimeRangeSelected(TimeRange.ONE_YEAR)
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            // Wide enough selected range: both expenses show up in the general expense list.
            assertEquals(setOf(previousMonthExpense, thisMonthExpense), content.expenses.toSet())
            // But budget spend only counts the current-month expense.
            val investmentsBudget = content.budgets.single { it.category == ExpenseCategory.INVESTMENTS }
            assertEquals(1_500L, investmentsBudget.spentCents)

            job.cancel()
        }

    // ---- isOverBudget ----

    @Test
    fun isOverBudget_falseWhenNoLimitSet_regardlessOfSpend() =
        runTest(testDispatcher) {
            val bigExpense =
                expense(id = "big", amountCents = 1_000_000L, category = ExpenseCategory.SHOPPING, date = now)
            val expenseRepo = FakeExpenseRepository(listOf(bigExpense))
            val budgetRepo = FakeBudgetRepository() // no limit set
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            val shoppingBudget = content.budgets.single { it.category == ExpenseCategory.SHOPPING }
            assertEquals(1_000_000L, shoppingBudget.spentCents)
            assertNull(shoppingBudget.limitCents)
            assertTrue(!shoppingBudget.isOverBudget)

            job.cancel()
        }

    @Test
    fun isOverBudget_falseWhenSpendEqualsLimitExactly() =
        runTest(testDispatcher) {
            val expenseAtLimit =
                expense(id = "at-limit", amountCents = 10_000L, category = ExpenseCategory.SHOPPING, date = now)
            val expenseRepo = FakeExpenseRepository(listOf(expenseAtLimit))
            val budgetRepo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            val shoppingBudget = content.budgets.single { it.category == ExpenseCategory.SHOPPING }
            assertEquals(10_000L, shoppingBudget.spentCents)
            assertTrue("spend exactly at the limit should not count as over budget", !shoppingBudget.isOverBudget)

            job.cancel()
        }

    @Test
    fun isOverBudget_trueWhenSpendExceedsLimit() =
        runTest(testDispatcher) {
            val overLimitExpense =
                expense(id = "over-limit", amountCents = 10_001L, category = ExpenseCategory.SHOPPING, date = now)
            val expenseRepo = FakeExpenseRepository(listOf(overLimitExpense))
            val budgetRepo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)))
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            val shoppingBudget = content.budgets.single { it.category == ExpenseCategory.SHOPPING }
            assertTrue(shoppingBudget.isOverBudget)

            job.cancel()
        }

    // ---- onSetBudget ----

    @Test
    fun onSetBudget_callsThroughToBudgetRepository_andUiStateReflectsNewLimit() =
        runTest(testDispatcher) {
            val expenseRepo = FakeExpenseRepository()
            val budgetRepo = FakeBudgetRepository()
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val before = viewModel.uiState.value as DashboardUiState.Content
            assertNull(before.budgets.single { it.category == ExpenseCategory.SHOPPING }.limitCents)

            viewModel.onSetBudget(ExpenseCategory.SHOPPING, 10_000L)
            advanceUntilIdle()

            val after = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(10_000L, after.budgets.single { it.category == ExpenseCategory.SHOPPING }.limitCents)
            // Confirm it really went through the repository, not just some ViewModel-local echo.
            assertEquals(listOf(Budget(ExpenseCategory.SHOPPING, 10_000L)), budgetRepo.observeBudgets().first())

            job.cancel()
        }

    @Test
    fun onSetBudget_updatingExistingLimit_replacesRatherThanDuplicates() =
        runTest(testDispatcher) {
            val expenseRepo = FakeExpenseRepository()
            val budgetRepo = FakeBudgetRepository(listOf(Budget(ExpenseCategory.SHOPPING, 5_000L)))
            val viewModel = DashboardViewModel(expenseRepo, budgetRepo, FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onSetBudget(ExpenseCategory.SHOPPING, 7_500L)
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(ExpenseCategory.entries.size, content.budgets.size)
            assertEquals(7_500L, content.budgets.single { it.category == ExpenseCategory.SHOPPING }.limitCents)

            job.cancel()
        }

    // ---- income / net balance: scoped to the currently-selected TimeRange (not the calendar-month budget range) ----

    @Test
    fun incomeCents_sumsIncomeWithinSelectedTimeRange_excludesIncomeOutsideIt() =
        runTest(testDispatcher) {
            val insideOneMonth = income(id = "inside", amountCents = 250_000L, date = now.minus(3, ChronoUnit.DAYS))
            val outsideOneMonth = income(id = "outside", amountCents = 999_999L, date = now.minus(40, ChronoUnit.DAYS))
            val expenseRepo = FakeExpenseRepository()
            val incomeRepo = FakeIncomeRepository(listOf(insideOneMonth, outsideOneMonth))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_MONTH, content.timeRange)
            assertEquals(250_000L, content.incomeCents)
            assertEquals(listOf(insideOneMonth), content.recentIncome)

            job.cancel()
        }

    @Test
    fun incomeCents_emptyIncomeRepository_isZero() =
        runTest(testDispatcher) {
            val expenseRepo = FakeExpenseRepository()
            val incomeRepo = FakeIncomeRepository()
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(0L, content.incomeCents)
            assertTrue(content.recentIncome.isEmpty())

            job.cancel()
        }

    @Test
    fun netCents_equalsIncomeMinusExpenses_forSelectedTimeRange() =
        runTest(testDispatcher) {
            val expenseThisMonth =
                expense(
                    id = "e1",
                    amountCents = 12_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(2, ChronoUnit.DAYS),
                )
            val incomeThisMonth = income(id = "i1", amountCents = 300_000L, date = now.minus(1, ChronoUnit.DAYS))
            val expenseRepo = FakeExpenseRepository(listOf(expenseThisMonth))
            val incomeRepo = FakeIncomeRepository(listOf(incomeThisMonth))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(300_000L, content.incomeCents)
            assertEquals(12_000L, content.totalCents)
            assertEquals(288_000L, content.netCents)

            job.cancel()
        }

    @Test
    fun netCents_negativeWhenExpensesExceedIncome() =
        runTest(testDispatcher) {
            val bigExpense = expense(id = "e1", amountCents = 50_000L, category = ExpenseCategory.SHOPPING, date = now)
            val smallIncome = income(id = "i1", amountCents = 10_000L, date = now)
            val expenseRepo = FakeExpenseRepository(listOf(bigExpense))
            val incomeRepo = FakeIncomeRepository(listOf(smallIncome))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(-40_000L, content.netCents)

            job.cancel()
        }

    @Test
    fun netCents_zeroWhenNoIncomeAndNoExpenses() =
        runTest(testDispatcher) {
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), FakeIncomeRepository())

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(0L, content.incomeCents)
            assertEquals(0L, content.totalCents)
            assertEquals(0L, content.netCents)

            job.cancel()
        }

    @Test
    fun recentIncome_returnsTop5ByDateDescending_whenMoreThan5InRange() =
        runTest(testDispatcher) {
            // 7 income entries inside the range, at distinct days, in scrambled insertion order.
            val i1 = income(id = "i1", amountCents = 100L, date = now.minus(1, ChronoUnit.DAYS))
            val i2 = income(id = "i2", amountCents = 100L, date = now.minus(2, ChronoUnit.DAYS))
            val i3 = income(id = "i3", amountCents = 100L, date = now.minus(3, ChronoUnit.DAYS))
            val i4 = income(id = "i4", amountCents = 100L, date = now.minus(4, ChronoUnit.DAYS))
            val i5 = income(id = "i5", amountCents = 100L, date = now.minus(5, ChronoUnit.DAYS))
            val i6 = income(id = "i6", amountCents = 100L, date = now.minus(6, ChronoUnit.DAYS))
            val i7 = income(id = "i7", amountCents = 100L, date = now.minus(7, ChronoUnit.DAYS))
            val incomeRepo = FakeIncomeRepository(listOf(i4, i1, i7, i2, i6, i3, i5))
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(listOf(i1, i2, i3, i4, i5), content.recentIncome)
            assertEquals(700L, content.incomeCents)

            job.cancel()
        }

    @Test
    fun income_inDifferentTimeRangeBucketThanExpenses_isFilteredIndependently() =
        runTest(testDispatcher) {
            // Income lands within the current month but *outside* the current week; the expense is
            // the reverse (inside the week, and therefore also inside the month). Selecting
            // ONE_WEEK should include the expense but exclude the income; selecting ONE_MONTH
            // should include both.
            val incomeOutsideWeekInsideMonth =
                income(id = "income-month-only", amountCents = 400_000L, date = now.minus(20, ChronoUnit.DAYS))
            val expenseInsideWeek =
                expense(
                    id = "expense-week",
                    amountCents = 5_000L,
                    category = ExpenseCategory.SHOPPING,
                    date = now.minus(1, ChronoUnit.DAYS),
                )
            val expenseRepo = FakeExpenseRepository(listOf(expenseInsideWeek))
            val incomeRepo = FakeIncomeRepository(listOf(incomeOutsideWeekInsideMonth))
            val viewModel = DashboardViewModel(expenseRepo, FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val monthContent = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_MONTH, monthContent.timeRange)
            assertEquals(400_000L, monthContent.incomeCents)
            assertEquals(5_000L, monthContent.totalCents)
            assertEquals(395_000L, monthContent.netCents)

            viewModel.onTimeRangeSelected(TimeRange.ONE_WEEK)
            advanceUntilIdle()

            val weekContent = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(TimeRange.ONE_WEEK, weekContent.timeRange)
            // Income from 20 days ago is excluded from the one-week bucket...
            assertEquals(0L, weekContent.incomeCents)
            assertTrue(weekContent.recentIncome.isEmpty())
            // ...while the expense (1 day ago) is still included.
            assertEquals(5_000L, weekContent.totalCents)
            assertEquals(-5_000L, weekContent.netCents)

            job.cancel()
        }

    // ---- observeIncome failure -> Error state (mirrors observeExpenses failure handling) ----

    @Test
    fun observeIncomeFailure_beforeFirstCollection_surfacesErrorState_withUnderlyingMessage() =
        runTest(testDispatcher) {
            val incomeRepo = FakeIncomeRepository()
            incomeRepo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)

            job.cancel()
        }

    @Test
    fun observeIncomeFailure_withNullExceptionMessage_fallsBackToDefaultMessage() =
        runTest(testDispatcher) {
            val incomeRepo = FakeIncomeRepository()
            incomeRepo.nextObserveIncomeError = RuntimeException()
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(DashboardUiState.Error("Something went wrong"), viewModel.uiState.value)

            job.cancel()
        }

    @Test
    fun onRetry_afterClearingObserveIncomeError_transitionsFromErrorBackToContent() =
        runTest(testDispatcher) {
            val existingIncome = income(id = "i1", amountCents = 1_000L, date = now.minus(1, ChronoUnit.DAYS))
            val incomeRepo = FakeIncomeRepository(listOf(existingIncome))
            incomeRepo.nextObserveIncomeError = IllegalStateException("Firestore unavailable")
            val viewModel = DashboardViewModel(FakeExpenseRepository(), FakeBudgetRepository(), incomeRepo)

            val job = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(DashboardUiState.Error("Firestore unavailable"), viewModel.uiState.value)

            incomeRepo.nextObserveIncomeError = null
            viewModel.onRetry()
            advanceUntilIdle()

            val content = viewModel.uiState.value as DashboardUiState.Content
            assertEquals(1_000L, content.incomeCents)

            job.cancel()
        }
}
