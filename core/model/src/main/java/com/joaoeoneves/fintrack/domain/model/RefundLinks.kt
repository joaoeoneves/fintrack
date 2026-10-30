package com.joaoeoneves.fintrack.domain.model

private const val PERCENT_FULL = 100L

/**
 * How much of an original expense its linked refunds returned. Display only: a refund keeps counting in its own
 * category and month, so linking changes no total.
 */
data class RefundStatus(
    val refundedCents: Long,
    val originalCents: Long,
) {
    /** Share refunded, rounded down and capped to 0..100. */
    val percent: Int
        get() =
            if (originalCents <= 0L) {
                0
            } else {
                (refundedCents * PERCENT_FULL / originalCents).coerceIn(0L, PERCENT_FULL).toInt()
            }

    /** What the original really cost after the refunds, never below zero. */
    val netCents: Long get() = (originalCents - refundedCents).coerceAtLeast(0L)
}

/** The [RefundStatus] of [original] from the [refunds] linked to it; `null` when none is or [original] is a refund. */
fun refundStatusOf(
    original: Expense,
    refunds: List<Expense>,
): RefundStatus? {
    if (original.isRefund) return null
    val linked = refunds.filter { it.isRefund && it.refundOf == original.id }
    return if (linked.isEmpty()) null else RefundStatus(linked.sumOf { it.amountCents }, original.amountCents)
}

/** Total refunded cents per original expense id, for the refunds in [refunds] that link to one. */
fun refundIndexOf(refunds: List<Expense>): Map<String, Long> =
    refunds
        .filter { it.isRefund && it.refundOf != null }
        .groupBy { it.refundOf.orEmpty() }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }

/** The expenses a refund can link to: regular expenses other than [excludeId], newest first. */
fun refundCandidates(
    expenses: List<Expense>,
    excludeId: String?,
): List<Expense> = expenses.filter { !it.isRefund && it.id != excludeId }.sortedByDescending { it.date }
