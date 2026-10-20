package com.joaoeoneves.fintrack.domain.model

/** The expenses and income dated within [range], as delivered by the repositories. */
data class TransactionsInRange(
    val range: InstantRange,
    val expenses: List<Expense>,
    val income: List<Income>,
) {
    /** Expenses and income merged into one list, newest first. */
    val all: List<Transaction> get() = (expenses + income).sortedByDescending { it.date }
}

/** The most recent expenses and income, each newest first. */
data class RecentActivity(
    val expenses: List<Expense>,
    val income: List<Income>,
)

/** The [limit] most recent expenses and the [limit] most recent income entries in this range. */
fun TransactionsInRange.recentActivity(limit: Int): RecentActivity =
    RecentActivity(
        expenses = expenses.sortedByDescending { it.date }.take(limit),
        income = income.sortedByDescending { it.date }.take(limit),
    )
