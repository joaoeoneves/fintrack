package com.joaoeoneves.fintrack.ui.insights

import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.dashboard.CategoryTotal

/**
 * What the category breakdown needs: [categoryTotals] actually spent in the period, the
 * [plannedSlices] of the plan, and whether the period has any expense at all ([hasExpenses]). [envelopes] is the
 * spend per envelope over the period (the implicit "no envelope" row is its unenveloped part); the envelope view
 * needs a real envelope.
 */
data class InsightsBreakdown(
    val categoryTotals: List<CategoryTotal>,
    val plannedSlices: List<CategoryTotal>,
    val hasExpenses: Boolean,
    val envelopes: EnvelopesSummary = EnvelopesSummary(emptyList(), emptyList(), 0L),
) {
    /** Whether the user has defined any envelope (otherwise the envelope view would only say "No envelope"). */
    val hasEnvelopes: Boolean get() = envelopes.envelopes.isNotEmpty()
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
