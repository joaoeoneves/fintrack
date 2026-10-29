package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeMonth
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.amountIn
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.domain.model.payingLines
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.PendingOccurrenceRow
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.rememberFinTrackHaptics
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate

@Suppress("LongParameterList") // label/value and their colour, style and badge are independent row options
@Composable
private fun AmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle = Amount,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        }
        Text(text = value, style = valueStyle, color = valueColor)
        trailing?.invoke()
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FinTrackTheme.colors.textSecondary,
            )
        }
    }
}

/** What the expected-income card can trigger, bundled for detekt's LongParameterList. */
data class ExpectedIncomeActions(
    val onAdd: () -> Unit,
    val onEdit: (String) -> Unit,
    val onEditWorkingDays: () -> Unit,
    val onOpenSalaryProfile: () -> Unit,
    val onReceive: (row: IncomeRow, date: LocalDate, amountCents: Long?) -> Unit,
    val onUndoReceive: (row: IncomeRow) -> Unit,
)

/** What the expected-income card shows, bundled for detekt's LongParameterList. */
data class ExpectedIncomeModel(
    val rows: List<IncomeRow>,
    val canAdd: Boolean,
    val workingDays: WorkingDaysState,
    val receipts: IncomeReceipts,
    /** Keys of the manual rows that look like a repeat of the salary the profile already generates. */
    val duplicateKeys: Set<String> = emptySet(),
    /** What each income line pays in a month. */
    val incomeMonth: IncomeMonth = IncomeMonth.forMonth(workingDays.month, workingDays.days),
)

/**
 * The expected-income rows with what each pays in the selected month. A manual line opens its editor on tap; every
 * salary-profile row opens the salary profile (a row that does not pay this month is not tappable). The add button
 * is hidden at the line cap. Under the header sits the month's payday; a paying row of a past or current month has a
 * check that records it as received
 * today (the snackbar then offers to change the date or amount); once received it shows a green amount, the day it
 * was received and a filled check that undoes it after a confirmation.
 */
@Composable
fun ExpectedIncomeCard(
    model: ExpectedIncomeModel,
    actions: ExpectedIncomeActions,
    modifier: Modifier = Modifier,
) {
    val (rows, canAdd, workingDays) = model
    val receipts = model.receipts
    var undoing by remember { mutableStateOf<IncomeRow?>(null) }
    SettingsGroup(label = stringResource(R.string.plan_expected_income), modifier = modifier) {
        (receipts.receivedPayday ?: receipts.payday)?.let { payday ->
            val textRes =
                if (receipts.receivedPayday != null) R.string.plan_payday_received else R.string.plan_payday_upcoming
            Text(
                text = stringResource(textRes, rememberPaydayText(payday)),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
        val (managed, manual) = rows.partition { it.component != null }
        if (managed.isNotEmpty()) IncomeGroupHeader(stringResource(R.string.plan_from_salary_profile))
        managed.forEach { key(it.key) { IncomeRowItem(it, model, actions, onUndo = { undoing = it }) } }
        if (managed.isNotEmpty() && manual.isNotEmpty()) IncomeGroupHeader(stringResource(R.string.plan_other_income))
        manual.forEach { key(it.key) { IncomeRowItem(it, model, actions, onUndo = { undoing = it }) } }
        if (canAdd) {
            GhostButton(
                text = stringResource(R.string.plan_add_income_line),
                onClick = actions.onAdd,
                modifier = Modifier.padding(top = Dimens.Space12),
            )
        }
    }
    undoing?.let { row ->
        UndoReceivedDialog(
            onConfirm = {
                actions.onUndoReceive(row)
                undoing = null
            },
            onDismiss = { undoing = null },
        )
    }
}

/** The month's set-up rows: the salary profile and the working days, as a small settings-style group. */
@Composable
fun IncomeSetupGroup(
    workingDays: WorkingDaysState,
    actions: ExpectedIncomeActions,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.tab_configure), modifier = modifier) {
        SettingsRow(
            title = stringResource(R.string.plan_salary_profile_row),
            onClick = actions.onOpenSalaryProfile,
            showChevron = true,
        )
        Hairline()
        WorkingDaysRow(state = workingDays, onClick = actions.onEditWorkingDays)
    }
}

/** "Automatic · day 5" or "Automatic · last working day" for a row with a self-recording line; else `null`. */
@Composable
private fun autoRecordText(row: IncomeRow): String? {
    val line = row.lines.firstOrNull { it.autoRecord } ?: return null
    val payDay = line.payDay
    return if (payDay == null) {
        stringResource(R.string.plan_auto_last_working_day)
    } else {
        stringResource(R.string.plan_auto_day, payDay)
    }
}

@Composable
private fun IncomeGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Dimens.Space12),
    )
}

@Suppress("LongMethod") // one row layout with its receive/undo wiring
@Composable
private fun IncomeRowItem(
    row: IncomeRow,
    model: ExpectedIncomeModel,
    actions: ExpectedIncomeActions,
    onUndo: (IncomeRow) -> Unit,
) {
    val receipts = model.receipts
    val paysThisMonth = row.cents > 0L
    val tappable = row.component == null || paysThisMonth
    val receivedOn = receipts.receivedOn[row.key]
    val received = receivedOn != null
    val feedback = LocalReceiveFeedback.current
    val haptics = rememberFinTrackHaptics()
    val title = row.displayLabel()
    // Dated on the row's own pay day (last month's for a salary that pays the next month), never after today.
    val receiveDate = minOf(row.payDateIn(model.workingDays.month, model.incomeMonth), receipts.today)
    val snackbarText = stringResource(R.string.plan_received_on, rememberReceivedText(receiveDate))
    val undoLabel = stringResource(R.string.action_undo)
    val showCheck = paysThisMonth && (received || receipts.canReceive)
    if (showCheck && !received) {
        PendingIncomeRow(
            row = row,
            title = title,
            model = model,
            modifier = rowTapModifier(tappable, row, actions),
            onReceive = {
                haptics.success()
                actions.onReceive(row, receiveDate, null)
                feedback?.announce(snackbarText, undoLabel) { actions.onUndoReceive(row) }
            },
            onChangeAmount = { feedback?.open(receiveChange(row, title, model, actions, receiveDate)) },
        )
        Hairline()
        return
    }
    val valueColor =
        when {
            !paysThisMonth -> FinTrackTheme.colors.textSecondary
            received -> FinTrackTheme.colors.positive
            else -> MaterialTheme.colorScheme.onSurface
        }
    AmountRow(
        label = title,
        badge =
            listOfNotNull(
                receivedOn?.let { stringResource(R.string.plan_received_on, rememberReceivedText(it)) },
                autoRecordText(row),
                stringResource(R.string.plan_looks_like_salary).takeIf { row.key in model.duplicateKeys },
            ).joinToString("\n").ifEmpty { null },
        value = rowValueText(row, receipts),
        valueColor = valueColor,
        valueStyle = if (paysThisMonth) Amount else Amount.copy(fontWeight = FontWeight.Normal),
        modifier = rowTapModifier(tappable, row, actions),
        showChevron = tappable,
        trailing =
            if (showCheck) {
                {
                    ReceivedCheckButton(
                        received = received,
                        onClick = {
                            if (received) {
                                onUndo(row)
                            } else {
                                haptics.success()
                                actions.onReceive(row, receiveDate, null)
                                feedback?.announce(snackbarText, undoLabel) { actions.onUndoReceive(row) }
                            }
                        },
                    )
                }
            } else {
                null
            },
    )
    Hairline()
}

/** An income row still to be received: the shared pending row, tapping it opens its editor. */
@Suppress("LongParameterList") // the row, its model and the two actions are independent
@Composable
private fun PendingIncomeRow(
    row: IncomeRow,
    title: String,
    model: ExpectedIncomeModel,
    modifier: Modifier,
    onReceive: () -> Unit,
    onChangeAmount: () -> Unit,
) {
    val payDate = row.payDateIn(model.workingDays.month, model.incomeMonth)
    val status =
        listOfNotNull(
            autoRecordText(row) ?: stringResource(R.string.plan_payday, rememberPaydayText(payDate)),
            stringResource(R.string.plan_looks_like_salary).takeIf { row.key in model.duplicateKeys },
        ).joinToString(" · ")
    PendingOccurrenceRow(
        title = title,
        amountText = formatAmountCents(row.cents),
        statusText = status,
        labels = rememberPendingLabels(income = true),
        modifier = modifier,
        onConfirm = onReceive,
        onChangeAmount = onChangeAmount,
    )
}

private fun rowTapModifier(
    tappable: Boolean,
    row: IncomeRow,
    actions: ExpectedIncomeActions,
): Modifier =
    if (tappable) {
        // A manual line opens its editor; a salary-profile row goes straight to the profile.
        Modifier.clickable {
            if (row.component == null) actions.onEdit(row.lines.first().id) else actions.onOpenSalaryProfile()
        }
    } else {
        Modifier
    }

/** What the received snackbar's "Change" reopens for [row]: the sheet, with the amount editable for one line. */
private fun receiveChange(
    row: IncomeRow,
    title: String,
    model: ExpectedIncomeModel,
    actions: ExpectedIncomeActions,
    date: LocalDate,
): ReceiveChange {
    val month = model.workingDays.month
    val paying = row.payingLines(month, model.incomeMonth)
    return ReceiveChange(
        title = title,
        today = model.receipts.today,
        date = date,
        editableAmountCents = paying.singleOrNull()?.let { model.incomeMonth.amountIn(it, month) },
        onConfirm = { date, cents -> actions.onReceive(row, date, cents) },
    )
}
