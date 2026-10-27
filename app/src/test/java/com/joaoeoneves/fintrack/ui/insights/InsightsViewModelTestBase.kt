package com.joaoeoneves.fintrack.ui.insights

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryGroupRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.ui.dashboard.DashboardRepositories
import com.joaoeoneves.fintrack.ui.dashboard.observePeriodOverviewOf
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
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Shared setup for the [InsightsViewModel] tests. "Now" is fixed at 2026-10-20 12:00 UTC. */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = Application::class)
open class InsightsViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val zone = ZoneOffset.UTC
    protected val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    protected val clock: Clock = Clock.fixed(now, zone)
    protected val currentMonth: YearMonth = YearMonth.of(2026, 10)
    protected val previousMonth: YearMonth = YearMonth.of(2026, 9)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun viewModel(
        expenseRepository: FakeExpenseRepository = FakeExpenseRepository(),
        budgetRepository: FakeBudgetRepository = FakeBudgetRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        categoryRepository: FakeCategoryRepository = FakeCategoryRepository(),
        groupRepository: FakeCategoryGroupRepository = FakeCategoryGroupRepository(),
    ) = InsightsViewModel(
        observePeriodOverviewOf(
            DashboardRepositories(
                expenseRepository,
                budgetRepository,
                FakeIncomeRepository(),
                categoryRepository,
                categoryGroupRepository = groupRepository,
            ),
        ),
        savedStateHandle,
        clock,
    )

    protected fun TestScope.collect(viewModel: InsightsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    protected fun InsightsViewModel.content() = uiState.value as InsightsUiState.Content

    protected fun daysAgo(days: Long): Instant = now.minus(days, ChronoUnit.DAYS)

    protected fun noonOn(
        yearMonth: YearMonth,
        day: Int,
    ): Instant =
        yearMonth
            .atDay(day)
            .atTime(LocalTime.NOON)
            .atZone(zone)
            .toInstant()

    protected fun expense(
        id: String,
        amountCents: Long,
        category: Category = BuiltInCategories.SHOPPING,
        date: Instant = daysAgo(1),
    ) = Expense(id = id, name = id, amountCents = amountCents, category = category, date = date, note = null)

    protected fun refund(
        id: String,
        amountCents: Long,
        category: Category = BuiltInCategories.SHOPPING,
        date: Instant = daysAgo(1),
    ) = expense(id, amountCents, category, date).copy(isRefund = true)

    protected fun InsightsUiState.Content.total(category: Category): Long =
        breakdown.categoryTotals.single { it.category == category }.totalCents
}
