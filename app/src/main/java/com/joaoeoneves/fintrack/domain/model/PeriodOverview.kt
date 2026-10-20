package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * Everything the dashboard shows for a selected [Period]: its [transactions] and their [summary],
 * the [budgets] progress for [budgetMonth] (the anchor month for month periods, otherwise the
 * current month), and the [recent] activity preview.
 */
data class PeriodOverview(
    val budgetMonth: YearMonth,
    val transactions: TransactionsInRange,
    val summary: CashFlowSummary,
    val budgets: List<CategoryBudget>,
    val recent: RecentActivity,
)
