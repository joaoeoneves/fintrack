package com.joaoeoneves.fintrack.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
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
    DashboardViewModel(
        observePeriodOverview =
            ObservePeriodOverview(
                transactionRepository = CombinedTransactionRepository(expenseRepository, incomeRepository),
                budgetRepository = budgetRepository,
                observeBudgetProgress = ObserveBudgetProgress(expenseRepository, budgetRepository),
            ),
        budgetRepository = budgetRepository,
        savedStateHandle = savedStateHandle,
        clock = clock,
    )
