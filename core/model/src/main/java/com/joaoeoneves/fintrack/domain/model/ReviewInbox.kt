package com.joaoeoneves.fintrack.domain.model

/**
 * The records recorded automatically and not yet reviewed (flagged [Expense.needsReview] /
 * [Income.needsReview]), newest first, ties broken by id.
 */
@Suppress("UnusedParameter") // Body lands with the review-inbox implementation.
fun reviewInbox(
    expenses: List<Expense>,
    incomes: List<Income>,
): List<Transaction> = emptyList()
