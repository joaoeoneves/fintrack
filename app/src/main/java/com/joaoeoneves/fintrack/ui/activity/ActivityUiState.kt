package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.common.DaySection
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.YearMonth

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
    val envelope: String?,
    val query: String,
    val sort: SortOption,
    val incomeSource: String? = null,
)

/**
 * The money in and out of the visible list: [inCents] is income, [outCents] net expenses (refunds netted, savings
 * included) of which [spendingCents] is spending and [savedCents] is put into savings.
 */
data class ActivityTotals(
    val inCents: Long,
    val outCents: Long,
    val spendingCents: Long = outCents,
    val savedCents: Long = 0L,
)

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    /**
     * [items] are the filtered and sorted transactions; [days] groups them under day headers and is
     * empty when [filters] sort by amount (the list then stays flat). [analytics] covers the whole period before the
     * filters; [categoryScope] limits the category chips to the selected envelope's categories (`null`: no limit).
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val items: List<Transaction>,
        val days: List<DaySection<Transaction>>,
        val totals: ActivityTotals,
        val filters: ActivityFilters,
        val analytics: ActivityAnalytics,
        val categoryScope: Set<CategoryId>? = null,
        val history: ActivityHistory? = null,
        val shiftedIncomes: Map<String, YearMonth> = emptyMap(),
    ) : ActivityUiState

    data class Error(
        val message: UiText,
    ) : ActivityUiState
}
