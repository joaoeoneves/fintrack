package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SPREAD_HALF_PPM
import com.joaoeoneves.fintrack.domain.model.SPREAD_NONE_PPM
import com.joaoeoneves.fintrack.domain.model.SUBSIDY_SPREADS_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.ui.designsystem.DecimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** A decimal text field of the salary form; shows [hint] under it unless the value is [invalid]. */
@Composable
internal fun SalaryTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    invalid: Boolean,
    hint: String? = null,
) {
    DecimalTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        isError = invalid,
        supportingText = if (invalid) stringResource(R.string.salary_error_invalid) else hint,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A money field of the salary form (cash-register input); shows [hint] under it. */
@Composable
internal fun SalaryMoneyField(
    label: String,
    cents: Long,
    onChange: (Long) -> Unit,
    hint: String? = null,
) {
    MoneyTextField(
        cents = cents,
        onCentsChange = onChange,
        label = label,
        supportingText = hint,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A settings-style group whose children are spaced like the fields of a form. */
@Composable
internal fun SalaryGroup(
    label: String,
    content: @Composable () -> Unit,
) {
    SettingsGroup(label = label) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) { content() }
    }
}

/** Gross mode: the base salary with the duodecimos choice, then the tax rates. */
@Composable
internal fun GrossSections(
    form: SalaryForm,
    invalid: Set<SalaryField>,
    onChange: (SalaryForm) -> Unit,
) {
    SalaryGroup(stringResource(R.string.salary_group_salary)) {
        SalaryMoneyField(
            label = stringResource(R.string.salary_field_base),
            cents = form.base,
            onChange = { onChange(form.copy(base = it)) },
        )
        Text(
            text = stringResource(R.string.salary_group_duo),
            style = MaterialTheme.typography.labelLarge,
            color = FinTrackTheme.colors.textSecondary,
        )
        SegmentedTextTabs(
            options = SUBSIDY_SPREADS_PPM,
            selected = form.spreadPpm,
            label = { stringResource(spreadLabel(it)) },
            onSelect = { onChange(form.copy(spreadPpm = it)) },
        )
        Text(
            text = stringResource(R.string.salary_duo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
    }
    SalaryGroup(stringResource(R.string.salary_group_taxes)) {
        SalaryTextField(
            label = stringResource(R.string.salary_field_irs_base),
            value = form.irsBase,
            onChange = { onChange(form.copy(irsBase = it)) },
            invalid = SalaryField.IRS_BASE in invalid,
        )
        SalaryTextField(
            label = stringResource(R.string.salary_field_irs_subsidies),
            value = form.irsSubsidies,
            onChange = { onChange(form.copy(irsSubsidies = it)) },
            invalid = SalaryField.IRS_SUBSIDIES in invalid,
            hint = stringResource(R.string.salary_irs_subsidies_hint),
        )
        SalaryTextField(
            label = stringResource(R.string.salary_field_irs_jovem),
            value = form.exempt,
            onChange = { onChange(form.copy(exempt = it)) },
            invalid = SalaryField.EXEMPT in invalid,
        )
        SalaryTextField(
            label = stringResource(ssFieldLabel(form.effectiveSector)),
            value = form.ss,
            onChange = { onChange(form.copy(ss = it)) },
            invalid = SalaryField.SS in invalid,
            hint = stringResource(ssHint(form.effectiveSector)),
        )
        SalaryTextField(
            label = stringResource(R.string.salary_field_other_deductions),
            value = form.otherDeductions,
            onChange = { onChange(form.copy(otherDeductions = it)) },
            invalid = SalaryField.OTHER_DEDUCTIONS in invalid,
        )
    }
}

internal fun spreadLabel(spreadPpm: Int): Int =
    when (spreadPpm) {
        SPREAD_NONE_PPM -> R.string.salary_spread_none
        SPREAD_HALF_PPM -> R.string.salary_spread_half
        SPREAD_FULL_PPM -> R.string.salary_spread_full
        else -> R.string.salary_spread_half
    }

/** Net mode: the monthly net salary as received and the net amount of each subsidy lump sum. */
@Composable
internal fun NetSection(
    form: SalaryForm,
    onChange: (SalaryForm) -> Unit,
) {
    SalaryGroup(stringResource(R.string.salary_group_net)) {
        SalaryMoneyField(
            label = stringResource(R.string.salary_field_net_monthly),
            cents = form.netMonthly,
            onChange = { onChange(form.copy(netMonthly = it)) },
            hint = stringResource(R.string.salary_net_monthly_hint),
        )
        SalaryMoneyField(
            label = stringResource(R.string.salary_field_net_holiday),
            cents = form.netHoliday,
            onChange = { onChange(form.copy(netHoliday = it)) },
        )
        SalaryMoneyField(
            label = stringResource(R.string.salary_field_net_christmas),
            cents = form.netChristmas,
            onChange = { onChange(form.copy(netChristmas = it)) },
        )
    }
}

/** The two months the subsidies are paid in (one grid, the third pick replaces the oldest); none with 100% spread. */
@Composable
internal fun SubsidyMonthsSection(
    form: SalaryForm,
    onChange: (SalaryForm) -> Unit,
) {
    if (form.mode == SalaryInputMode.GROSS && form.spreadPpm == SPREAD_FULL_PPM) return
    SalaryGroup(stringResource(R.string.salary_group_subsidy_months)) {
        SalaryMonthPicker(
            title = stringResource(R.string.salary_subsidy_months),
            selected = form.subsidyMonths,
            onChange = { onChange(form.copy(subsidyMonths = it)) },
            max = SUBSIDY_COUNT,
        )
    }
}

private const val SUBSIDY_COUNT = 2
