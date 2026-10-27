package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * Progress of a single [Category] against what is planned for a budget month. [limitCents] is the explicit
 * budget limit (`null` when none is set, even if [planned] comes from recurring items), [skipMonths] the
 * months that limit does not apply to, [planned] what is planned for this month and [plannedSource] where
 * that comes from.
 */
data class CategoryBudget(
    val category: Category,
    val limitCents: Long?,
    val spentCents: Long,
    val planned: Long? = limitCents,
    val plannedSource: PlannedSource = if (limitCents != null) PlannedSource.BUDGET else PlannedSource.NONE,
    val skipMonths: Set<Int> = emptySet(),
) {
    val isOverBudget: Boolean get() = planned != null && spentCents > planned
}

/**
 * Budget progress per category, given that month's expenses and the [categories] catalog: one row
 * per active category plus any category that has a budget, a planned amount or spend (e.g. archived or
 * unknown ones, so a stale budget can still be cleared), in catalog order. Spend is the category's net
 * outflow, floored at zero so refunds never show negative progress. With a [month], planned amounts
 * follow [plannedForCategory] (budgets, skipped months and [recurring] items); without one only the
 * budgets' limits count.
 */
fun budgetProgressOf(
    monthExpenses: List<Expense>,
    budgets: List<Budget>,
    categories: List<Category>,
    recurring: List<RecurringItem> = emptyList(),
    month: YearMonth? = null,
): List<CategoryBudget> {
    val spendById = monthExpenses.groupBy { it.category.id }.mapValues { (_, expenses) -> expenses.netCents() }
    val budgetById = budgets.associateBy { it.categoryId }
    val plannedById =
        if (month == null) {
            budgetById.mapValues { (_, budget) -> PlannedAmount(budget.limitCents, PlannedSource.BUDGET) }
        } else {
            val recurringById =
                recurring
                    .mapNotNull { item -> item.categoryId?.let { it to item } }
                    .groupBy({ it.first }, { it.second })
            (budgetById.keys + recurringById.keys).associateWith { id ->
                plannedForCategory(month, budgetById[id], recurringById[id].orEmpty())
            }
        }
    val plannedIds = plannedById.filterValues { it.source != PlannedSource.NONE }.keys
    return categories.rowsFor(spendById.keys + budgetById.keys + plannedIds).map { category ->
        val planned = plannedById[category.id] ?: PlannedAmount(null, PlannedSource.NONE)
        CategoryBudget(
            category = category,
            limitCents = budgetById[category.id]?.limitCents,
            spentCents = (spendById[category.id] ?: 0L).coerceAtLeast(0),
            planned = planned.cents,
            plannedSource = planned.source,
            skipMonths = budgetById[category.id]?.skipMonths.orEmpty(),
        )
    }
}
