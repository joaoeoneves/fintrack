package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.IncomeSourceChip
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
    val envelope: String?,
    val query: String,
    val sort: SortOption,
    val incomeSource: String? = null,
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
        val filters: ActivityFilters,
        val analytics: ActivityAnalytics,
        val categoryScope: Set<CategoryId>? = null,
        val incomeSources: List<IncomeSourceChip> = emptyList(),
        /** Per expense id: the share (1..100) of it refunded through linked refunds; absent when none. */
        val refundedPercent: Map<String, Int> = emptyMap(),
    ) : ActivityUiState

    data class Error(
        val message: UiText,
    ) : ActivityUiState
}
