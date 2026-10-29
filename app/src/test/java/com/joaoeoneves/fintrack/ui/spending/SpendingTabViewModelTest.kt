package com.joaoeoneves.fintrack.ui.spending

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.usecase.ObserveMonthlyHistory
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SpendingTabViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Setup(
        val vm: SpendingTabViewModel,
        val expenseRepo: CountingExpenseRepository,
    )

    private fun setup(
        vararg args: Pair<String, Any?>,
        expenseRepo: ExpenseRepository? = null,
    ): Setup {
        val counting = CountingExpenseRepository(listOf<Expense>(testExpense("e", 100L, clock.instant())))
        val transactions = CombinedTransactionRepository(expenseRepo ?: counting, CountingIncomeRepository(emptyList()))
        val sources =
            PlanDataSources(
                budgets = FakeBudgetRepository(),
                plannedIncome = FakePlannedIncomeRepository(),
                monthSettings = FakeMonthSettingsRepository(),
                categories = FakeCategoryRepository(),
                envelopes = FakeEnvelopeRepository(),
                recurring = FakeRecurringRepository(),
            )
        val vm =
            SpendingTabViewModel(
                ObservePeriodOverview(transactionRepository = transactions, sources = sources),
                ObserveMonthlyHistory(transactions, sources),
                FakeCategoryRepository(),
                SavedStateHandle(mapOf(*args)),
                clock,
            )
        return Setup(vm, counting)
    }

    private fun TestScope.content(vm: SpendingTabViewModel): SpendingTabUiState.Content {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value as SpendingTabUiState.Content
    }

    @Test
    fun seededFromRoute_period() =
        runTest(testDispatcher) {
            val period = Period.Month(YearMonth.of(2026, 7))
            val c = content(setup("period" to period.encode()).vm)

            assertEquals(period, c.period)
        }

    @Test
    fun seededFromRoute_yearToken() =
        runTest(testDispatcher) {
            val c = content(setup("period" to "year:2026").vm)

            assertEquals(Period.Year(2026), c.period)
        }

    @Test
    fun defaults_currentMonth() =
        runTest(testDispatcher) {
            assertEquals(Period.CurrentMonth, content(setup().vm).period)
        }

    @Test
    fun window_isTwelveMonthsEndingAtThePeriodsLastMonth() =
        runTest(testDispatcher) {
            val c = content(setup("period" to Period.Month(YearMonth.of(2026, 7)).encode()).vm)
            val months =
                c.history!!
                    .monthly.months
                    .map { it.month }

            assertEquals(12, months.size)
            assertEquals(YearMonth.of(2026, 7), months.last())
        }

    @Test
    fun window_longerPeriodWidensTheWindow() =
        runTest(testDispatcher) {
            val custom = Period.Custom(LocalDate.of(2025, 3, 1), LocalDate.of(2026, 10, 15))
            val c = content(setup("period" to custom.encode()).vm)

            assertEquals(
                20,
                c.history!!
                    .monthly.months.size,
            )
            assertEquals(
                YearMonth.of(2026, 10),
                c.history
                    .monthly.months
                    .last()
                    .month,
            )
        }

    @Test
    fun periodChange_resubscribes_andMovesTheWindow() =
        runTest(testDispatcher) {
            val s = setup()
            content(s.vm)
            val before = s.expenseRepo.listeners.observedRanges.size

            s.vm.periodActions.onPrevious()
            advanceUntilIdle()
            assertTrue(s.expenseRepo.listeners.observedRanges.size > before)
            val last =
                (s.vm.uiState.value as SpendingTabUiState.Content)
                    .history!!
                    .monthly.months
                    .last()
            assertEquals(YearMonth.of(2026, 9), last.month)
        }

    @Test
    fun overviewFailure_isAnError() =
        runTest(testDispatcher) {
            val failing =
                object : ExpenseRepository by CountingExpenseRepository(emptyList<Expense>()) {
                    override fun observeExpenses(
                        startInclusive: Instant,
                        endExclusive: Instant,
                    ): Flow<List<Expense>> = flow { throw IllegalStateException("boom") }
                }
            val vm = setup(expenseRepo = failing).vm
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertTrue(vm.uiState.value is SpendingTabUiState.Error)
        }
}
