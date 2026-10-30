package com.joaoeoneves.fintrack.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.carnivalWeekdayIn
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.MonthPickerDialog
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** "Working days in Oct: 22 (2 holidays, 3 days off)"; tap to edit the month's days off. */
@Composable
fun WorkingDaysRow(
    state: WorkingDaysState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value =
        if (state.daysOff > 0) {
            stringResource(
                R.string.plan_working_days_value_off,
                state.days,
                pluralStringResource(R.plurals.plan_holidays_count, state.holidays, state.holidays),
                pluralStringResource(R.plurals.plan_days_off_count, state.daysOff, state.daysOff),
            )
        } else {
            pluralStringResource(R.plurals.plan_working_days_value, state.holidays, state.days, state.holidays)
        }
    SettingsRow(
        title = stringResource(R.string.plan_working_days_title, monthName(state)),
        value = value,
        onClick = onClick,
        showChevron = true,
        modifier = modifier,
    )
}

@Composable
internal fun monthName(state: WorkingDaysState): String = formatShortMonthName(state.month, currentLocale())

/** The days off by type; each type's stepper may use what the other two leave of [MAX_DAYS_OFF]. */
internal data class DaysOffCounts(
    val vacation: Int = 0,
    val sick: Int = 0,
    val other: Int = 0,
) {
    val total: Int get() = vacation + sick + other

    val maxVacation: Int get() = MAX_DAYS_OFF - sick - other
    val maxSick: Int get() = MAX_DAYS_OFF - vacation - other
    val maxOther: Int get() = MAX_DAYS_OFF - vacation - sick
}

/** How many months a range of days off may reach beyond the opened month. */
internal const val DAYS_OFF_MAX_MONTHS_AHEAD = 23

/** The months from [start] to [until] inclusive; just [start] when [until] is not after it. */
internal fun monthsBetween(
    start: YearMonth,
    until: YearMonth,
): List<YearMonth> {
    val count = ChronoUnit.MONTHS.between(start, until).toInt()
    return (0..count.coerceIn(0, DAYS_OFF_MAX_MONTHS_AHEAD)).map { start.plusMonths(it.toLong()) }
}

/** Dialog for the month's days off by type, optionally applied to the following months too. */
@Composable
fun WorkingDaysDialog(
    state: WorkingDaysState,
    onSave: (months: List<YearMonth>, vacation: Int, sick: Int, other: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var counts by remember { mutableStateOf(DaysOffCounts(state.vacationDays, state.sickDays, state.otherDays)) }
    var until by remember { mutableStateOf(state.month) }
    val months = monthsBetween(state.month, until)
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_working_days_title, monthName(state))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
            ) {
                DaysOffFields(counts = counts, onChange = { counts = it })
                CarnivalShortcut(state.month, counts.other, counts.maxOther) { counts = counts.copy(other = it) }
                DaysOffUntil(state.month, until, months.size) { until = it }
                Text(
                    text =
                        stringResource(
                            R.string.plan_working_days_computed,
                            maxOf(0, state.calendarDays - counts.total),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    onSave(months, counts.vacation, counts.sick, counts.other)
                    onDismiss()
                },
                modifier = Modifier.testTag(TestTags.DAYS_OFF_SAVE),
            )
        },
        dismissButton = { DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

/** The optional "Until" month: the counts are then written to every month from the opened one to it. */
@Composable
private fun DaysOffUntil(
    month: YearMonth,
    until: YearMonth,
    monthCount: Int,
    onChange: (YearMonth) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = stringResource(R.string.plan_days_off_until),
        value = formatMonthLabel(until, currentLocale()),
        onClick = { picking = true },
        showChevron = true,
        modifier = Modifier.testTag(TestTags.DAYS_OFF_UNTIL),
    )
    if (monthCount > 1) {
        Text(
            text = pluralStringResource(R.plurals.plan_days_off_range_hint, monthCount, monthCount),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
    }
    if (picking) {
        MonthPickerDialog(
            initialMonth = until,
            currentMonth = month.plusMonths(DAYS_OFF_MAX_MONTHS_AHEAD.toLong()),
            onMonthSelected = {
                onChange(if (it.isBefore(month)) month else it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The three "days off" steppers: vacation, sick leave and other. */
@Composable
internal fun DaysOffFields(
    counts: DaysOffCounts,
    onChange: (DaysOffCounts) -> Unit,
) {
    DaysOffStepper(
        title = R.string.plan_days_off_vacation,
        value = counts.vacation,
        max = counts.maxVacation,
        labels = StepperLabels(R.string.cd_fewer_vacation_days, R.string.cd_more_vacation_days),
        onChange = { onChange(counts.copy(vacation = it)) },
    )
    DaysOffStepper(
        title = R.string.plan_days_off_sick,
        value = counts.sick,
        max = counts.maxSick,
        labels = StepperLabels(R.string.cd_fewer_sick_days, R.string.cd_more_sick_days),
        onChange = { onChange(counts.copy(sick = it)) },
    )
    DaysOffStepper(
        title = R.string.plan_days_off_other,
        value = counts.other,
        max = counts.maxOther,
        labels = StepperLabels(R.string.cd_fewer_other_days, R.string.cd_more_other_days),
        onChange = { onChange(counts.copy(other = it)) },
    )
}

@Composable
private fun DaysOffStepper(
    @StringRes title: Int,
    value: Int,
    max: Int,
    labels: StepperLabels,
    onChange: (Int) -> Unit,
) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
    )
    Stepper(value = value, onChange = onChange, max = max, labels = labels)
}

/**
 * The Carnival Tuesday as an optional day off: a switch (shown only in a month it falls on a weekday) that adds it
 * to, or takes it from, the "other" days ([other], at most [max]).
 */
@Composable
internal fun CarnivalShortcut(
    month: YearMonth,
    other: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val day = remember(month) { carnivalWeekdayIn(month) } ?: return
    var counted by remember { mutableStateOf(false) }
    SettingsSwitchRow(
        title = stringResource(R.string.plan_days_off_carnival, day.dayOfMonth),
        supportingText = stringResource(R.string.plan_days_off_carnival_hint),
        checked = counted,
        onCheckedChange = {
            counted = it
            onChange(withCarnival(other, it, max))
        },
    )
}

/** The "other" days after the Carnival shortcut is switched [on] (one more) or off (one fewer), within 0..[max]. */
internal fun withCarnival(
    other: Int,
    on: Boolean,
    max: Int = MAX_DAYS_OFF,
): Int = (if (on) other + 1 else other - 1).coerceIn(0, max.coerceAtLeast(0))

/** Content descriptions of a [Stepper]'s two buttons. */
internal data class StepperLabels(
    @param:StringRes val decrease: Int,
    @param:StringRes val increase: Int,
)

@Composable
internal fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    max: Int,
    labels: StepperLabels,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(labels.decrease),
            onClick = { onChange(value - 1) },
            enabled = value > 0,
        )
        Text(
            text = value.toString(),
            style = Amount,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Dimens.Space24),
        )
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(labels.increase),
            onClick = { onChange(value + 1) },
            enabled = value < max,
        )
    }
}
