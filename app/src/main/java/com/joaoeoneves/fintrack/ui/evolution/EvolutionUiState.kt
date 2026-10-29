package com.joaoeoneves.fintrack.ui.evolution

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.common.UiText

/** The two sides the Evolution screen switches between. */
enum class EvolutionType {
    EXPENSE,
    INCOME,
}

sealed interface EvolutionUiState {
    data object Loading : EvolutionUiState

    /**
     * [analytics] covers the whole period; [history] is the 13-month window ending at the period's last month (`null`
     * until it has loaded).
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val type: EvolutionType,
        val analytics: ActivityAnalytics,
        val history: ActivityHistory?,
    ) : EvolutionUiState

    data class Error(
        val message: UiText,
    ) : EvolutionUiState
}
