package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.AnnualPlan
import com.joaoeoneves.fintrack.domain.model.AnnualPlanInputs
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.annualPlanOf
import com.joaoeoneves.fintrack.domain.model.resolvedWith
import com.joaoeoneves.fintrack.domain.model.salaryShiftEnabled
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Year
import java.time.ZoneId
import javax.inject.Inject

/** Observes the [AnnualPlan] of a calendar year: the plan-side repositories plus the year's transactions. */
class ObserveAnnualPlan
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val sources: PlanDataSources,
        private val goalRepository: GoalRepository,
    ) {
        operator fun invoke(
            year: Int,
            zone: ZoneId,
        ): Flow<AnnualPlan> {
            val start =
                Year
                    .of(year)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
            val end =
                Year
                    .of(year + 1)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
            val planInputs =
                combine(
                    sources.budgets.observeBudgets(),
                    sources.plannedIncome.observePlannedIncome(),
                    sources.monthSettings.observeMonthSettings(),
                    sources.categories.observeCategories(),
                ) { budgets, lines, settings, catalog -> AnnualPlanSources(budgets, lines, settings, catalog) }
            val shift = sources.planSettings.observeSalaryPaysNextMonth()
            val extraInputs =
                combine(
                    sources.envelopes.observeEnvelopes(),
                    sources.recurring.observeRecurring(),
                    goalRepository.observeGoals(),
                ) { envelopes, recurring, goals -> Triple(envelopes, recurring, goals) }
            return combine(
                combine(planInputs, shift) { plan, pref -> plan to pref },
                extraInputs,
                transactionRepository.observeTransactions(InstantRange(start, end)),
            ) { (plan, pref), (envelopes, recurring, goals), transactions ->
                annualPlanOf(
                    year,
                    AnnualPlanInputs(
                        lines = plan.plannedIncome,
                        budgets = plan.budgets,
                        recurring = recurring,
                        categories = plan.categories,
                        envelopes = envelopes,
                        monthSettings = plan.monthSettings,
                        yearExpenses = transactions.expenses.resolvedWith(plan.categories),
                        goals = goals,
                        shiftSalary = salaryShiftEnabled(pref, plan.plannedIncome),
                    ),
                )
            }
        }
    }

private class AnnualPlanSources(
    val budgets: List<Budget>,
    val plannedIncome: List<PlannedIncome>,
    val monthSettings: List<MonthSettings>,
    val categories: List<Category>,
)
