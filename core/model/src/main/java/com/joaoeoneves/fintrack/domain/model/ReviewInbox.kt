package com.joaoeoneves.fintrack.domain.model

/**
 * The records to look at, newest first, ties broken by id: those recorded automatically and not yet reviewed
 * (flagged [Expense.needsReview] / [Income.needsReview]). Expenses marked as awaiting a refund are not listed here;
 * they have their own card (see [owedRefunds]).
 */
fun reviewInbox(
    expenses: List<Expense>,
    incomes: List<Income>,
): List<Transaction> =
    (expenses.filter { it.needsReview } + incomes.filter { it.needsReview })
        .distinctBy { it.id }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenBy { it.id })
