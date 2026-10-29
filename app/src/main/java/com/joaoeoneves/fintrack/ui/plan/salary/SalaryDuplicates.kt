package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.flow.first

/**
 * The user's own monthly lines (not generated): after saving a salary profile they may be counted
 * twice, so the user is offered removing them.
 */
internal suspend fun manualMonthlyLines(plannedIncome: PlannedIncomeRepository): List<PlannedIncome> =
    plannedIncome
        .observePlannedIncome()
        .first()
        .filter { !it.isManaged && it.frequency == IncomeFrequency.MONTHLY }
