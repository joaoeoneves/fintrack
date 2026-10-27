package com.joaoeoneves.fintrack.domain.model

/**
 * Progress of a single [Category] against its budget limit for a budget month.
 * [limitCents] is `null` when no budget has been set for the category.
 */
data class CategoryBudget(
    val category: Category,
    val limitCents: Long?,
    val spentCents: Long,
) {
    val isOverBudget: Boolean get() = limitCents != null && spentCents > limitCents
}

/**
 * Budget progress per category, given that month's expenses and the [categories] catalog: one row
 * per active category plus any category that has a budget or spend (e.g. archived or unknown ones,
 * so a stale budget can still be cleared), in catalog order. Spend is the category's net outflow,
 * floored at zero so refunds never show negative progress.
 */
fun budgetProgressOf(
    monthExpenses: List<Expense>,
    budgets: List<Budget>,
    categories: List<Category>,
): List<CategoryBudget> {
    val spendById = monthExpenses.groupBy { it.category.id }.mapValues { (_, expenses) -> expenses.netCents() }
    val limitById = budgets.associate { it.categoryId to it.limitCents }
    return categories.rowsFor(spendById.keys + limitById.keys).map { category ->
        CategoryBudget(
            category = category,
            limitCents = limitById[category.id],
            spentCents = (spendById[category.id] ?: 0L).coerceAtLeast(0),
        )
    }
}
