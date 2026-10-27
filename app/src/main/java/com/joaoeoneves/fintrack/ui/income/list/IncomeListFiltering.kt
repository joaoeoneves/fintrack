package com.joaoeoneves.fintrack.ui.income.list

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.amountMatchesQuery
import com.joaoeoneves.fintrack.ui.common.containsIgnoreCase
import com.joaoeoneves.fintrack.ui.expense.list.SortOption

/**
 * Whether this income entry matches a search [query]: case-insensitively against its source or
 * note, or as an amount (see [amountMatchesQuery]).
 */
fun Income.matchesQuery(query: String): Boolean {
    val q = query.trim()
    return source.containsIgnoreCase(q) || note.containsIgnoreCase(q) || amountMatchesQuery(amountCents, q)
}

/** Applies [query] (when not blank) and [sort] to these entries and wraps the result as list content. */
fun List<Income>.toListContent(
    period: Period,
    canGoToNextMonth: Boolean,
    query: String,
    sort: SortOption,
): IncomeListUiState.Content {
    val filtered = if (query.isBlank()) this else filter { it.matchesQuery(query) }
    val sorted =
        when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.date }
            SortOption.DATE_ASC -> filtered.sortedBy { it.date }
            SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amountCents }
            SortOption.AMOUNT_ASC -> filtered.sortedBy { it.amountCents }
        }
    return IncomeListUiState.Content(
        period = period,
        canGoToNextMonth = canGoToNextMonth,
        query = query,
        sortOption = sort,
        income = sorted,
    )
}
