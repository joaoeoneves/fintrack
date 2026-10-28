package com.joaoeoneves.fintrack.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.Instant
import java.time.ZoneId

/**
 * A read-only date field that opens a [DatePickerDialog] when tapped anywhere on the field body,
 * not just on the trailing icon; [maxDate], when set, is the latest day that can be picked. Used by the Add/Edit
 * Expense and Add/Edit Income date fields and the "Received" sheet.
 */
@Suppress("LongParameterList") // independent optional field options (zone, label, latest pickable day)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyDateField(
    date: Instant,
    onDateSelected: (Instant) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    @StringRes labelRes: Int = R.string.field_date,
    maxDate: Instant? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val displayDateFormatter = rememberLocaleAwareDateFormatter().withZone(zone)

    val label = stringResource(labelRes)
    val formattedDate = displayDateFormatter.format(date)
    val changeDateCd = stringResource(R.string.cd_change_date)

    // One merged, clickable node: the whole row opens the picker. The trailing icon is purely
    // decorative; TalkBack reads "Change date" plus the "<label>, <date>" state.
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.SettingsRowMinHeight)
                .clickable(role = Role.Button) { showDatePicker = true }
                .semantics(mergeDescendants = true) {
                    contentDescription = changeDateCd
                    stateDescription = "$label, $formattedDate"
                },
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(text = formattedDate, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.padding(Dimens.Space12),
        )
    }

    if (showDatePicker) {
        PickDateDialog(
            date = date,
            zone = zone,
            maxDate = maxDate,
            onDateSelected = onDateSelected,
            onDismiss = { showDatePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(
    date: Instant,
    zone: ZoneId,
    maxDate: Instant?,
    onDateSelected: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = date.toDatePickerMillis(zone),
            selectableDates = maxDate?.let { NotAfter(it.toDatePickerMillis(zone)) } ?: DatePickerDefaults.AllDates,
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = finTrackDatePickerColors(),
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onDateSelected(datePickerMillisToInstant(millis, date, zone))
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = datePickerState, colors = finTrackDatePickerColors())
    }
}

/** Dates up to [maxMillis] (a picker UTC midnight) can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
private class NotAfter(
    private val maxMillis: Long,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxMillis
}

/** Date-picker colours on the soft surface tone with the monochrome selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun finTrackDatePickerColors(): DatePickerColors =
    DatePickerDefaults.colors(
        containerColor = FinTrackTheme.colors.surface2,
        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
        todayContentColor = MaterialTheme.colorScheme.onSurface,
        todayDateBorderColor = MaterialTheme.colorScheme.onSurface,
        selectedYearContainerColor = MaterialTheme.colorScheme.primary,
        selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
        dayInSelectionRangeContainerColor = FinTrackTheme.colors.hairline,
        dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSurface,
    )
