package com.joaoeoneves.fintrack.domain.model

/**
 * The records to look at, newest first, ties broken by id: those recorded automatically and not yet reviewed
 * (flagged [Expense.needsReview] / [Income.needsReview]), plus the [awaiting] expenses (flagged
 * [Expense.awaitingRefund]) that no refund in [linkedRefunds] points at through [Expense.refundOf]; once any refund
 * links to one, partial or not, it leaves the inbox, and it returns if that refund is deleted. An expense that is
 * in both lists appears once.
 */
fun reviewInbox(
    expenses: List<Expense>,
    incomes: List<Income>,
    awaiting: List<Expense> = emptyList(),
    linkedRefunds: List<Expense> = emptyList(),
): List<Transaction> {
    val refunded = linkedRefunds.mapNotNullTo(mutableSetOf()) { it.refundOf }
    val stillAwaiting = awaiting.filter { it.id !in refunded }
    return (expenses.filter { it.needsReview } + stillAwaiting + incomes.filter { it.needsReview })
        .distinctBy { it.id }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenBy { it.id })
}
