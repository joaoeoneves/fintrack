package com.joaoeoneves.fintrack.ui.common.period

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.nextMonth
import com.joaoeoneves.fintrack.domain.model.nextYear
import com.joaoeoneves.fintrack.domain.model.previousMonth
import com.joaoeoneves.fintrack.domain.model.previousYear
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock

/**
 * The selected [Period] of a screen, persisted in [savedStateHandle] under [key] so it survives
 * process death. Shared by the dashboard and the expense/income list ViewModels.
 *
 * Always stores the encoded token, never a resolved month: a stored [Period.CurrentMonth] keeps
 * meaning "this month" after the calendar rolls over. Consumers decode [token] with a fresh
 * `clock.instant()` each time they (re)subscribe.
 */
class PeriodSelection(
    private val savedStateHandle: SavedStateHandle,
    private val key: String,
    initialToken: String,
    private val clock: Clock,
    private val monthsAhead: Int = 0,
) {
    val token: StateFlow<String> = savedStateHandle.getStateFlow(key, initialToken)

    /** Selects [period]; a `Month` that is current (or future) is normalized to `CurrentMonth`. */
    fun select(period: Period) {
        val normalized =
            if (period is Period.Month) {
                Period.ofMonth(period.yearMonth, clock.instant(), clock.zone, monthsAhead)
            } else {
                period
            }
        savedStateHandle[key] = normalized.encode()
    }

    /** Moves a month selection one month back (a year selection one year); no-op for rolling/custom periods. */
    fun previous() {
        val now = clock.instant()
        val period = current()
        select(if (period is Period.Year) period.previousYear() else period.previousMonth(now, clock.zone))
    }

    /** Moves a selection forward by its scale, never past the current month or year (plus [monthsAhead]). */
    fun next() {
        val now = clock.instant()
        val period = current()
        select(
            if (period is Period.Year) {
                period.nextYear(now, clock.zone, monthsAhead)
            } else {
                period.nextMonth(now, clock.zone, monthsAhead)
            },
        )
    }

    /** Switches to [scale] keeping the anchor, see [switchedTo]. */
    fun selectScale(scale: PeriodScale) = select(current().switchedTo(scale, clock.instant(), clock.zone, monthsAhead))

    private fun current(): Period = decodePeriod(token.value, clock.instant(), clock.zone, monthsAhead)
}

/** This period if it is a month or a year, otherwise the current month: for screens with the "Mês | Ano" switch. */
fun Period.asMonthOrYearPeriod(): Period = if (this is Period.Year) this else asMonthPeriod()

/** This period if it is a month, otherwise the current month: for screens that only know months. */
fun Period.asMonthPeriod(): Period {
    val isMonth = this is Period.CurrentMonth || this is Period.Month
    return if (isMonth) this else Period.CurrentMonth
}
