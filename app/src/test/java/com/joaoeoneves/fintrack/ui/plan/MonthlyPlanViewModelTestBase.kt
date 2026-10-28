package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.testutil.MutableClock
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
import java.time.Instant
import java.time.ZoneOffset

/** Shared setup for the [MonthlyPlanViewModel] tests. */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = Application::class)
open class MonthlyPlanViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val incomeRepo = FakePlannedIncomeRepository()
    protected val monthSettingsRepo = FakeMonthSettingsRepository()
    protected val budgetRepo = FakeBudgetRepository()
    protected val categoryRepo = FakeCategoryRepository()
    protected val expenseRepo = FakeExpenseRepository()
    protected val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected val actualIncomeRepo = FakeIncomeRepository()

    protected fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        expenses: ExpenseRepository = expenseRepo,
    ) = MonthlyPlanViewModel(
        PlanRepositories(incomeRepo, budgetRepo, categoryRepo, monthSettingsRepo),
        observePeriodOverviewOf(
            DashboardRepositories(
                expenses,
                budgetRepo,
                actualIncomeRepo,
                categoryRepo,
                incomeRepo,
                monthSettingsRepo,
            ),
        ),
        RecordPayday(incomeRepo, monthSettingsRepo, actualIncomeRepo, FakeAutomationSettingsRepository()),
        savedStateHandle,
        clock,
    )

    protected fun TestScope.collect(viewModel: MonthlyPlanViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    protected fun MonthlyPlanViewModel.content() = uiState.value as MonthlyPlanUiState.Content

    protected fun line(
        id: String,
        amountCents: Long = 100_000L,
        order: Int = 0,
    ) = PlannedIncome(id = id, label = id, amountCents = amountCents, order = order)
}
