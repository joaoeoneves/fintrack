package com.joaoeoneves.fintrack.ui.common.period

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.theme.Dimens
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** The two scales of the "Mês | Ano" switch every screen with figures offers. */
enum class PeriodScale { MONTH, YEAR }

/** [PeriodScale.YEAR] for a calendar-year period, [PeriodScale.MONTH] for every other one. */
val Period.scale: PeriodScale get() = if (this is Period.Year) PeriodScale.YEAR else PeriodScale.MONTH

/**
 * This period moved to [target], keeping the anchor: a month becomes its year; a year becomes the current month
 * (the current year) or January (a past year). A period already on [target] is returned unchanged.
 */
fun Period.switchedTo(
    target: PeriodScale,
    now: Instant,
    zone: ZoneId,
    monthsAhead: Int = 0,
): Period {
    if (scale == target) return this
    return when (target) {
        PeriodScale.YEAR -> Period.Year((anchorMonth(now, zone) ?: currentYearMonth(now, zone)).year)
        PeriodScale.MONTH -> {
            val year = (this as Period.Year).year
            val current = currentYearMonth(now, zone)
            if (year >= current.year) Period.CurrentMonth else Period.ofMonth(YearMonth.of(year, 1), now, zone, monthsAhead)
        }
    }
}

/** The "Mês | Ano" switch: a two-option segmented control. */
@Composable
fun PeriodScaleSwitch(
    selected: PeriodScale,
    onSelect: (PeriodScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedTextTabs(
        options = PeriodScale.entries,
        selected = selected,
        label = {
            stringResource(if (it == PeriodScale.MONTH) R.string.period_scale_month else R.string.period_scale_year)
        },
        onSelect = onSelect,
        modifier = modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space4),
    )
}
