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
import com.joaoeoneves.fintrack.domain.model.MAX_WORKING_DAYS
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

/**
 * "Working days in Oct: 22 (2 holidays, 3 days off)"; tap to edit. Shows "(edited)" instead when overridden.
 */
@Composable
fun WorkingDaysRow(
    state: WorkingDaysState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value =
        when {
            state.overridden -> stringResource(R.string.plan_working_days_edited, state.days)
            state.daysOff > 0 ->
                stringResource(
                    R.string.plan_working_days_value_off,
                    state.days,
                    pluralStringResource(R.plurals.plan_holidays_count, state.holidays, state.holidays),
                    pluralStringResource(R.plurals.plan_days_off_count, state.daysOff, state.daysOff),
                )
            else -> pluralStringResource(R.plurals.plan_working_days_value, state.holidays, state.days, state.holidays)
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

/**
 * Dialog for the month's working days and days off. The working-days stepper (0 to [MAX_WORKING_DAYS]) follows
 * "calendar count minus days off" until it is edited by hand; saving a value equal to that automatic count
 * clears the override, and "Reset to automatic" is offered while one is set.
 */
@Composable
fun WorkingDaysDialog(
    state: WorkingDaysState,
    onSave: (month: YearMonth, override: Int?, daysOff: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var override by remember { mutableStateOf(state.days.takeIf { state.overridden }) }
    var daysOff by remember { mutableIntStateOf(state.daysOff) }
    val automatic = maxOf(0, state.calendarDays - daysOff)
    val hintRes = if (override == null) R.string.plan_working_days_hint_auto else R.string.plan_working_days_hint
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_working_days_title, monthName(state))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
                Text(
                    text = stringResource(R.string.plan_working_days_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = FinTrackTheme.colors.textSecondary,
                )
                Stepper(
                    value = override ?: automatic,
                    onChange = { override = it.takeIf { value -> value != automatic } },
                    max = MAX_WORKING_DAYS,
                    labels = StepperLabels(R.string.cd_fewer_working_days, R.string.cd_more_working_days),
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
                if (override != null) {
                    DialogTextButton(
                        text = stringResource(R.string.plan_working_days_reset),
                        onClick = { override = null },
                    )
                }
                DaysOffFields(
                    daysOff = daysOff,
                    onChange = { daysOff = it },
                )
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    onSave(state.month, override?.takeIf { it != automatic }, daysOff)
                    onDismiss()
                },
            )
        },
        dismissButton = { DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

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
