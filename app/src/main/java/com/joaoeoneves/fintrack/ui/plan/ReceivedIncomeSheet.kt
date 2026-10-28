package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.expense.addedit.decimalSeparatorFor
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText
import com.joaoeoneves.fintrack.ui.theme.Dimens
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Bottom sheet to mark an income row as received: the day it arrived (today by default, never later) and, only
 * when the row is a single line ([editableAmountCents] set), the amount actually received.
 */
@Composable
fun ReceivedIncomeSheet(
    title: String,
    today: LocalDate,
    editableAmountCents: Long?,
    onConfirm: (date: LocalDate, amountCents: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val decimalSeparator = decimalSeparatorFor(currentLocale())
    var date by remember { mutableStateOf(today) }
    var amountText by remember { mutableStateOf(editableAmountCents?.toAmountInputText(decimalSeparator).orEmpty()) }
    var amountError by remember { mutableStateOf(false) }
    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            ReadOnlyDateField(
                date = date.atTime(LocalTime.NOON).atZone(zone).toInstant(),
                onDateSelected = { date = it.atZone(zone).toLocalDate().coerceAtMost(today) },
                zone = zone,
                maxDate = today.atTime(LocalTime.NOON).atZone(zone).toInstant(),
            )
            if (editableAmountCents != null) {
                MinimalTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = stringResource(R.string.plan_received_amount),
                    isError = amountError,
                    supportingText = { if (amountError) Text(stringResource(R.string.error_invalid_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PrimaryPillButton(
            text = stringResource(R.string.income_received),
            onClick = {
                if (editableAmountCents == null) {
                    onConfirm(date, null)
                } else {
                    parseAmountCents(amountText)
                        .onSuccess { onConfirm(date, it) }
                        .onFailure { amountError = true }
                }
            },
            modifier = Modifier.padding(top = Dimens.Space16),
        )
    }
}

/** A payday as "Fri 30 Oct" in the current language. */
@Composable
fun rememberPaydayText(payday: LocalDate): String {
    val locale = currentLocale()
    return remember(payday, locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale).format(payday) }
}

/** The day an income was received as "28 Oct" in the current language. */
@Composable
fun rememberReceivedText(date: LocalDate): String {
    val locale = currentLocale()
    return remember(date, locale) { DateTimeFormatter.ofPattern("d MMM", locale).format(date) }
}
