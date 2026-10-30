package com.joaoeoneves.fintrack.domain.model

/**
 * The records recorded automatically and not yet reviewed (flagged [Expense.needsReview] /
 * [Income.needsReview]), newest first, ties broken by id.
 */
fun reviewInbox(
    expenses: List<Expense>,
    incomes: List<Income>,
    awaiting: List<Expense> = emptyList(),
    linkedRefunds: List<Expense> = emptyList(),
): List<Transaction> {
    val refunded = linkedRefunds.mapNotNull { it.refundOf }.toSet()
    val waiting = awaiting.filter { it.id !in refunded }
    return (expenses.filter { it.needsReview } + waiting + incomes.filter { it.needsReview })
        .distinctBy { it.id }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenBy { it.id })
}
