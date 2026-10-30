package com.joaoeoneves.fintrack.domain.model

/** Most parts one purchase can be split into. */
const val MAX_SPLIT_PARTS = 10

private const val MIN_SPLIT_PARTS = 2

/** One slice of a split purchase: its [category] and [amountCents]. */
data class SplitPart(
    val category: Category,
    val amountCents: Long,
)

/**
 * Splits [base] into one sibling expense per part, all sharing [splitId] (the other fields, with a blank id, come from
 * [base]). Requires 2..[MAX_SPLIT_PARTS] parts, each positive, summing to [base]'s amount, and a non-refund [base].
 */
fun splitExpenses(
    base: Expense,
    parts: List<SplitPart>,
    splitId: String,
): List<Expense> {
    require(!base.isRefund) { "A refund cannot be split" }
    require(parts.size in MIN_SPLIT_PARTS..MAX_SPLIT_PARTS) { "A split needs $MIN_SPLIT_PARTS..$MAX_SPLIT_PARTS parts" }
    require(parts.all { it.amountCents > 0L }) { "Every part must be positive" }
    require(parts.sumOf { it.amountCents } == base.amountCents) { "The parts must add up to the total" }
    return parts.map { part ->
        base.copy(
            id = "",
            category = part.category,
            amountCents = part.amountCents,
            refundOf = null,
            splitId = splitId,
        )
    }
}
