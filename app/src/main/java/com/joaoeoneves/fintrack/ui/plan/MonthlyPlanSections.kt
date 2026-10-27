package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon

@Composable
private fun PlanCard(
    title: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun AmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

/** Planned totals: expected income, planned spending and saving, and what is left over. */
@Composable
fun PlanSummaryCard(
    summary: MonthlyPlanSummary,
    modifier: Modifier = Modifier,
) {
    val negative = MaterialTheme.colorScheme.error
    PlanCard(title = stringResource(R.string.plan_title), modifier = modifier) {
        AmountRow(stringResource(R.string.plan_expected_income), formatAmountCents(summary.expectedIncomeCents))
        AmountRow(stringResource(R.string.plan_planned_spending), formatAmountCents(summary.plannedSpendingCents))
        AmountRow(stringResource(R.string.plan_planned_saving), formatAmountCents(summary.plannedSavingCents))
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
    PlanCard(title = stringResource(R.string.plan_expected_income), modifier = modifier) {
        lines.forEach { line ->
            AmountRow(
                label = line.label,
                value = formatAmountCents(line.amountCents),
                modifier = Modifier.clickable { onEdit(line.id) },
            )
        }
        if (canAdd) {
            TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.plan_add_income_line), modifier = Modifier.padding(start = 8.dp))
            }
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
    PlanCard(title = stringResource(R.string.plan_allocations), modifier = modifier) {
        allocations.forEach { allocation ->
            AllocationRow(allocation = allocation, onClick = { onEdit(allocation) })
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
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(category.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = category.icon, contentDescription = null, tint = category.color)
        }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = allocation.limitCents?.let { formatAmountCents(it) } ?: stringResource(R.string.plan_not_planned),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
