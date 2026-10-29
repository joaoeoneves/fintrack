package com.joaoeoneves.fintrack.ui.income

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.ui.dashboard.DashboardRepositories
import com.joaoeoneves.fintrack.ui.dashboard.observeMonthlyHistoryOf
import com.joaoeoneves.fintrack.ui.dashboard.observePeriodOverviewOf
import com.joaoeoneves.fintrack.ui.plan.PlanRepositories
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

/** Shared setup for the [IncomeTabViewModel] tests. */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = Application::class)
open class IncomeTabViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()
    protected val incomeRepo = FakePlannedIncomeRepository()
    protected val monthSettingsRepo = FakeMonthSettingsRepository()
    protected val budgetRepo = FakeBudgetRepository()
    protected val categoryRepo = FakeCategoryRepository()
    protected val expenseRepo = FakeExpenseRepository()
    protected val envelopeRepo = FakeEnvelopeRepository()
    protected val recurringRepo = FakeRecurringRepository()
    protected val goalRepo = FakeGoalRepository()
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
    protected val automationRepo = FakeAutomationSettingsRepository()

    protected fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        expenses: ExpenseRepository = expenseRepo,
        incomes: IncomeRepository = actualIncomeRepo,
    ): IncomeTabViewModel {
        val repositories =
            DashboardRepositories(
                expenses,
                budgetRepo,
                incomes,
                categoryRepo,
                incomeRepo,
                monthSettingsRepo,
                envelopeRepo,
                recurringRepo,
            )
        return IncomeTabViewModel(
            PlanRepositories(incomeRepo, budgetRepo, categoryRepo, monthSettingsRepo, goalRepo, automationRepo),
            observePeriodOverviewOf(repositories),
            observeMonthlyHistoryOf(repositories),
            RecordPayday(incomeRepo, monthSettingsRepo, incomes, automationRepo, FakeSalaryLineLabelsProvider()),
            savedStateHandle,
            clock,
        )
    }

    protected fun TestScope.collect(viewModel: IncomeTabViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    protected fun IncomeTabViewModel.content() = uiState.value as IncomeTabUiState.Content

    protected val IncomeTabUiState.Content.incomeRows get() = month!!.incomeRows
    protected val IncomeTabUiState.Content.workingDays get() = month!!.workingDays
    protected val IncomeTabUiState.Content.receipts get() = month!!.receipts
    protected val IncomeTabUiState.Content.duplicateKeys get() = month!!.duplicateKeys

    protected fun line(
        id: String,
        amountCents: Long = 100_000L,
        order: Int = 0,
    ) = PlannedIncome(id = id, label = id, amountCents = amountCents, order = order)
}
