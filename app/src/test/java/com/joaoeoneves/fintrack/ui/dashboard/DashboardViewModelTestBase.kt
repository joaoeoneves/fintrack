package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
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
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Shared fixtures/setup for [DashboardViewModel] tests, split by feature area into
 * [DashboardViewModelExpenseTest], [DashboardViewModelBudgetTest],
 * [DashboardViewModelIncomeTest], [DashboardViewModelPeriodTest], [DashboardViewModelRefundTest] and
 * [DashboardViewModelFoodCategoryTest].
 *
 * "Now" is a fixed [clock] (2026-10-20 12:00 UTC) injected into the ViewModel, so every fixture
 * lands deterministically inside/outside the current month -- no `Instant.now()` and no
 * Assume-based "near a month boundary" skips.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = Application::class)
open class DashboardViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected val zone: ZoneId = ZoneOffset.UTC
    protected val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    protected val clock: Clock = Clock.fixed(now, zone)
    protected val currentMonth: YearMonth = YearMonth.of(2026, 10)
    protected val previousMonth: YearMonth = YearMonth.of(2026, 9)

    protected fun viewModel(
        expenseRepository: ExpenseRepository = FakeExpenseRepository(),
        budgetRepository: BudgetRepository = FakeBudgetRepository(),
        incomeRepository: IncomeRepository = FakeIncomeRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        clock: Clock = this.clock,
    ) = dashboardViewModel(expenseRepository, budgetRepository, incomeRepository, savedStateHandle, clock)

    /** Starts collecting [viewModel]'s uiState in the background and runs it to idle. */
    protected fun TestScope.collect(viewModel: DashboardViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    protected fun DashboardViewModel.content() = uiState.value as DashboardUiState.Content

    protected fun daysAgo(days: Long): Instant = now.minus(days, ChronoUnit.DAYS)

    // Named `name` after `id` (no test needs a distinct display name), keeping this under detekt's
    // LongParameterList threshold.
    protected fun expense(
        id: String,
        amountCents: Long,
        category: Category = BuiltInCategories.SHOPPING,
        date: Instant,
        note: String? = null,
    ) = Expense(id = id, name = id, amountCents = amountCents, category = category, date = date, note = note)

    protected fun income(
        id: String,
        source: String = id,
        amountCents: Long,
        date: Instant,
        note: String? = null,
    ) = Income(id = id, source = source, amountCents = amountCents, date = date, note = note)

    /** Noon on [day] of the given [yearMonth], safely away from midnight rollover edges. */
    protected fun noonOn(
        yearMonth: YearMonth,
        day: Int,
    ): Instant =
        yearMonth
            .atDay(day)
            .atTime(LocalTime.NOON)
            .atZone(zone)
            .toInstant()
}
