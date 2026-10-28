package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.StartChecklist
import com.joaoeoneves.fintrack.domain.model.startChecklistOf
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
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
        private val envelopes: EnvelopeRepository,
        private val recurring: RecurringRepository,
        private val goals: GoalRepository,
        private val expenses: ExpenseRepository,
    ) {
        operator fun invoke(): Flow<StartChecklist> {
            val income =
                combine(salaryProfiles.observeSalaryProfile(), plannedIncome.observePlannedIncome()) { profile, lines ->
                    StartIncomeInputs(profile, lines)
                }
            val plan =
                combine(envelopes.observeEnvelopes(), recurring.observeRecurring()) { envelopes, recurring ->
                    StartPlanInputs(envelopes, recurring)
                }
            return combine(income, plan, goals.observeGoals(), expenses.observeHasAnyExpense()) { inc, pl, goals, has ->
                startChecklistOf(inc.profile, inc.lines, pl.envelopes, pl.recurring, goals, has)
            }
        }
    }

private class StartIncomeInputs(
    val profile: SalaryProfile?,
    val lines: List<PlannedIncome>,
)

private class StartPlanInputs(
    val envelopes: List<Envelope>,
    val recurring: List<RecurringItem>,
)
