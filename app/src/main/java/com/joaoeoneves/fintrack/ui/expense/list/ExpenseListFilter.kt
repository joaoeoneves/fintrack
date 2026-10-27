package com.joaoeoneves.fintrack.ui.expense.list

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.amountMatchesQuery
import com.joaoeoneves.fintrack.ui.common.containsIgnoreCase

/**
 * The expense list's active filters: the free-text [query], the selected [category] (null = all),
 * and the localized [categoryLabels] the query is also matched against (resolved by the screen,
 * since the ViewModel has no access to the current locale's resources).
 */
data class ExpenseListFilter(
    val query: String,
    val category: CategoryId?,
    val categoryLabels: Map<CategoryId, String>,
)

/**
 * Whether this expense matches a search [query]: case-insensitively against its name, note,
 * category name (the user's own, or the displayed label from [labels]), a built-in category's id
 * (e.g. `SHOPPING`; generated ids of user categories are never matched), or as an amount (see
 * [amountMatchesQuery]).
 */
fun Expense.matchesQuery(
    query: String,
    labels: Map<CategoryId, String>,
): Boolean {
    val q = query.trim()
    return name.containsIgnoreCase(q) ||
        note.containsIgnoreCase(q) ||
        category.customName.containsIgnoreCase(q) ||
        labels[category.id].containsIgnoreCase(q) ||
        (category.builtIn && category.id.value.containsIgnoreCase(q)) ||
        amountMatchesQuery(amountCents, q)
}

/**
 * Applies [filter] (category first, then the query when not blank) and [sort] to these expenses
 * and wraps the result as list content. Amount sorts use [Expense.signedAmountCents], so refunds
 * sort below regular expenses.
 */
fun List<Expense>.toListContent(
    period: Period,
    canGoToNextMonth: Boolean,
    filter: ExpenseListFilter,
    sort: SortOption,
): ExpenseListUiState.Content {
    val byCategory = if (filter.category == null) this else filter { it.category.id == filter.category }
    val filtered =
        if (filter.query.isBlank()) {
            byCategory
        } else {
            byCategory.filter { it.matchesQuery(filter.query, filter.categoryLabels) }
        }
    val sorted =
        when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.date }
            SortOption.DATE_ASC -> filtered.sortedBy { it.date }
            SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.signedAmountCents }
            SortOption.AMOUNT_ASC -> filtered.sortedBy { it.signedAmountCents }
        }
    return ExpenseListUiState.Content(
        period = period,
        canGoToNextMonth = canGoToNextMonth,
        query = filter.query,
        sortOption = sort,
        expenses = sorted,
        categoryFilter = filter.category,
    )
}
