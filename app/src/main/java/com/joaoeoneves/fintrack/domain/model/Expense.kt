package com.joaoeoneves.fintrack.domain.model

import java.time.Instant

/**
 * A single expense. [amountCents] is always positive; [isRefund] marks money returned to the user
 * (e.g. a product return), which reduces its category's spending instead of adding to it.
 */
data class Expense(
    val id: String,
    val name: String,
    val amountCents: Long,
    val category: ExpenseCategory,
    val date: Instant,
    val note: String?,
    val isRefund: Boolean = false,
) {
    /** [amountCents] with its sign applied: negative for refunds, positive otherwise. */
    val signedAmountCents: Long get() = if (isRefund) -amountCents else amountCents
}

/** The net outflow of these expenses: regular expenses minus refunds (may be negative). */
fun Iterable<Expense>.netCents(): Long = sumOf { it.signedAmountCents }
