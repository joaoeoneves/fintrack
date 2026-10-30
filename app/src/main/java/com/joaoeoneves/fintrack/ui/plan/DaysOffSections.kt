package com.joaoeoneves.fintrack.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.carnivalWeekdayIn
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

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

/** Dialog for the month's days off; the working-day count it implies is shown read-only. */
@Composable
fun WorkingDaysDialog(
    state: WorkingDaysState,
    onSave: (month: YearMonth, daysOff: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var daysOff by remember { mutableIntStateOf(state.daysOff) }
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_working_days_title, monthName(state))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
                DaysOffFields(daysOff = daysOff, onChange = { daysOff = it })
                CarnivalShortcut(state.month, daysOff) { daysOff = it }
                Text(
                    text = stringResource(R.string.plan_working_days_computed, maxOf(0, state.calendarDays - daysOff)),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    onSave(state.month, daysOff)
                    onDismiss()
                },
            )
        },
        dismissButton = { DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

/** The "days off this month" stepper. */
@Composable
internal fun DaysOffFields(
    daysOff: Int,
    onChange: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.plan_days_off_title),
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
    )
    Stepper(
        value = daysOff,
        onChange = onChange,
        max = MAX_DAYS_OFF,
        labels = StepperLabels(R.string.cd_fewer_days_off, R.string.cd_more_days_off),
    )
}

/**
 * The Carnival Tuesday as an optional day off: a switch (shown only in a month it falls on a weekday) that adds it
 * to, or takes it from, [daysOff].
 */
@Composable
internal fun CarnivalShortcut(
    month: YearMonth,
    daysOff: Int,
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
            onChange(withCarnival(daysOff, it))
        },
    )
}

/** The days off after the Carnival shortcut is switched [on] (one more) or off (one fewer), within 0..max. */
internal fun withCarnival(
    daysOff: Int,
    on: Boolean,
): Int = (if (on) daysOff + 1 else daysOff - 1).coerceIn(0, MAX_DAYS_OFF)

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
