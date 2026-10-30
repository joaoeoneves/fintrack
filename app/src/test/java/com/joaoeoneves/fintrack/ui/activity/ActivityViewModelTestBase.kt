package com.joaoeoneves.fintrack.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.usecase.DeleteTransaction
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Shared setup for [ActivityViewModel] tests (filters, period, edit/error). "Now" is fixed at
 * 2026-10-20 12:00 UTC. Robolectric supplies the `Bundle` the [SavedStateHandle] needs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
abstract class ActivityViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    protected val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun noonOn(
        year: Int,
        month: Int,
        day: Int,
    ): Instant = LocalDate.of(year, month, day).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC)

    protected fun expense(
        id: String,
        amountCents: Long = 100L,
        date: Instant = now,
        isRefund: Boolean = false,
        category: Category = BuiltInCategories.SHOPPING,
    ): Expense = testExpense(id, amountCents, date, category, isRefund)

    protected fun income(
        id: String,
        amountCents: Long = 100L,
        date: Instant = now,
    ): Income = testIncome(id, amountCents, date)

    protected fun args(vararg pairs: Pair<String, Any?>) = SavedStateHandle(mapOf(*pairs))

    /** Expense and income repositories (listener-counting) plus the ViewModel built on them. */
    protected inner class Fixture(
        expenses: List<Expense> = emptyList(),
        incomes: List<Income> = emptyList(),
        val handle: SavedStateHandle = SavedStateHandle(),
        val categories: FakeCategoryRepository = FakeCategoryRepository(),
        val envelopes: FakeEnvelopeRepository = FakeEnvelopeRepository(),
    ) {
        val expenseRepo = CountingExpenseRepository(expenses)
        val incomeRepo = CountingIncomeRepository(incomes)
        val recurringRepo = FakeRecurringRepository()
        val plannedIncomeRepo = FakePlannedIncomeRepository()
        private val sources =
            PlanDataSources(
                plannedIncome = plannedIncomeRepo,
                monthSettings = FakeMonthSettingsRepository(),
                categories = categories,
                envelopes = envelopes,
                recurring = recurringRepo,
            )
        private val transactions = CombinedTransactionRepository(expenseRepo, incomeRepo)
        private val overview = ObservePeriodOverview(transactionRepository = transactions, sources = sources)
        val deleteTransaction = DeleteTransaction(expenseRepo, incomeRepo, recurringRepo)
        val vm = ActivityViewModel(overview, categories, deleteTransaction, handle, clock)

        /** A fresh ViewModel over the same repositories and [SavedStateHandle] (process-death restore). */
        fun recreate() = ActivityViewModel(overview, categories, deleteTransaction, handle, clock)
    }

    protected fun TestScope.content(vm: ActivityViewModel): ActivityUiState.Content {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return current(vm)
    }

    protected fun current(vm: ActivityViewModel) = vm.uiState.value as ActivityUiState.Content
}
