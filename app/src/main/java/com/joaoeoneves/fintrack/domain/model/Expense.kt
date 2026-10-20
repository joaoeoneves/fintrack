package com.joaoeoneves.fintrack.domain.model

import java.time.Instant

/**
 * A single expense. [amountCents] is always positive; [isRefund] marks money returned to the user
 * (e.g. a product return), which reduces its category's spending instead of adding to it.
 */
data class Expense(
    override val id: String,
    val name: String,
    override val amountCents: Long,
    val category: ExpenseCategory,
    override val date: Instant,
    override val note: String?,
    val isRefund: Boolean = false,
) : Transaction {
    /** [amountCents] with its sign applied: negative for refunds, positive otherwise. */
    val signedAmountCents: Long get() = if (isRefund) -amountCents else amountCents

    /** Balance effect: a regular expense lowers the balance, a refund raises it. */
    override val cashFlowCents: Long get() = -signedAmountCents
}

/** The net outflow of these expenses: regular expenses minus refunds (may be negative). */
fun Iterable<Expense>.netCents(): Long = sumOf { it.signedAmountCents }
