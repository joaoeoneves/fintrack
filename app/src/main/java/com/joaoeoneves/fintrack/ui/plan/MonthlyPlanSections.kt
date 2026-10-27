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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

@Composable
private fun AmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = Amount, color = valueColor)
    }
}

/** Planned totals: expected income, planned spending and saving, and what is left over. */
@Composable
fun PlanSummaryCard(
    summary: MonthlyPlanSummary,
    modifier: Modifier = Modifier,
) {
    val negative = FinTrackTheme.colors.negative
    SettingsGroup(label = stringResource(R.string.plan_title), modifier = modifier) {
        AmountRow(stringResource(R.string.plan_expected_income), formatAmountCents(summary.expectedIncomeCents))
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
    }
}

/** The expected-income lines; tap one to edit it. The add button is hidden at the line cap. */
@Composable
fun ExpectedIncomeCard(
    lines: List<PlannedIncome>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.plan_expected_income), modifier = modifier) {
        lines.forEach { line ->
            AmountRow(
                label = line.label,
                value = formatAmountCents(line.amountCents),
                modifier = Modifier.clickable { onEdit(line.id) },
            )
            Hairline()
        }
        if (canAdd) {
            GhostButton(
                text = stringResource(R.string.plan_add_income_line),
                onClick = onAdd,
                modifier = Modifier.padding(top = Dimens.Space12),
            )
        }
    }
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
        }
        Text(
            text = allocation.limitCents?.let { formatAmountCents(it) } ?: stringResource(R.string.plan_not_planned),
            style = Amount,
        )
    }
}
