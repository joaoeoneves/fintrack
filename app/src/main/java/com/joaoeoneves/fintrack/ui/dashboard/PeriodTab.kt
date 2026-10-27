package com.joaoeoneves.fintrack.ui.dashboard

import androidx.annotation.StringRes
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange

/** The quick period choices under the dashboard chart. */
enum class PeriodTab(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    ONE_MONTH(R.string.period_tab_1m, R.string.cd_period_tab_1m),
    THREE_MONTHS(R.string.period_tab_3m, R.string.cd_period_tab_3m),
    SIX_MONTHS(R.string.period_tab_6m, R.string.cd_period_tab_6m),
    ONE_YEAR(R.string.period_tab_1y, R.string.cd_period_tab_1y),
}

/** The tab matching this period; both the current and a past month read as 1M, a custom range as none. */
fun Period.toTab(): PeriodTab? =
    when (this) {
        Period.CurrentMonth, is Period.Month -> PeriodTab.ONE_MONTH
        is Period.Rolling ->
            when (range) {
                TimeRange.THREE_MONTHS -> PeriodTab.THREE_MONTHS
                TimeRange.SIX_MONTHS -> PeriodTab.SIX_MONTHS
                TimeRange.ONE_YEAR -> PeriodTab.ONE_YEAR
            }
        is Period.Custom -> null
    }

/** The period selected by tapping this tab. */
fun PeriodTab.toPeriod(): Period =
    when (this) {
        PeriodTab.ONE_MONTH -> Period.CurrentMonth
        PeriodTab.THREE_MONTHS -> Period.Rolling(TimeRange.THREE_MONTHS)
        PeriodTab.SIX_MONTHS -> Period.Rolling(TimeRange.SIX_MONTHS)
        PeriodTab.ONE_YEAR -> Period.Rolling(TimeRange.ONE_YEAR)
    }
