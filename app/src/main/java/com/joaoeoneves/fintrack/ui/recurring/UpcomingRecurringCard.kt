package com.joaoeoneves.fintrack.ui.recurring

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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.UpcomingOccurrence
import com.joaoeoneves.fintrack.domain.model.monthlyEquivalentCents
import com.joaoeoneves.fintrack.domain.model.payingLines
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.rememberDayMonthText
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.designsystem.PendingOccurrenceRow
import com.joaoeoneves.fintrack.ui.designsystem.PendingTone
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.rememberFinTrackHaptics
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import com.joaoeoneves.fintrack.ui.plan.LocalReceiveFeedback
import com.joaoeoneves.fintrack.ui.plan.ReceiveChange
import com.joaoeoneves.fintrack.ui.plan.displayLabel
import com.joaoeoneves.fintrack.ui.plan.key
import com.joaoeoneves.fintrack.ui.plan.rememberPaydayText
import com.joaoeoneves.fintrack.ui.plan.rememberPendingLabels
import com.joaoeoneves.fintrack.ui.plan.rememberReceivedText
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate

/** How many upcoming rows the dashboard card shows. */
const val DASHBOARD_UPCOMING_LIMIT = 5

/** The card's state plus its callbacks, bundled to keep the dashboard's parameter lists short. */
data class UpcomingRecurringModel(
    val state: UpcomingRecurringUiState,
    val onConfirm: (UpcomingOccurrence, amountCents: Long?) -> Unit,
    val onSkip: (UpcomingOccurrence) -> Unit,
    val onManage: () -> Unit,
    /** Records the income as received; returns what takes it back. */
    val onReceiveIncome: (row: IncomeRow, date: LocalDate, amountCents: Long?) -> () -> Unit,
)

/** Shows the view model's failures and its confirm/skip snackbars (with "Undo") in [snackbarHostState]. */
@Composable
fun UpcomingRecurringSnackbars(
    viewModel: UpcomingRecurringViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { undo ->
            val message =
                when (undo.kind) {
                    RecurringUndo.Kind.CONFIRMED -> resources.getString(R.string.recurring_added, undo.itemName)
                    RecurringUndo.Kind.SKIPPED -> resources.getString(R.string.recurring_skipped, undo.itemName)
                }
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showActionSnackbar(message, resources.getString(R.string.action_undo))
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndo(undo)
        }
    }
}

/**
 * The dashboard block listing what recurring bills/income are due; hidden while loading or on
 * error. Without any recurring items it collapses to a single call-to-action row.
 */
@Composable
fun UpcomingRecurringCard(
    model: UpcomingRecurringModel,
    modifier: Modifier = Modifier,
) {
    val state = model.state as? UpcomingRecurringUiState.Content ?: return
    Column(modifier = modifier.fillMaxWidth().testTag(TestTags.RECURRING_CARD)) {
        if (state.overview.hasItems) {
            UpcomingContent(state = state, model = model)
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = model.onManage)
                        .heightIn(min = Dimens.SettingsRowMinHeight),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recurring_empty_cta),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = FinTrackTheme.colors.textSecondary,
                )
            }
            Hairline()
        }
        state.expectedIncome?.takeIf { it.entries.isNotEmpty() }?.let {
            ExpectedIncomeGroup(expected = it, today = state.today, onReceive = model.onReceiveIncome)
        }
    }
}

/**
 * The current month's income not yet received, offered from a week before payday: each row with a check that
 * records it as received today and offers to change the date or amount from the snackbar.
 */
@Composable
private fun ExpectedIncomeGroup(
    expected: ExpectedIncome,
    today: LocalDate,
    onReceive: (row: IncomeRow, date: LocalDate, amountCents: Long?) -> () -> Unit,
) {
    val feedback = LocalReceiveFeedback.current
    val haptics = rememberFinTrackHaptics()
    val undoLabel = stringResource(R.string.action_undo)
    val labels = rememberPendingLabels(income = true)
    SectionTitle(title = stringResource(R.string.home_to_receive_title))
    expected.entries.forEachIndexed { index, entry ->
        if (index > 0) Hairline()
        val row = entry.row
        val title = row.displayLabel()
        val payDate = entry.payDate
        val receiveDate = minOf(payDate, today)
        val snackbarText = stringResource(R.string.plan_received_on, rememberReceivedText(receiveDate))
        key(row.key, payDate) {
            PendingOccurrenceRow(
                title = title,
                amountText = formatAmountCents(row.cents),
                statusText = stringResource(R.string.plan_payday, rememberPaydayText(payDate)),
                labels = labels,
                onConfirm =
                    if (row.cents > 0L) {
                        {
                            haptics.success()
                            val undo = onReceive(row, receiveDate, null)
                            feedback?.announce(snackbarText, undoLabel, undo)
                        }
                    } else {
                        null
                    },
                onChangeAmount = {
                    val paying = row.payingLines(entry.month, expected.incomeMonth)
                    feedback?.open(
                        ReceiveChange(
                            title = title,
                            today = today,
                            date = receiveDate,
                            editableAmountCents =
                                paying.singleOrNull()?.let { expected.incomeMonth.amountIn(it, entry.month) },
                            onConfirm = { date, cents -> onReceive(row, date, cents) },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun UpcomingContent(
    state: UpcomingRecurringUiState.Content,
    model: UpcomingRecurringModel,
) {
    val rows = state.overview.upcoming
    SectionTitle(
        title = stringResource(R.string.recurring_upcoming_title),
        trailing = { SectionAction(text = stringResource(R.string.recurring_manage), onClick = model.onManage) },
    )
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.recurring_nothing_due),
            style = MaterialTheme.typography.bodyMedium,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.padding(vertical = Dimens.Space8),
        )
    }
    rows.take(DASHBOARD_UPCOMING_LIMIT).forEachIndexed { index, row ->
        if (index > 0) Hairline()
        key(row.item.id, row.date) { UpcomingRow(row = row, today = state.today, model = model) }
    }
    if (state.overview.annualExpenseCents > 0) {
        Text(
            text =
                stringResource(
                    R.string.recurring_annual_cost,
                    formatAmountCents(state.overview.annualExpenseCents),
                    formatAmountCents(monthlyEquivalentCents(state.overview.annualExpenseCents)),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Dimens.Space12),
        )
    }
}

@Composable
private fun UpcomingRow(
    row: UpcomingOccurrence,
    today: LocalDate,
    model: UpcomingRecurringModel,
) {
    val haptics = rememberFinTrackHaptics()
    var changingAmount by remember { mutableStateOf(false) }
    // A future auto-posted bill posts itself: informational, no controls.
    val automatic = row.item.autoPost && !row.isDue
    val status =
        when {
            automatic ->
                stringResource(R.string.pending_automatic, rememberDayMonthText(row.date))
            row.date < today -> stringResource(R.string.recurring_overdue)
            row.date == today -> stringResource(R.string.recurring_due_today)
            else -> rememberLocaleAwareDateFormatter().format(row.date)
        }
    PendingOccurrenceRow(
        title = row.item.name,
        amountText = formatAmountCents(row.item.amountCents),
        statusText = status,
        statusTone =
            when {
                row.date < today -> PendingTone.NEGATIVE
                row.isDue -> PendingTone.ACCENT
                else -> PendingTone.NEUTRAL
            },
        extraText =
            if (row.overdueCount > 0) {
                pluralStringResource(R.plurals.recurring_more_overdue, row.overdueCount, row.overdueCount)
            } else {
                null
            },
        labels = rememberPendingLabels(),
        onConfirm =
            if (automatic) {
                null
            } else {
                {
                    haptics.confirm()
                    model.onConfirm(row, null)
                }
            },
        confirmEnabled = row.isDue,
        onSkip =
            {
                haptics.reject()
                model.onSkip(row)
            },
        onChangeAmount = { changingAmount = true }.takeIf { row.isDue },
    )
    if (changingAmount) {
        ChangeAmountDialog(
            title = stringResource(R.string.recurring_amount_title, row.item.name),
            initialCents = row.item.amountCents,
            onConfirm = {
                changingAmount = false
                haptics.confirm()
                model.onConfirm(row, it)
            },
            onDismiss = { changingAmount = false },
        )
    }
}

/** Asks for the amount actually billed before confirming a bill whose amount varies. */
@Composable
private fun ChangeAmountDialog(
    title: String,
    initialCents: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var cents by remember { mutableLongStateOf(initialCents) }
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            MoneyTextField(
                cents = cents,
                onCentsChange = { cents = it },
                label = stringResource(R.string.field_amount),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.recurring_confirm),
                onClick = { if (cents > 0L) onConfirm(cents) },
            )
        },
        dismissButton = { DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
