package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MonthlyPlanSummary
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import kotlin.math.abs

/**
 * The dashboard's "safe to spend" card: a call to action while no expected income is planned,
 * otherwise what is still free to spend this month and how the plan is shaping up.
 */
@Composable
fun MonthlyPlanCard(
    state: DashboardUiState.Content,
    onOpenMonthlyPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (state.plan.isConfigured) {
            PlanContent(state = state, onOpenMonthlyPlan = onOpenMonthlyPlan)
        } else {
            PlanSetupPrompt(onOpenMonthlyPlan = onOpenMonthlyPlan)
        }
    }
}

@Composable
private fun PlanSetupPrompt(onOpenMonthlyPlan: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenMonthlyPlan)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.plan_setup_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.plan_setup_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlanContent(
    state: DashboardUiState.Content,
    onOpenMonthlyPlan: () -> Unit,
) {
    val plan = state.plan
    val safe = plan.safeToSpendCents
    val title =
        if (state.budgetMonthIsCurrent) {
            stringResource(R.string.plan_safe_to_spend)
        } else {
            stringResource(R.string.plan_safe_to_spend_month, formatMonthLabel(state.budgetMonth, currentLocale()))
        }
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text =
                if (safe >= 0) {
                    formatAmountCents(safe)
                } else {
                    stringResource(R.string.plan_over_by, formatAmountCents(abs(safe)))
                },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (safe >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        state.dailyAllowance?.let { allowance ->
            Text(
                text =
                    pluralStringResource(
                        R.plurals.plan_daily_allowance,
                        allowance.daysLeft,
                        formatAmountCents(allowance.cents),
                        allowance.daysLeft,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        PlanRows(state.plan)
        if (!state.budgetMonthIsCurrent) {
            Text(
                text = stringResource(R.string.plan_current_plan_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenMonthlyPlan) {
            Text(stringResource(R.string.plan_edit))
        }
    }
}

@Composable
private fun PlanRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlanRows(plan: MonthlyPlanSummary) {
    PlanRow(
        label = stringResource(R.string.plan_expected_income),
        value =
            stringResource(
                R.string.plan_expected_vs_received,
                formatAmountCents(plan.expectedIncomeCents),
                formatAmountCents(plan.actualIncomeCents),
            ),
    )
    PlanRow(
        label = stringResource(R.string.plan_planned_allocations),
        value = formatAmountCents(plan.plannedAllocationsCents),
    )
    PlanRow(
        label = stringResource(R.string.plan_left_in_budgets),
        value = formatAmountCents(plan.remainingInBucketsCents),
    )
}
