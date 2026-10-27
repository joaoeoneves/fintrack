package com.joaoeoneves.fintrack.ui.insights

import com.joaoeoneves.fintrack.domain.model.GroupProgress
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.dashboard.CategoryTotal

/**
 * What the category breakdown needs: [categoryTotals] actually spent in the period, the
 * [plannedSlices] of the plan, and whether the period has any expense at all ([hasExpenses]). [groups] is the
 * spend per category group over the period (the implicit "Other" last); the Group view needs a real group.
 */
data class InsightsBreakdown(
    val categoryTotals: List<CategoryTotal>,
    val plannedSlices: List<CategoryTotal>,
    val hasExpenses: Boolean,
    val groups: List<GroupProgress> = emptyList(),
) {
    /** Whether the user has defined any group (otherwise a Group view would only say "Other"). */
    val hasGroups: Boolean get() = groups.any { it.group != null }
}

sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val breakdown: InsightsBreakdown,
        val split: SpendingSplit = SpendingSplit(0L, 0L, 0L),
    ) : InsightsUiState {
        /** `true` when there is neither spending nor a plan to show. */
        val isEmpty: Boolean get() = !breakdown.hasExpenses && breakdown.plannedSlices.isEmpty()
    }

    data class Error(
        val message: UiText,
    ) : InsightsUiState
}
