package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
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
    var date by remember { mutableStateOf(today) }
    var amountCents by remember { mutableLongStateOf(editableAmountCents ?: 0L) }
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
                MoneyTextField(
                    cents = amountCents,
                    onCentsChange = {
                        amountCents = it
                        amountError = false
                    },
                    label = stringResource(R.string.plan_received_amount),
                    isError = amountError,
                    supportingText = stringResource(R.string.error_invalid_amount).takeIf { amountError },
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
                    if (amountCents > 0L) onConfirm(date, amountCents) else amountError = true
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

/**
 * The discreet "mark as received" control of an income row: an outlined check that records the row on tap, or, once
 * [received], a filled check in the positive colour that asks to undo.
 */
@Composable
fun ReceivedCheckButton(
    received: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (received) R.string.income_undo_received else R.string.income_mark_received
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (received) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
            contentDescription = stringResource(description),
            tint = if (received) FinTrackTheme.colors.positive else FinTrackTheme.colors.textSecondary,
        )
    }
}

/** Confirmation before taking a received row back to not received. */
@Composable
fun UndoReceivedDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.income_undo_title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_undo)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
