package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * Everything the dashboard shows for a selected [Period]: its [transactions] and their [summary],
 * the [budgets] progress and monthly [plan] for [budgetMonth] (the anchor month for month periods, otherwise the
 * current month), the [envelopes] of [budgetMonth] (amounts, spend, funding and the free money), the [recurring]
 * items they were planned from, the [recent] activity preview and the [periodEnvelopes]: spent per envelope over the
 * whole
 * period (not just [budgetMonth]; amounts and funding are not meaningful there).
 */
data class PeriodOverview(
    val budgetMonth: YearMonth,
    val transactions: TransactionsInRange,
    val summary: CashFlowSummary,
    val budgets: List<CategoryBudget>,
    val plan: MonthlyPlanSummary,
    val recent: RecentActivity,
    val envelopes: EnvelopesSummary = EnvelopesSummary(emptyList(), emptyList(), 0L),
    val recurring: List<RecurringItem> = emptyList(),
    val periodEnvelopes: EnvelopesSummary = EnvelopesSummary(emptyList(), emptyList(), 0L),
    val figures: MonthFigures = MonthFigures(0L, 0L, 0L, 0L),
    val funding: IncomeFunding = IncomeFunding.unshifted(budgetMonth, 0),
    val monthIncome: List<Income> = emptyList(),
)
