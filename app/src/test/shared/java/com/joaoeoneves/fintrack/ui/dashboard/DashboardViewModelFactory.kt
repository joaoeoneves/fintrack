package com.joaoeoneves.fintrack.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import java.time.Clock

/**
 * Builds a [DashboardViewModel] wired to the real use cases on top of the given repositories (usually
 * fakes), mirroring the Hilt graph so tests only have to provide the repositories.
 */
fun dashboardViewModel(
    expenseRepository: ExpenseRepository,
    budgetRepository: BudgetRepository,
    incomeRepository: IncomeRepository,
    savedStateHandle: SavedStateHandle,
    clock: Clock,
): DashboardViewModel =
    dashboardViewModel(
        DashboardRepositories(expenseRepository, budgetRepository, incomeRepository, FakeCategoryRepository()),
        savedStateHandle,
        clock,
    )

/** The repositories a [DashboardViewModel] is built on (see [dashboardViewModel]). */
data class DashboardRepositories(
    val expenseRepository: ExpenseRepository,
    val budgetRepository: BudgetRepository,
    val incomeRepository: IncomeRepository,
    val categoryRepository: CategoryRepository,
    val plannedIncomeRepository: PlannedIncomeRepository = FakePlannedIncomeRepository(),
    val monthSettingsRepository: MonthSettingsRepository = FakeMonthSettingsRepository(),
)

/** Like the five-parameter overload, but with an explicit [DashboardRepositories.categoryRepository]. */
fun dashboardViewModel(
    repositories: DashboardRepositories,
    savedStateHandle: SavedStateHandle,
    clock: Clock,
): DashboardViewModel =
    DashboardViewModel(
        observePeriodOverview = observePeriodOverviewOf(repositories),
        savedStateHandle = savedStateHandle,
        clock = clock,
    )

/** The real [ObservePeriodOverview] use case on top of [repositories], as the Hilt graph wires it. */
fun observePeriodOverviewOf(repositories: DashboardRepositories): ObservePeriodOverview =
    ObservePeriodOverview(
        transactionRepository =
            CombinedTransactionRepository(repositories.expenseRepository, repositories.incomeRepository),
        budgetRepository = repositories.budgetRepository,
        plannedIncomeRepository = repositories.plannedIncomeRepository,
        categoryRepository = repositories.categoryRepository,
        monthSettingsRepository = repositories.monthSettingsRepository,
    )
