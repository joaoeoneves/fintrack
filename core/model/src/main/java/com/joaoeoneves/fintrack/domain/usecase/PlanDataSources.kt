package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryGroupRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import javax.inject.Inject

/** The plan-side repositories an overview is assembled from, bundled for detekt's LongParameterList. */
class PlanDataSources
    @Inject
    constructor(
        val budgets: BudgetRepository,
        val plannedIncome: PlannedIncomeRepository,
        val monthSettings: MonthSettingsRepository,
        val categories: CategoryRepository,
        val groups: CategoryGroupRepository,
        val recurring: RecurringRepository,
    )
