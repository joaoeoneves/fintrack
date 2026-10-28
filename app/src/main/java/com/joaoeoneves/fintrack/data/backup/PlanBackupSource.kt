package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.LegacyExtraNamesProvider
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The monthly-plan data of a backup: budgets, income lines, recurring, month settings, groups, salary profile
 * goals and automation settings.
 */
data class PlanBackupData(
    val budgets: List<Budget>,
    val plannedIncome: List<PlannedIncome>,
    val recurring: List<RecurringItem>,
    val monthSettings: List<MonthSettings> = emptyList(),
    val categoryGroups: List<Envelope> = emptyList(),
    val salaryProfile: SalaryProfile? = null,
    val goals: List<SavingsGoal> = emptyList(),
    val automation: AutomationSettings = AutomationSettings(),
    /** The name given to the placeholder of a category a record references but that no longer exists. */
    val deletedCategoryName: String = PLACEHOLDER_CATEGORY_NAME,
)

/** The salary profile, goal and automation repositories (and default names), bundled for detekt's LongParameterList. */
class PlanSingletonSources
    @Inject
    constructor(
        val salaryProfile: SalaryProfileRepository,
        val goals: GoalRepository,
        val automation: AutomationSettingsRepository,
        val names: LegacyExtraNamesProvider,
    )

/** Reads the plan data (budgets, planned income, recurring) of the current session for a backup. */
class PlanBackupSource
    @Inject
    constructor(
        private val budgetRepository: BudgetRepository,
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val recurringRepository: RecurringRepository,
        private val monthSettingsRepository: MonthSettingsRepository,
        private val categoryGroupRepository: EnvelopeRepository,
        private val singletons: PlanSingletonSources,
    ) {
        suspend fun read(): PlanBackupData =
            PlanBackupData(
                budgets = budgetRepository.observeBudgets().first(),
                plannedIncome = plannedIncomeRepository.observePlannedIncome().first(),
                recurring = recurringRepository.observeRecurring().first(),
                monthSettings = monthSettingsRepository.observeMonthSettings().first(),
                categoryGroups = categoryGroupRepository.observeEnvelopes().first(),
                salaryProfile = singletons.salaryProfile.observeSalaryProfile().first(),
                goals = singletons.goals.observeGoals().first(),
                automation = singletons.automation.observeAutomationSettings().first(),
                deletedCategoryName = singletons.names.names().deletedCategory,
            )
    }
