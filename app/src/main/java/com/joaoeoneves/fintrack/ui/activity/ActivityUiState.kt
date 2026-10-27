package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.DaySection
import com.joaoeoneves.fintrack.ui.common.UiText

/** Which kinds of transaction the Activity list shows. */
enum class ActivityType {
    ALL,
    EXPENSE,
    INCOME,
}

/** The Activity list's active filters and sort. */
data class ActivityFilters(
    val type: ActivityType,
    val category: CategoryId?,
    val query: String,
    val sort: SortOption,
)

/** The period's money in and out, before the filters: [inCents] is income, [outCents] net expenses (refunds netted). */
data class ActivityTotals(
    val inCents: Long,
    val outCents: Long,
)

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    /**
     * [items] are the filtered and sorted transactions; [days] groups them under day headers and is
     * empty when [filters] sort by amount (the list then stays flat).
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val items: List<Transaction>,
        val days: List<DaySection<Transaction>>,
        val totals: ActivityTotals,
        val filters: ActivityFilters,
    ) : ActivityUiState

    data class Error(
        val message: UiText,
    ) : ActivityUiState
}
