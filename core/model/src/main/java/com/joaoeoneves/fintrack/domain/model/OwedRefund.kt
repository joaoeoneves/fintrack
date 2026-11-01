package com.joaoeoneves.fintrack.domain.model

/** An expense marked "will be refunded" with how much of it came back and how much is still owed. */
data class OwedRefund(
    val expense: Expense,
    val refundedCents: Long,
    val remainingCents: Long,
) {
    /** Share already refunded, rounded down, 0..100. */
    val percent: Int get() = RefundStatus(refundedCents, expense.amountCents).percent
}

/**
 * The [awaiting] expenses still owed money, newest first (ties by id): remaining is the amount minus the refunds in
 * [linkedRefunds] pointing at it through [Expense.refundOf]. Fully or over-refunded expenses are left out; split
 * parts count individually.
 */
fun owedRefunds(
    awaiting: List<Expense>,
    linkedRefunds: List<Expense>,
): List<OwedRefund> {
    val refunded = refundIndexOf(linkedRefunds)
    return awaiting
        .filter { !it.isRefund }
        .distinctBy { it.id }
        .map { OwedRefund(it, refunded[it.id] ?: 0L, it.amountCents - (refunded[it.id] ?: 0L)) }
        .filter { it.remainingCents > 0L }
        .sortedWith(compareByDescending<OwedRefund> { it.expense.date }.thenBy { it.expense.id })
}

/** Total still owed over [owed]. */
fun List<OwedRefund>.totalRemainingCents(): Long = sumOf { it.remainingCents }
