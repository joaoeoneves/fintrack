package com.joaoeoneves.fintrack.ui.common.period

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.nextMonth
import com.joaoeoneves.fintrack.domain.model.previousMonth
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
) {
    val token: StateFlow<String> = savedStateHandle.getStateFlow(key, initialToken)

    /** Selects [period]; a `Month` that is current (or future) is normalized to `CurrentMonth`. */
    fun select(period: Period) {
        val normalized =
            if (period is Period.Month) Period.ofMonth(period.yearMonth, clock.instant(), clock.zone) else period
        savedStateHandle[key] = normalized.encode()
    }

    /** Moves a month selection one month back; no-op for rolling/custom periods. */
    fun previous() = select(current().previousMonth(clock.instant(), clock.zone))

    /** Moves a month selection one month forward, never past the current month. */
    fun next() = select(current().nextMonth(clock.instant(), clock.zone))

    private fun current(): Period = decodePeriod(token.value, clock.instant(), clock.zone)
}
