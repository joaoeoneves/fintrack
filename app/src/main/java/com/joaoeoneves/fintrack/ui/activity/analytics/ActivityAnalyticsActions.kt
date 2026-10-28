package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions

/** What the header lets the user change: the period, the envelope filter and (from the breakdown) the category. */
data class ActivityAnalyticsActions(
    val period: PeriodActions,
    val onEnvelopeSelected: (String?) -> Unit,
    val onCategorySelected: (Category) -> Unit,
)
