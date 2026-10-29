package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.SalaryBreakdown
import com.joaoeoneves.fintrack.domain.model.SalaryYearSummary
import com.joaoeoneves.fintrack.domain.model.YearPayment
import com.joaoeoneves.fintrack.domain.model.YearPaymentKind
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import java.time.YearMonth

/**
 * What the profile expects, never for one month: a typical month (salary + twelfths), the payments spread over the
 * year (subsidy lumps, extras, meal allowance) and the year's totals.
 */
@Composable
internal fun SalaryBreakdownCard(state: SalaryProfileUiState.Content) {
    val breakdown = state.breakdown ?: return
    SettingsGroup(label = stringResource(R.string.salary_breakdown_title)) {
        MonthlyRows(breakdown, state)
    }
    val summary = state.yearSummary ?: return
    if (summary.payments.isNotEmpty() || summary.mealPerDayCents > 0) {
        SettingsGroup(label = stringResource(R.string.salary_year_payments_title)) {
            PaymentRows(summary, state.month.year, state.vacationDaysAssumed)
        }
    }
    SettingsGroup(label = stringResource(R.string.salary_year_title)) {
        YearRows(summary)
    }
}

@Composable
private fun MonthlyRows(
    breakdown: SalaryBreakdown,
    state: SalaryProfileUiState.Content,
) {
    val rows = mutableListOf<Pair<String, Long>>()
    if (breakdown.grossCents > 0) {
        rows += stringResource(R.string.salary_breakdown_gross) to breakdown.grossCents
        rows += stringResource(
            if (state.form.effectiveSector == EmploymentSector.PUBLIC) {
                R.string.salary_breakdown_ss_public
            } else {
                R.string.salary_breakdown_ss
            },
        ) to -breakdown.ssCents
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
    rows.forEachIndexed { index, (label, cents) ->
        if (index > 0) Hairline()
        SettingsRow(title = label, value = formatAmountCents(cents))
    }
}

@Composable
private fun PaymentRows(
    summary: SalaryYearSummary,
    year: Int,
    vacationDays: Int,
) {
    val locale = currentLocale()
    summary.payments.forEachIndexed { index, payment ->
        if (index > 0) Hairline()
        val months = payment.months.joinToString { formatShortMonthName(YearMonth.of(year, it), locale) }
        val each = formatAmountCents(payment.eachCents)
        SettingsRow(
            title = paymentTitle(payment),
            supportingText = months,
            value =
                if (payment.months.size > 1) {
                    stringResource(R.string.salary_year_each, each)
                } else {
                    each
                },
        )
    }
    if (summary.mealPerDayCents > 0) {
        if (summary.payments.isNotEmpty()) Hairline()
        SettingsRow(
            title = stringResource(R.string.salary_year_meal),
            supportingText =
                stringResource(R.string.salary_year_meal_year, formatAmountCents(summary.mealYearCents)) +
                    " · " + stringResource(R.string.salary_year_meal_vacation, vacationDays),
            value = stringResource(R.string.salary_year_meal_day, formatAmountCents(summary.mealPerDayCents)),
        )
    }
}

@Composable
private fun paymentTitle(payment: YearPayment): String =
    when (payment.kind) {
        YearPaymentKind.HOLIDAY -> stringResource(R.string.salary_field_net_holiday)
        YearPaymentKind.CHRISTMAS -> stringResource(R.string.salary_field_net_christmas)
        YearPaymentKind.EXTRA -> payment.name
    }

@Composable
private fun YearRows(summary: SalaryYearSummary) {
    val rows = mutableListOf(stringResource(R.string.salary_year_net) to summary.netYearCents)
    summary.grossYearCents?.let { rows += stringResource(R.string.salary_year_gross) to it }
    summary.ssYearCents?.let { rows += stringResource(R.string.salary_year_ss) to it }
    summary.otherDeductionsYearCents?.takeIf { it > 0 }?.let {
        rows += stringResource(R.string.salary_year_other_deductions) to it
    }
    summary.irsYearCents?.let { rows += stringResource(R.string.salary_year_irs) to it }
    rows.forEachIndexed { index, (label, cents) ->
        if (index > 0) Hairline()
        SettingsRow(title = label, value = formatAmountCents(cents))
    }
}
