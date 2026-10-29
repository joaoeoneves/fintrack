package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.PlanMonth
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.budgetProgressOf
import com.joaoeoneves.fintrack.domain.model.cashFlowSummaryOf
import com.joaoeoneves.fintrack.domain.model.contains
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.encloses
import com.joaoeoneves.fintrack.domain.model.envelopesOf
import com.joaoeoneves.fintrack.domain.model.filterOwnedIn
import com.joaoeoneves.fintrack.domain.model.monthlyPlanOf
import com.joaoeoneves.fintrack.domain.model.recentActivity
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
 * the period's anchor month, or the current month for rolling/custom periods. Income counts in its owner month
 * (see `ownerMonth`). When the
 * period's range already covers that whole month, the month's spend and income are derived from the period's own
 * transactions instead of opening a second listener.
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
            val inputs =
                combine(
                    combine(
                        sources.budgets.observeBudgets(),
                        sources.plannedIncome.observePlannedIncome(),
                        sources.monthSettings.observeMonthSettings(),
                        sources.categories.observeCategories(),
                    ) { budgets, lines, settings, catalog -> PlanInputs(budgets, lines, settings, catalog) },
                    sources.envelopes.observeEnvelopes(),
                    sources.recurring.observeRecurring(),
                ) { plan, envelopes, recurring -> OverviewInputs(plan, envelopes, recurring) }
            return if (range.encloses(budgetRange)) {
                combine(transactions, inputs) { tx, plan ->
                    val month =
                        tx.copy(
                            expenses = tx.expenses.filter { it.date in budgetRange },
                            income = filterOwnedIn(tx.income, budgetRange, zone),
                        )
                    overviewOf(budgetMonth, tx, month, plan, recentLimit)
                }
            } else {
                val monthTransactions = transactionRepository.observeTransactions(budgetRange)
                combine(transactions, monthTransactions, inputs) { tx, mo, plan ->
                    overviewOf(budgetMonth, tx, mo, plan, recentLimit)
                }
            }
        }

        private fun overviewOf(
            budgetMonth: YearMonth,
            transactions: TransactionsInRange,
            monthTransactions: TransactionsInRange,
            inputs: OverviewInputs,
            recentLimit: Int,
        ): PeriodOverview {
            val plan = inputs.plan
            val monthIncome = monthTransactions.income
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
                    planMonth = planMonth,
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
                        monthIncome = monthIncome,
                        month = budgetMonth,
                        workingDays = workingDays,
                        envelopes = inputs.envelopes,
                    ),
                recent = transactions.recentActivity(recentLimit),
                envelopes = envelopes,
                recurring = inputs.recurring,
                periodEnvelopes = periodEnvelopesOf(transactions, plan.categories, inputs.envelopes, planMonth),
                monthIncome = monthIncome,
                plannedIncome = plan.plannedIncome,
            )
        }
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
)
