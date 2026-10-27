package com.joaoeoneves.fintrack.domain.model

/**
 * Share of [incomeCents] that was saved or invested ([savedCents]), as a fraction (0.25 = 25%).
 * Returns `null` when there is no positive income to compare against, since the rate is undefined.
 */
fun savingsRate(
    savedCents: Long,
    incomeCents: Long,
): Double? = if (incomeCents <= 0L) null else savedCents.toDouble() / incomeCents.toDouble()

/** The net outflow ([Expense]s minus refunds, possibly negative) of a single [category]. */
data class CategoryTotal(
    val category: Category,
    val totalCents: Long,
)

/**
 * Aggregated cash flow over a set of transactions.
 *
 * @property spendingCents net outflow of [CategoryKind.SPENDING] categories.
 * @property savedCents net outflow of [CategoryKind.SAVING] categories.
 * @property categoryTotals one entry per active category plus any other category with expenses
 *   (archived or unknown), in catalog order with unknown ones last.
 */
data class CashFlowSummary(
    val incomeCents: Long,
    val spendingCents: Long,
    val savedCents: Long,
    val categoryTotals: List<CategoryTotal>,
) {
    /** Everything that left, i.e. [spendingCents] + [savedCents]. */
    val outflowCents: Long get() = spendingCents + savedCents

    /** Income minus all outflow (spending and saving alike). */
    val netCents: Long get() = incomeCents - outflowCents

    /** [savedCents] as a fraction of [incomeCents], `null` without income. */
    val savingsRate: Double? get() = savingsRate(savedCents = savedCents, incomeCents = incomeCents)
}

/**
 * Summarizes [expenses] and [income] against the [categories] catalog, which decides each
 * expense's [CategoryKind] (looked up by id, so a stale copy on the expense never wins). Active
 * categories without expenses get a zero total.
 */
fun cashFlowSummaryOf(
    expenses: List<Expense>,
    income: List<Income>,
    categories: List<Category>,
): CashFlowSummary {
    val byId = expenses.groupBy { it.category.id }
    val rows = categories.rowsFor(byId.keys)
    val totals = rows.map { category -> CategoryTotal(category, byId[category.id].orEmpty().netCents()) }
    return CashFlowSummary(
        incomeCents = income.sumOf { it.amountCents },
        spendingCents = totals.filter { it.category.kind == CategoryKind.SPENDING }.sumOf { it.totalCents },
        savedCents = totals.filter { it.category.kind == CategoryKind.SAVING }.sumOf { it.totalCents },
        categoryTotals = totals,
    )
}
