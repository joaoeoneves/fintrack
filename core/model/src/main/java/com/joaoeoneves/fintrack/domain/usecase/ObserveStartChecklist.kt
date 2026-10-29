package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.StartChecklist
import com.joaoeoneves.fintrack.domain.model.startChecklistOf
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Observes Home's "Get started" [StartChecklist] from the repositories its steps read. */
class ObserveStartChecklist
    @Inject
    constructor(
        private val salaryProfiles: SalaryProfileRepository,
        private val plannedIncome: PlannedIncomeRepository,
        private val recurring: RecurringRepository,
        private val expenses: ExpenseRepository,
    ) {
        operator fun invoke(): Flow<StartChecklist> {
            val income =
                combine(salaryProfiles.observeSalaryProfile(), plannedIncome.observePlannedIncome()) { profile, lines ->
                    StartIncomeInputs(profile, lines)
                }
            return combine(income, recurring.observeRecurring(), expenses.observeHasAnyExpense()) { inc, rec, has ->
                startChecklistOf(inc.profile, inc.lines, rec, has)
            }
        }
    }

private class StartIncomeInputs(
    val profile: SalaryProfile?,
    val lines: List<PlannedIncome>,
)
