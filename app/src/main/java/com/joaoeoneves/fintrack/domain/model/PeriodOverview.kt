package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * Everything the dashboard shows for a selected [Period]: its [transactions] and their [summary],
 * the [budgets] progress and monthly [plan] for [budgetMonth] (the anchor month for month periods, otherwise the
 * current month), the [groups] planned vs actual, the [recurring] items they were planned from and the [recent]
 * activity preview, the [pots] the month's money is split into (`null` when not computed) and the
 * [periodGroups]: spent per group over the whole period (not just [budgetMonth]; planned is not set).
 */
data class PeriodOverview(
    val budgetMonth: YearMonth,
    val transactions: TransactionsInRange,
    val summary: CashFlowSummary,
    val budgets: List<CategoryBudget>,
    val plan: MonthlyPlanSummary,
    val recent: RecentActivity,
    val groups: List<GroupProgress> = emptyList(),
    val recurring: List<RecurringItem> = emptyList(),
    val pots: PotsSummary? = null,
    val periodGroups: List<GroupProgress> = emptyList(),
)
