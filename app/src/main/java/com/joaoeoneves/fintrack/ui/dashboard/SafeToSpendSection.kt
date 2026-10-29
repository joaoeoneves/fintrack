package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.EnvelopeOverflowRow
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.plan.MonthSummaryActions
import com.joaoeoneves.fintrack.ui.plan.MonthSummarySection
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import kotlin.math.abs

private const val MIN_SAFE_SP = 20
private const val MAX_SAFE_SP = 32

/**
 * The dashboard's "safe to spend" block: a single row inviting to set up the plan while no
 * expected income is planned, otherwise what is still free to spend and how the plan is shaping up.
 */
@Composable
fun SafeToSpendSection(
    state: DashboardUiState.Content,
    onOpenMonthlyPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.plan.isConfigured) {
        PlanContent(state = state, onOpenMonthlyPlan = onOpenMonthlyPlan, modifier = modifier)
    } else {
        PlanSetupRow(onOpenMonthlyPlan = onOpenMonthlyPlan, modifier = modifier)
    }
}

@Composable
private fun PlanSetupRow(
    onOpenMonthlyPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Hairline()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpenMonthlyPlan)
                    .padding(vertical = Dimens.Space16),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.plan_setup_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.plan_setup_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FinTrackTheme.colors.textSecondary,
            )
        }
        Hairline()
    }
}

@Suppress("LongMethod") // One column of independent rows.
@Composable
private fun PlanContent(
    state: DashboardUiState.Content,
    onOpenMonthlyPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safe = state.safeToSpendCents
    val title =
        if (state.budgetMonthIsCurrent) {
            stringResource(R.string.plan_safe_to_spend)
        } else {
            val month = formatMonthLabel(state.budgetMonth, currentLocale(), titlecase = false)
            stringResource(R.string.plan_safe_to_spend_month, month)
        }
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(
            title = title,
            trailing = { SectionAction(text = stringResource(R.string.plan_edit), onClick = onOpenMonthlyPlan) },
        )
        Text(
            text =
                if (safe >= 0) {
                    formatAmountCents(safe)
                } else {
                    stringResource(R.string.plan_over_by, formatAmountCents(abs(safe)))
                },
            style = AmountLarge,
            color = if (safe >= 0) MaterialTheme.colorScheme.onBackground else FinTrackTheme.colors.negative,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_SAFE_SP.sp, maxFontSize = MAX_SAFE_SP.sp),
            modifier = Modifier.padding(top = Dimens.Space4),
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
                color = FinTrackTheme.colors.textSecondary,
            )
        }
        state.envelopes?.let { EnvelopeOverflowRow(summary = it, modifier = Modifier.padding(top = Dimens.Space8)) }
        state.figures?.let {
            MonthSummarySection(
                figures = it,
                actions =
                    MonthSummaryActions(
                        onOpenIncome = onOpenMonthlyPlan,
                        onOpenRecurring = onOpenMonthlyPlan,
                        onOpenEnvelopes = onOpenMonthlyPlan,
                        onOpenSavings = onOpenMonthlyPlan,
                    ),
                extraIncomeCents = state.plan.extrasExpectedCents,
                modifier = Modifier.padding(top = Dimens.Space16),
            )
        }
        if (!state.budgetMonthIsCurrent) {
            Text(
                text = stringResource(R.string.plan_current_plan_caption),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Dimens.Space12),
            )
        }
    }
}
