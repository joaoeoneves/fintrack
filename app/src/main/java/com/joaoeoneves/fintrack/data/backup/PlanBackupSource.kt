package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The monthly-plan data of a backup: budgets, income lines, recurring, month settings, groups, salary profile. */
data class PlanBackupData(
    val budgets: List<Budget>,
    val plannedIncome: List<PlannedIncome>,
    val recurring: List<RecurringItem>,
    val monthSettings: List<MonthSettings> = emptyList(),
    val categoryGroups: List<CategoryGroup> = emptyList(),
    val salaryProfile: SalaryProfile? = null,
)

/** Reads the plan data (budgets, planned income, recurring) of the current session for a backup. */
class PlanBackupSource
    @Inject
    constructor(
        private val budgetRepository: BudgetRepository,
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val recurringRepository: RecurringRepository,
        private val monthSettingsRepository: MonthSettingsRepository,
        private val categoryGroupRepository: CategoryGroupRepository,
        private val salaryProfileRepository: SalaryProfileRepository,
    ) {
        suspend fun read(): PlanBackupData =
            PlanBackupData(
                budgets = budgetRepository.observeBudgets().first(),
                plannedIncome = plannedIncomeRepository.observePlannedIncome().first(),
                recurring = recurringRepository.observeRecurring().first(),
                monthSettings = monthSettingsRepository.observeMonthSettings().first(),
                categoryGroups = categoryGroupRepository.observeGroups().first(),
                salaryProfile = salaryProfileRepository.observeSalaryProfile().first(),
            )
    }
