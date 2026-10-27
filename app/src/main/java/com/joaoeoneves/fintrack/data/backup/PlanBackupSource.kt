package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The monthly-plan data of a backup: budgets, planned-income lines and recurring items. */
data class PlanBackupData(
    val budgets: List<Budget>,
    val plannedIncome: List<PlannedIncome>,
    val recurring: List<RecurringItem>,
)

/** Reads the plan data (budgets, planned income, recurring) of the current session for a backup. */
class PlanBackupSource
    @Inject
    constructor(
        private val budgetRepository: BudgetRepository,
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val recurringRepository: RecurringRepository,
    ) {
        suspend fun read(): PlanBackupData =
            PlanBackupData(
                budgets = budgetRepository.observeBudgets().first(),
                plannedIncome = plannedIncomeRepository.observePlannedIncome().first(),
                recurring = recurringRepository.observeRecurring().first(),
            )
    }
