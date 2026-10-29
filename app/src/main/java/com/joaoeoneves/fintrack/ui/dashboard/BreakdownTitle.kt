package com.joaoeoneves.fintrack.ui.dashboard

import androidx.annotation.StringRes
import com.joaoeoneves.fintrack.R

/** The category breakdown's heading: the planned total, "Total outflow" (with its big total) or plain "By category". */
@StringRes
internal fun breakdownTitleRes(
    planned: Boolean,
    showTotal: Boolean,
): Int =
    when {
        planned -> R.string.dashboard_planned_per_month
        showTotal -> R.string.dashboard_total_outflow
        else -> R.string.insights_by_category
    }
