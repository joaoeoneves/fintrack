package com.joaoeoneves.fintrack.ui.common.period

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import java.time.Month
import java.time.YearMonth

// How many years back from the current one the month picker lets the user browse.
private const val MONTH_PICKER_YEARS_BACK = 20

// Months per row of the 3-column, 4-row month grid.
private const val MONTHS_PER_ROW = 3

/**
 * Lets the user pick a calendar month: a year header with previous/next arrows over a grid of the
 * year's months. Months after [currentMonth] are disabled, and browsing is bounded to
 * `[currentMonth.year - 20, currentMonth.year]`. Tapping a month selects it immediately.
 */
@Composable
fun MonthPickerDialog(
    initialMonth: YearMonth,
    currentMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val minYear = currentMonth.year - MONTH_PICKER_YEARS_BACK
    var year by rememberSaveable { mutableIntStateOf(initialMonth.year.coerceIn(minYear, currentMonth.year)) }

    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                YearHeader(
                    year = year,
                    canGoBack = year > minYear,
                    canGoForward = year < currentMonth.year,
                    onPrevious = { year-- },
                    onNext = { year++ },
                )
                MonthGrid(
                    year = year,
                    selectedMonth = initialMonth,
                    currentMonth = currentMonth,
                    onMonthSelected = onMonthSelected,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun YearHeader(
    year: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_previous_year),
            onClick = onPrevious,
            enabled = canGoBack,
        )
        Text(text = year.toString(), style = MaterialTheme.typography.titleMedium)
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_next_year),
            onClick = onNext,
            enabled = canGoForward,
        )
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    selectedMonth: YearMonth,
    currentMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
) {
    val locale = currentLocale()
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Month.entries.chunked(MONTHS_PER_ROW).forEach { rowMonths ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowMonths.forEach { month ->
                    val yearMonth = YearMonth.of(year, month)
                    val isSelected = yearMonth == selectedMonth
                    TextButton(
                        onClick = { onMonthSelected(yearMonth) },
                        enabled = yearMonth <= currentMonth,
                        shape = CircleShape,
                        colors =
                            if (isSelected) {
                                ButtonDefaults.textButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = formatShortMonthName(yearMonth, locale),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
