package com.joaoeoneves.fintrack.ui.plan

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** "Working days in Oct: 22 (2 holidays)"; tap to edit. Shows "(edited)" instead when overridden. */
@Composable
fun WorkingDaysRow(
    state: WorkingDaysState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value =
        if (state.overridden) {
            stringResource(R.string.plan_working_days_edited, state.days)
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
private fun monthName(state: WorkingDaysState): String = formatShortMonthName(state.month, currentLocale())

/**
 * Stepper dialog for the month's working days (0 to [MAX_WORKING_DAYS]). Saving a value equal to the
 * calendar count clears the override; "Reset to automatic" is offered while one is set.
 */
@Composable
fun WorkingDaysDialog(
    state: WorkingDaysState,
    onSave: (month: java.time.YearMonth, days: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by remember { mutableIntStateOf(state.days) }
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_working_days_title, monthName(state))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
                Stepper(value = days, onChange = { days = it })
                Text(
                    text = stringResource(R.string.plan_working_days_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
                if (state.overridden) {
                    TextButton(
                        onClick = {
                            onSave(state.month, null)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.plan_working_days_reset)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(state.month, days.takeIf { it != state.calendarDays })
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_fewer_working_days),
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
            contentDescription = stringResource(R.string.cd_more_working_days),
            onClick = { onChange(value + 1) },
            enabled = value < MAX_WORKING_DAYS,
        )
    }
}
