package com.joaoeoneves.fintrack.domain.model

/**
 * How a period's outflow divides: [fixedCents] is spending from confirmed recurring occurrences, [variableCents]
 * the rest of the spending and [savingCents] the saving-kind outflow, reported on its own. Each bucket is net
 * of refunds and floored at zero.
 */
data class SpendingSplit(
    val fixedCents: Long,
    val variableCents: Long,
    val savingCents: Long,
) {
    /** Fixed plus variable spending (saving excluded). */
    val spendingCents: Long get() = fixedCents + variableCents
}

/** Splits [expenses] into fixed, variable and saving buckets by kind and [isRecurringOccurrenceId]. */
fun spendingSplitOf(expenses: List<Expense>): SpendingSplit {
    val (spending, saving) = expenses.partition { it.category.kind == CategoryKind.SPENDING }
    val (fixed, variable) = spending.partition { isRecurringOccurrenceId(it.id) }
    return SpendingSplit(
        fixedCents = fixed.netCents().coerceAtLeast(0),
        variableCents = variable.netCents().coerceAtLeast(0),
        savingCents = saving.netCents().coerceAtLeast(0),
    )
}
