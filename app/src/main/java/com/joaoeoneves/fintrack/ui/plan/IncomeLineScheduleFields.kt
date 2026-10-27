package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

// Bounds of the year stepper of a one-off line.
private const val MIN_ONCE_YEAR = 2000
private const val MAX_ONCE_YEAR = 2099

/**
 * The basis and frequency tabs of an income line plus the month selection its frequency needs: "not
 * paid in" chips (monthly), a paid-in month (yearly) or a year and month (one-off). [preview], when
 * set, is shown under the basis tabs.
 */
@Composable
internal fun IncomeScheduleFields(
    schedule: IncomeSchedule,
    onChange: (IncomeSchedule) -> Unit,
    fallbackMonth: YearMonth,
    preview: String?,
) {
    SegmentedTextTabs(
        options = IncomeBasis.entries,
        selected = schedule.basis,
        label = { stringResource(basisLabel(it)) },
        onSelect = { onChange(schedule.copy(basis = it)) },
    )
    if (preview != null) {
        Text(text = preview, style = MaterialTheme.typography.bodyMedium, color = FinTrackTheme.colors.textSecondary)
    }
    SegmentedTextTabs(
        options = IncomeFrequency.entries,
        selected = schedule.frequency,
        label = { stringResource(frequencyLabel(it)) },
        onSelect = { onChange(schedule.copy(frequency = it).normalized(fallbackMonth)) },
    )
    when (schedule.frequency) {
        IncomeFrequency.MONTHLY ->
            MonthChips(
                title = stringResource(R.string.plan_not_paid_in),
                selected = schedule.skipMonths,
                onToggle = { month ->
                    val skip = schedule.skipMonths
                    onChange(schedule.copy(skipMonths = if (month in skip) skip - month else skip + month))
                },
            )
        IncomeFrequency.YEARLY ->
            MonthChips(
                title = stringResource(R.string.plan_pay_month),
                selected = setOfNotNull(schedule.monthOfYear),
                onToggle = { onChange(schedule.copy(monthOfYear = it)) },
            )
        IncomeFrequency.ONCE ->
            OnceMonthPicker(selected = schedule.yearMonth ?: fallbackMonth) { onChange(schedule.copy(yearMonth = it)) }
    }
}

private fun basisLabel(basis: IncomeBasis): Int =
    when (basis) {
        IncomeBasis.FIXED -> R.string.plan_basis_fixed
        IncomeBasis.PER_WORKING_DAY -> R.string.plan_basis_per_day
    }

private fun frequencyLabel(frequency: IncomeFrequency): Int =
    when (frequency) {
        IncomeFrequency.MONTHLY -> R.string.plan_frequency_monthly
        IncomeFrequency.YEARLY -> R.string.plan_frequency_yearly
        IncomeFrequency.ONCE -> R.string.plan_frequency_once
    }

/** A year stepper over the twelve month chips of a one-off line's year-month. */
@Composable
private fun OnceMonthPicker(
    selected: YearMonth,
    onSelect: (YearMonth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_previous_year),
            onClick = { onSelect(selected.minusYears(1)) },
            enabled = selected.year > MIN_ONCE_YEAR,
        )
        Text(text = selected.year.toString(), style = MaterialTheme.typography.titleMedium)
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_next_year),
            onClick = { onSelect(selected.plusYears(1)) },
            enabled = selected.year < MAX_ONCE_YEAR,
        )
    }
    MonthChips(title = null, selected = setOf(selected.monthValue), onToggle = { onSelect(selected.withMonth(it)) })
}

/** Twelve month chips under an optional [title]; [onToggle] gets the tapped month number (1..12). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthChips(
    title: String?,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val locale = currentLocale()
    if (title != null) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = FinTrackTheme.colors.textSecondary)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        for (month in MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR) {
            CategoryChip(
                label = formatShortMonthName(YearMonth.of(MIN_ONCE_YEAR, month), locale),
                selected = month in selected,
                onClick = { onToggle(month) },
            )
        }
    }
}
