package com.joaoeoneves.fintrack.ui.spending

import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface SpendingTabUiState {
    data object Loading : SpendingTabUiState

    /**
     * [analytics] covers the whole period; [history] is the 13-month window ending at the period's last month (`null`
     * until it has loaded).
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val analytics: ActivityAnalytics,
        val groups: List<SpendingGroup>,
        val history: ActivityHistory?,
    ) : SpendingTabUiState

    data class Error(
        val message: UiText,
    ) : SpendingTabUiState
}

/** One expense group (envelope) of the tab's inline "Groups" list. */
data class SpendingGroup(
    val id: String,
    val name: String,
)
