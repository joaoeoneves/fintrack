package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import javax.inject.Inject

/** The repositories the monthly plan is assembled from, bundled for detekt's LongParameterList. */
class PlanRepositories
    @Inject
    constructor(
        val plannedIncome: PlannedIncomeRepository,
        val categories: CategoryRepository,
        val monthSettings: MonthSettingsRepository,
        val automation: AutomationSettingsRepository,
    )
