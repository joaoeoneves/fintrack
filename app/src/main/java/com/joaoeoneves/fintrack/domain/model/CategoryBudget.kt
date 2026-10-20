package com.joaoeoneves.fintrack.domain.model

/**
 * Progress of a single [ExpenseCategory] against its budget limit for a budget month.
 * [limitCents] is `null` when no budget has been set for the category.
 */
data class CategoryBudget(
    val category: ExpenseCategory,
    val limitCents: Long?,
    val spentCents: Long,
) {
    val isOverBudget: Boolean get() = limitCents != null && spentCents > limitCents
}

/**
 * Budget progress for every [ExpenseCategory] (in enum order), given that month's expenses. Spend
 * is the category's net outflow, floored at zero so refunds never show negative progress.
 */
fun budgetProgressOf(
    monthExpenses: List<Expense>,
    budgets: List<Budget>,
): List<CategoryBudget> =
    ExpenseCategory.entries.map { category ->
        CategoryBudget(
            category = category,
            limitCents = budgets.find { it.category == category }?.limitCents,
            spentCents =
                monthExpenses
                    .filter { it.category == category }
                    .netCents()
                    .coerceAtLeast(0),
        )
    }
