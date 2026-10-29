package com.joaoeoneves.fintrack.ui.dashboard

import androidx.annotation.StringRes
import com.joaoeoneves.fintrack.R

/** The category breakdown's heading: "Total outflow" (with its big total) or plain "By category". */
@StringRes
internal fun breakdownTitleRes(showTotal: Boolean): Int =
    when {
        showTotal -> R.string.dashboard_total_outflow
        else -> R.string.insights_by_category
    }
