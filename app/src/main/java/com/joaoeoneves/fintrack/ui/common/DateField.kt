package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import java.time.Instant
import java.time.ZoneOffset

/**
 * A read-only date field that opens a [DatePickerDialog] when tapped anywhere on the field body,
 * not just on the trailing icon. Used by the Add/Edit Expense and Add/Edit Income date fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyDateField(
    date: Instant,
    onDateSelected: (Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val displayDateFormatter = rememberLocaleAwareDateFormatter().withZone(ZoneOffset.UTC)

    val dateFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(dateFieldInteractionSource) {
        dateFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showDatePicker = true
            }
        }
    }

    OutlinedTextField(
        readOnly = true,
        value = displayDateFormatter.format(date),
        onValueChange = {},
        label = { Text(stringResource(R.string.field_date)) },
        interactionSource = dateFieldInteractionSource,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                val changeDateCd = stringResource(R.string.cd_change_date)
                Icon(Icons.Default.DateRange, contentDescription = changeDateCd)
            }
        },
        modifier = modifier,
    )

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = date.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis))
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
