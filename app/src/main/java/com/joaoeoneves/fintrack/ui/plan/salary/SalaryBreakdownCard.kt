package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SalaryBreakdown
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow

/** The live result of the form: monthly gross, SS, IRS and net, plus the meal, lump sums and each extra. */
@Composable
internal fun SalaryBreakdownCard(state: SalaryProfileUiState.Content) {
    val breakdown = state.breakdown ?: return
    SettingsGroup(label = stringResource(R.string.salary_breakdown_title)) {
        BreakdownRows(breakdown, state)
    }
}

@Composable
private fun BreakdownRows(
    breakdown: SalaryBreakdown,
    state: SalaryProfileUiState.Content,
) {
    val rows = mutableListOf<Pair<String, Long>>()
    if (breakdown.grossCents > 0) {
        rows += stringResource(R.string.salary_breakdown_gross) to breakdown.grossCents
        rows += stringResource(R.string.salary_breakdown_ss) to -breakdown.ssCents
        if (breakdown.otherDeductionsCents > 0) {
            rows += stringResource(R.string.salary_breakdown_other_deductions) to -breakdown.otherDeductionsCents
        }
        rows += stringResource(R.string.salary_breakdown_irs) to -breakdown.irsCents
    }
    rows += stringResource(R.string.salary_breakdown_net) to breakdown.netMonthlyCents
    if (breakdown.netDuoCents > 0) {
        rows += stringResource(R.string.salary_breakdown_net_base) to breakdown.netBaseCents
        rows += stringResource(R.string.salary_breakdown_net_duo) to breakdown.netDuoCents
    }
    if (breakdown.mealRatePerDayCents > 0) {
        val monthName = formatShortMonthName(state.month, currentLocale())
        rows += stringResource(R.string.salary_breakdown_meal, monthName) to
            breakdown.mealRatePerDayCents * state.workingDays
    }
    if (breakdown.holidayLumpNetCents > 0) {
        rows += stringResource(R.string.salary_field_net_holiday) to breakdown.holidayLumpNetCents
    }
    if (breakdown.christmasLumpNetCents > 0) {
        rows += stringResource(R.string.salary_field_net_christmas) to breakdown.christmasLumpNetCents
    }
    val texts =
        rows.map { (label, cents) -> label to formatAmountCents(cents) } +
            breakdown.extras.map { it.name to extraAmountText(it) }
    texts.forEachIndexed { index, (label, text) ->
        if (index > 0) Hairline()
        SettingsRow(title = label, value = text)
    }
}
