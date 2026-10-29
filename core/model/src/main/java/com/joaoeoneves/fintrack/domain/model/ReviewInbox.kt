package com.joaoeoneves.fintrack.domain.model

/**
 * The records recorded automatically and not yet reviewed (flagged [Expense.needsReview] /
 * [Income.needsReview]), newest first, ties broken by id.
 */
fun reviewInbox(
    expenses: List<Expense>,
    incomes: List<Income>,
): List<Transaction> =
    (expenses.filter { it.needsReview } + incomes.filter { it.needsReview })
        .sortedWith(compareByDescending<Transaction> { it.date }.thenBy { it.id })
