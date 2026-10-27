package com.joaoeoneves.fintrack.ui.common.period

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.finTrackDatePickerColors
import com.joaoeoneves.fintrack.ui.common.toUtcMillis
import com.joaoeoneves.fintrack.ui.common.utcMillisToLocalDate
import java.time.LocalDate

/** Only dates up to (and including) [today] can be picked; there is nothing to show after it. */
@OptIn(ExperimentalMaterial3Api::class)
private class UpToTodaySelectableDates(
    private val today: LocalDate,
) : SelectableDates {
    private val todayUtcMillis = today.toUtcMillis()

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis

    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

/**
 * Lets the user pick an inclusive custom date range ending no later than [today], preselecting
 * [initialRange] when the current period already is one. OK is only enabled once both ends are set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialRange: Period.Custom?,
    today: LocalDate,
    onRangeSelected: (Period.Custom) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectableDates = remember(today) { UpToTodaySelectableDates(today) }
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialRange?.start?.toUtcMillis(),
            initialSelectedEndDateMillis = initialRange?.endInclusive?.toUtcMillis(),
            selectableDates = selectableDates,
        )
    val startMillis = state.selectedStartDateMillis
    val endMillis = state.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = finTrackDatePickerColors(),
        confirmButton = {
            TextButton(
                onClick = {
                    if (startMillis != null && endMillis != null) {
                        val start = utcMillisToLocalDate(startMillis)
                        onRangeSelected(Period.Custom(start, utcMillisToLocalDate(endMillis)))
                    }
                },
                enabled = startMillis != null && endMillis != null,
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DateRangePicker(
            state = state,
            colors = finTrackDatePickerColors(),
            title = {
                Text(
                    text = stringResource(R.string.period_select_dates),
                    modifier = Modifier.padding(start = 64.dp, end = 12.dp, top = 16.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}
