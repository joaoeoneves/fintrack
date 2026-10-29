package com.joaoeoneves.fintrack.ui.common.period

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackDropdownMenu
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackMenuItem
import com.joaoeoneves.fintrack.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId

/** The entries of the period menu, in display order. */
private enum class PeriodMenuOption {
    THIS_MONTH,
    CHOOSE_MONTH,
    LAST_3_MONTHS,
    LAST_6_MONTHS,
    LAST_12_MONTHS,
    CUSTOM_RANGE,
}

/** The menu entries of a month-only selector (Home): no rolling or custom ranges. */
private val MONTH_ONLY_OPTIONS = listOf(PeriodMenuOption.THIS_MONTH, PeriodMenuOption.CHOOSE_MONTH)

/** Which picker dialog, if any, the selector is showing. */
private enum class PeriodDialog { NONE, MONTH, DATE_RANGE }

private fun Period.menuOption(): PeriodMenuOption =
    when (this) {
        Period.CurrentMonth -> PeriodMenuOption.THIS_MONTH
        is Period.Month -> PeriodMenuOption.CHOOSE_MONTH
        is Period.Custom -> PeriodMenuOption.CUSTOM_RANGE
        is Period.Rolling ->
            when (range) {
                TimeRange.THREE_MONTHS -> PeriodMenuOption.LAST_3_MONTHS
                TimeRange.SIX_MONTHS -> PeriodMenuOption.LAST_6_MONTHS
                TimeRange.ONE_YEAR -> PeriodMenuOption.LAST_12_MONTHS
            }
    }

@Composable
private fun PeriodMenuOption.label(): String =
    when (this) {
        PeriodMenuOption.THIS_MONTH -> stringResource(R.string.period_this_month)
        PeriodMenuOption.CHOOSE_MONTH -> stringResource(R.string.period_choose_month)
        PeriodMenuOption.LAST_3_MONTHS -> rollingRangeLabel(TimeRange.THREE_MONTHS)
        PeriodMenuOption.LAST_6_MONTHS -> rollingRangeLabel(TimeRange.SIX_MONTHS)
        PeriodMenuOption.LAST_12_MONTHS -> rollingRangeLabel(TimeRange.ONE_YEAR)
        PeriodMenuOption.CUSTOM_RANGE -> stringResource(R.string.period_custom_range)
    }

/**
 * Period picker shared by the dashboard and the expense/income lists: previous/next month arrows
 * (month periods only) around a tappable label that opens a menu of every period kind (only the two
 * month entries when [monthOnly]).
 */
@Suppress("LongParameterList") // shared picker API; every option is independent and defaulted
@Composable
fun PeriodSelector(
    period: Period,
    canGoToNextMonth: Boolean,
    actions: PeriodActions,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    monthOnly: Boolean = false,
) {
    var dialog by rememberSaveable { mutableStateOf(PeriodDialog.NONE) }
    val isMonthPeriod = period is Period.CurrentMonth || period is Period.Month

    PeriodHeader(
        navigation =
            PeriodNavigation(
                onPrevious = actions.onPrevious.takeIf { isMonthPeriod },
                onNext = actions.onNext.takeIf { isMonthPeriod },
                canGoNext = canGoToNextMonth,
            ),
        modifier = modifier,
        alignment = alignment,
    ) {
        PeriodMenuButton(
            period = period,
            options = if (monthOnly) MONTH_ONLY_OPTIONS else PeriodMenuOption.entries,
            onOptionSelected = { option ->
                when (option) {
                    PeriodMenuOption.THIS_MONTH -> actions.onSelect(Period.CurrentMonth)
                    PeriodMenuOption.CHOOSE_MONTH -> dialog = PeriodDialog.MONTH
                    PeriodMenuOption.LAST_3_MONTHS -> actions.onSelect(Period.Rolling(TimeRange.THREE_MONTHS))
                    PeriodMenuOption.LAST_6_MONTHS -> actions.onSelect(Period.Rolling(TimeRange.SIX_MONTHS))
                    PeriodMenuOption.LAST_12_MONTHS -> actions.onSelect(Period.Rolling(TimeRange.ONE_YEAR))
                    PeriodMenuOption.CUSTOM_RANGE -> dialog = PeriodDialog.DATE_RANGE
                }
            },
        )
    }

    PeriodPickerDialogs(
        dialog = dialog,
        period = period,
        onSelect = actions.onSelect,
        onDismiss = { dialog = PeriodDialog.NONE },
    )
}

@Composable
private fun PeriodMenuButton(
    period: Period,
    options: List<PeriodMenuOption>,
    onOptionSelected: (PeriodMenuOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = period.menuOption()

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(start = Dimens.Space12, end = Dimens.Space4, top = Dimens.Space8, bottom = Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = periodLabel(period),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.cd_change_period))
        }
        FinTrackDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                FinTrackMenuItem(
                    text = option.label(),
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                    selected = option == selectedOption,
                )
            }
        }
    }
}

@Composable
private fun PeriodPickerDialogs(
    dialog: PeriodDialog,
    period: Period,
    onSelect: (Period) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    when (dialog) {
        PeriodDialog.NONE -> Unit
        PeriodDialog.MONTH -> {
            val currentMonth = currentYearMonth(now, zone)
            MonthPickerDialog(
                initialMonth = period.anchorMonth(now, zone) ?: currentMonth,
                currentMonth = currentMonth,
                onMonthSelected = { month ->
                    onSelect(Period.ofMonth(month, Instant.now(), zone))
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        PeriodDialog.DATE_RANGE ->
            DateRangePickerDialog(
                initialRange = period as? Period.Custom,
                today = now.atZone(zone).toLocalDate(),
                onRangeSelected = { range ->
                    onSelect(range)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
    }
}
