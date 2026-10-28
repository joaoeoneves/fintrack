package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.domain.model.PlannedSource
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.model.amountIn
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
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
    }
}

/** Planned totals: expected income, planned spending and saving, and what is left over. */
@Composable
fun PlanSummaryCard(
    summary: MonthlyPlanSummary,
    modifier: Modifier = Modifier,
    fixedThisMonthCents: Long = 0L,
) {
    val negative = FinTrackTheme.colors.negative
    Column(modifier = modifier.fillMaxWidth().padding(top = Dimens.Space24)) {
        AmountRow(stringResource(R.string.plan_expected_income), formatAmountCents(summary.expectedIncomeCents))
        if (summary.extrasExpectedCents > 0L) {
            Hairline()
            AmountRow(stringResource(R.string.plan_extras_this_month), formatAmountCents(summary.extrasExpectedCents))
        }
        Hairline()
        AmountRow(stringResource(R.string.plan_planned_spending), formatAmountCents(summary.plannedSpendingCents))
        Hairline()
        AmountRow(stringResource(R.string.plan_planned_saving), formatAmountCents(summary.plannedSavingCents))
        Hairline()
        AmountRow(
            label = stringResource(R.string.plan_leftover),
            value = formatAmountCents(summary.plannedLeftoverCents),
            valueColor = if (summary.plannedLeftoverCents < 0) negative else MaterialTheme.colorScheme.onSurface,
        )
        if (fixedThisMonthCents > 0L) {
            Hairline()
            AmountRow(stringResource(R.string.plan_fixed_this_month), formatAmountCents(fixedThisMonthCents))
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
)

/**
 * The salary-profile entry, the working-days row and the expected-income rows with what each pays in the
 * selected month. A manual line, the salary and the meal allowance open their editor on tap; the other salary
 * components (which sum several lines) open the salary profile. The add button is hidden at the line cap. Under the
 * header sits the month's payday; a paying row of a past or current month has a check that records it as received
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
        Text(
            text = stringResource(R.string.plan_payday, rememberPaydayText(receipts.payday)),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
        SettingsRow(
            title = stringResource(R.string.plan_salary_profile_row),
            onClick = actions.onOpenSalaryProfile,
            showChevron = true,
        )
        Hairline()
        WorkingDaysRow(state = workingDays, onClick = actions.onEditWorkingDays)
        Hairline()
        val (managed, manual) = rows.partition { it.component != null }
        if (managed.isNotEmpty()) IncomeGroupHeader(stringResource(R.string.plan_from_salary_profile))
        managed.forEach { IncomeRowItem(it, model, actions, onUndo = { undoing = it }) }
        if (managed.isNotEmpty() && manual.isNotEmpty()) IncomeGroupHeader(stringResource(R.string.plan_other_income))
        manual.forEach { IncomeRowItem(it, model, actions, onUndo = { undoing = it }) }
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

@Composable
private fun IncomeGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Dimens.Space12),
    )
}

/** Rows opening the line editor: a manual line (no component), the salary and the meal allowance. */
private val EDITABLE_COMPONENTS = listOf(null, SalaryComponent.SALARY, SalaryComponent.MEAL)

@Composable
private fun IncomeRowItem(
    row: IncomeRow,
    model: ExpectedIncomeModel,
    actions: ExpectedIncomeActions,
    onUndo: (IncomeRow) -> Unit,
) {
    val receipts = model.receipts
    val line = row.lines.first()
    val opensEditor = row.component in EDITABLE_COMPONENTS
    val paysThisMonth = row.cents > 0L
    val receivedOn = receipts.receivedOn[row.key]
    val received = receivedOn != null
    val feedback = LocalReceiveFeedback.current
    val title = row.displayLabel()
    val snackbarText = stringResource(R.string.plan_received_on, rememberReceivedText(receipts.today))
    val changeLabel = stringResource(R.string.action_change)
    val showCheck = paysThisMonth && (received || receipts.canReceive)
    val valueColor =
        when {
            !paysThisMonth -> FinTrackTheme.colors.textSecondary
            received -> FinTrackTheme.colors.positive
            else -> MaterialTheme.colorScheme.onSurface
        }
    AmountRow(
        label = title,
        badge = receivedOn?.let { stringResource(R.string.plan_received_on, rememberReceivedText(it)) },
        value = if (paysThisMonth) formatAmountCents(row.cents) else stringResource(R.string.plan_not_this_month),
        valueColor = valueColor,
        valueStyle = if (paysThisMonth) Amount else Amount.copy(fontWeight = FontWeight.Normal),
        modifier = Modifier.clickable { if (opensEditor) actions.onEdit(line.id) else actions.onOpenSalaryProfile() },
        trailing =
            if (showCheck) {
                {
                    ReceivedCheckButton(
                        received = received,
                        onClick = {
                            if (received) {
                                onUndo(row)
                            } else {
                                actions.onReceive(row, receipts.today, null)
                                feedback?.announce(
                                    message = snackbarText,
                                    actionLabel = changeLabel,
                                    change = receiveChange(row, title, model, actions),
                                )
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

/** What the received snackbar's "Change" reopens for [row]: the sheet, with the amount editable for one line. */
private fun receiveChange(
    row: IncomeRow,
    title: String,
    model: ExpectedIncomeModel,
    actions: ExpectedIncomeActions,
): ReceiveChange {
    val (month, days) = model.workingDays.let { it.month to it.days }
    val paying = row.lines.filter { it.amountIn(month, days) > 0L }
    return ReceiveChange(
        title = title,
        today = model.receipts.today,
        editableAmountCents = paying.singleOrNull()?.amountIn(month, days),
        onConfirm = { date, cents -> actions.onReceive(row, date, cents) },
    )
}

/** One row per category with its planned amount; tap one to set, change or remove it. */
@Composable
fun AllocationsCard(
    allocations: List<CategoryBudget>,
    onEdit: (CategoryBudget) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.plan_allocations), modifier = modifier) {
        allocations.forEach { allocation ->
            AllocationRow(allocation = allocation, onClick = { onEdit(allocation) })
            Hairline()
        }
    }
}

@Composable
private fun AllocationRow(
    allocation: CategoryBudget,
    onClick: () -> Unit,
) {
    val category = allocation.category
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.SettingsRowMinHeight)
                .clickable(onClick = onClick)
                .padding(vertical = Dimens.Space8),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = category.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    stringResource(
                        if (category.kind == CategoryKind.SAVING) {
                            R.string.category_kind_saving
                        } else {
                            R.string.category_kind_spending
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
            if (allocation.plannedSource == PlannedSource.RECURRING) {
                Text(
                    text = stringResource(R.string.plan_from_recurring),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
            if (category.archived && allocation.planned != null) {
                Text(
                    text = stringResource(R.string.plan_archived_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        }
        val planned = allocation.planned
        if (planned != null) {
            Text(text = formatAmountCents(planned), style = Amount)
        } else {
            Text(
                text = stringResource(R.string.plan_not_planned),
                style = Amount.copy(fontWeight = FontWeight.Normal),
                color = FinTrackTheme.colors.textSecondary,
            )
        }
    }
}
