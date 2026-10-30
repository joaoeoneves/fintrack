package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * Progress of a single [Category] against what is planned for a month: [planned] is what the active recurring
 * expense items of the category plan for it (`null` when none) and [plannedSource] where that comes from.
 */
data class CategoryBudget(
    val category: Category,
    val spentCents: Long,
    val planned: Long? = null,
    val plannedSource: PlannedSource = if (planned != null) PlannedSource.RECURRING else PlannedSource.NONE,
) {
    val isOverBudget: Boolean get() = planned != null && spentCents > planned
}

/**
 * Progress per category, given that month's expenses and the [categories] catalog: one row per active category
 * plus any category that has a planned amount or spend (e.g. archived or unknown ones), in catalog order. Spend is
 * the category's net outflow, floored at zero so refunds never show negative progress. With a [month], planned
 * amounts follow [plannedForCategory] over the [recurring] items; without one nothing is planned.
 */
fun budgetProgressOf(
    monthExpenses: List<Expense>,
    categories: List<Category>,
    recurring: List<RecurringItem> = emptyList(),
    month: YearMonth? = null,
): List<CategoryBudget> {
    val spendById = monthExpenses.groupBy { it.category.id }.mapValues { (_, expenses) -> expenses.netCents() }
    val plannedById =
        if (month == null) {
            emptyMap()
        } else {
            recurring
                .mapNotNull { item -> item.categoryId?.let { it to item } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, items) -> plannedForCategory(month, items) }
        }
    val plannedIds = plannedById.filterValues { it.source != PlannedSource.NONE }.keys
    return categories.rowsFor(spendById.keys + plannedIds).map { category ->
        val planned = plannedById[category.id] ?: PlannedAmount(null, PlannedSource.NONE)
        CategoryBudget(
            category = category,
            spentCents = (spendById[category.id] ?: 0L).coerceAtLeast(0),
            planned = planned.cents,
            plannedSource = planned.source,
        )
    }
}
