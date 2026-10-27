package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The monthly-plan data of a backup: budgets plus planned-income lines. */
data class PlanBackupData(
    val budgets: List<Budget>,
    val plannedIncome: List<PlannedIncome>,
)

/** Reads the plan data (budgets and planned income) of the current session for a backup. */
class PlanBackupSource
    @Inject
    constructor(
        private val budgetRepository: BudgetRepository,
        private val plannedIncomeRepository: PlannedIncomeRepository,
    ) {
        suspend fun read(): PlanBackupData =
            PlanBackupData(
                budgets = budgetRepository.observeBudgets().first(),
                plannedIncome = plannedIncomeRepository.observePlannedIncome().first(),
            )
    }
