package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CARRY_LOOKBACK_MONTHS
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.IncomeFunding
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.PlanMonth
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.budgetProgressOf
import com.joaoeoneves.fintrack.domain.model.carryOverCents
import com.joaoeoneves.fintrack.domain.model.cashFlowSummaryOf
import com.joaoeoneves.fintrack.domain.model.contains
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.encloses
import com.joaoeoneves.fintrack.domain.model.envelopesOf
import com.joaoeoneves.fintrack.domain.model.fundingIn
import com.joaoeoneves.fintrack.domain.model.monthActualsOf
import com.joaoeoneves.fintrack.domain.model.monthFiguresOf
import com.joaoeoneves.fintrack.domain.model.monthlyPlanOf
import com.joaoeoneves.fintrack.domain.model.recentActivity
import com.joaoeoneves.fintrack.domain.model.salaryShiftEnabled
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Observes the [PeriodOverview] of a [Period]. Budgets and the monthly plan are evaluated against
 * the period's anchor month, or the current month for rolling/custom periods. When the period's
 * range already covers that whole month, the month's spend and income are derived from the period's
 * own transactions instead of opening a second listener.
 */
class ObservePeriodOverview
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val sources: PlanDataSources,
    ) {
        operator fun invoke(
            period: Period,
            now: Instant,
            zone: ZoneId,
            recentLimit: Int,
        ): Flow<PeriodOverview> {
            val range = period.toInstantRange(now, zone)
            val budgetMonth = period.anchorMonth(now, zone) ?: currentYearMonth(now, zone)
            val budgetRange = budgetMonth.toInstantRange(zone)
            val transactions = transactionRepository.observeTransactions(range)
            val planInputs =
                combine(
                    sources.budgets.observeBudgets(),
                    sources.plannedIncome.observePlannedIncome(),
                    sources.monthSettings.observeMonthSettings(),
                    sources.categories.observeCategories(),
                ) { budgets, lines, settings, catalog -> PlanInputs(budgets, lines, settings, catalog) }
            val inputs =
                combine(
                    combine(
                        planInputs,
                        sources.envelopes.observeEnvelopes(),
                        sources.recurring.observeRecurring(),
                    ) { plan, envelopes, recurring -> OverviewInputs(plan, envelopes, recurring) },
                    sources.planSettings.observeSalaryPaysNextMonth(),
                ) { overview, shift -> overview.copy(shiftPreference = shift) }
            // What the earlier months did, for the money they carry over and a salary paid at their end.
            val history =
                transactionRepository.observeTransactions(
                    InstantRange(
                        budgetMonth.minusMonths(CARRY_LOOKBACK_MONTHS).toInstantRange(zone).startInclusive,
                        budgetRange.startInclusive,
                    ),
                )
            return if (range.encloses(budgetRange)) {
                combine(transactions, history, inputs) { tx, past, plan ->
                    val month =
                        tx.copy(
                            expenses = tx.expenses.filter { it.date in budgetRange },
                            income = tx.income.filter { it.date in budgetRange },
                        )
                    overviewOf(budgetMonth, OverviewTransactions(tx, month, past, zone), plan, recentLimit)
                }
            } else {
                val monthTransactions = transactionRepository.observeTransactions(budgetRange)
                combine(transactions, monthTransactions, history, inputs) { tx, month, past, plan ->
                    overviewOf(budgetMonth, OverviewTransactions(tx, month, past, zone), plan, recentLimit)
                }
            }
        }

        private fun overviewOf(
            budgetMonth: YearMonth,
            all: OverviewTransactions,
            inputs: OverviewInputs,
            recentLimit: Int,
        ): PeriodOverview {
            val transactions = all.period
            val monthTransactions = all.month
            val plan = inputs.plan
            val context = fundingContextOf(budgetMonth, all, inputs)
            val funding = context.funding
            val fundingIncome = context.income
            val rows =
                budgetProgressOf(
                    monthTransactions.expenses,
                    plan.budgets,
                    plan.categories,
                    inputs.recurring,
                    budgetMonth,
                )
            val monthSettings = plan.monthSettings.firstOrNull { it.month == budgetMonth }
            val workingDays =
                effectiveWorkingDays(budgetMonth, monthSettings?.workingDaysOverride, monthSettings?.daysOff ?: 0)
            val planMonth = PlanMonth(budgetMonth, workingDays)
            val envelopes =
                envelopesOf(
                    buckets = rows,
                    categories = plan.categories,
                    envelopes = inputs.envelopes,
                    plannedIncome = plan.plannedIncome,
                    monthIncome = fundingIncome,
                    planMonth = planMonth,
                    funding = funding,
                )
            return PeriodOverview(
                budgetMonth = budgetMonth,
                transactions = transactions,
                summary = cashFlowSummaryOf(transactions.expenses, transactions.income, plan.categories),
                budgets = rows,
                plan =
                    monthlyPlanOf(
                        buckets = rows,
                        plannedIncome = plan.plannedIncome,
                        monthIncome = fundingIncome,
                        month = budgetMonth,
                        workingDays = workingDays,
                        envelopes = inputs.envelopes,
                        funding = funding,
                    ),
                recent = transactions.recentActivity(recentLimit),
                envelopes = envelopes,
                recurring = inputs.recurring,
                periodEnvelopes = periodEnvelopesOf(transactions, plan.categories, inputs.envelopes, planMonth),
                figures = monthFiguresOf(envelopes, context.carry),
                funding = funding,
                monthIncome = fundingIncome,
            )
        }
    }

/** How income is attributed to months, the incomes funding the month and what earlier months carry into it. */
private class FundingContext(
    val funding: IncomeFunding,
    val income: List<Income>,
    val carry: Long,
)

// A salary paid at the end of last month funds this month, one paid at the end of this month does not.
private fun fundingContextOf(
    month: YearMonth,
    all: OverviewTransactions,
    inputs: OverviewInputs,
): FundingContext {
    val plan = inputs.plan
    val lines = plan.plannedIncome
    val funding = IncomeFunding.of(salaryShiftEnabled(inputs.shiftPreference, lines), plan.monthSettings)
    val income = (all.month.income + all.past.income).fundingIn(month, lines, funding, all.zone)
    val actuals =
        monthActualsOf(all.past.expenses, all.past.income + all.month.income, plan.categories, lines, funding, all.zone)
    return FundingContext(funding, income, carryOverCents(month, actuals))
}

/** Spent per envelope over the whole period's [transactions] (planned amounts and funding are left out). */
private fun periodEnvelopesOf(
    transactions: TransactionsInRange,
    categories: List<Category>,
    envelopes: List<Envelope>,
    planMonth: PlanMonth,
): EnvelopesSummary =
    envelopesOf(
        buckets = budgetProgressOf(transactions.expenses, emptyList(), categories),
        categories = categories,
        envelopes = envelopes,
        plannedIncome = emptyList(),
        monthIncome = emptyList(),
        planMonth = planMonth,
    )

/** The plan-side flows of an overview, combined once so the main combine stays within five flows. */
private class PlanInputs(
    val budgets: List<Budget>,
    val plannedIncome: List<PlannedIncome>,
    val monthSettings: List<MonthSettings>,
    val categories: List<Category>,
)

/** [PlanInputs] with the envelopes and recurring items, combined once more to stay within five flows. */
private class OverviewInputs(
    val plan: PlanInputs,
    val envelopes: List<Envelope>,
    val recurring: List<RecurringItem>,
    val shiftPreference: Boolean? = null,
) {
    fun copy(shiftPreference: Boolean?) = OverviewInputs(plan, envelopes, recurring, shiftPreference)
}

/** The transactions of the [period], of the budget [month], and of the [past] months before it. */
private class OverviewTransactions(
    val period: TransactionsInRange,
    val month: TransactionsInRange,
    val past: TransactionsInRange,
    val zone: ZoneId,
)
