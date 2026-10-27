package com.joaoeoneves.fintrack.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.usecase.ObserveBudgetProgress
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
)

/** Like the five-parameter overload, but with an explicit [DashboardRepositories.categoryRepository]. */
fun dashboardViewModel(
    repositories: DashboardRepositories,
    savedStateHandle: SavedStateHandle,
    clock: Clock,
): DashboardViewModel {
    val expenseRepository = repositories.expenseRepository
    val budgetRepository = repositories.budgetRepository
    val incomeRepository = repositories.incomeRepository
    val categoryRepository = repositories.categoryRepository
    return DashboardViewModel(
        observePeriodOverview =
            ObservePeriodOverview(
                transactionRepository = CombinedTransactionRepository(expenseRepository, incomeRepository),
                budgetRepository = budgetRepository,
                categoryRepository = categoryRepository,
                observeBudgetProgress = ObserveBudgetProgress(expenseRepository, budgetRepository, categoryRepository),
            ),
        budgetRepository = budgetRepository,
        savedStateHandle = savedStateHandle,
        clock = clock,
    )
}
