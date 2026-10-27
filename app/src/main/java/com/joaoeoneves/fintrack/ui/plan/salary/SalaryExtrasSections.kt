package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode

/** The meal allowance per working day (paid every month; days off are set in the Plan's working days). */
@Composable
internal fun MealSection(
    form: SalaryForm,
    invalid: Set<SalaryField>,
    onChange: (SalaryForm) -> Unit,
) {
    SalaryGroup(stringResource(R.string.salary_group_meal)) {
        SalaryTextField(
            label = stringResource(R.string.salary_field_meal),
            value = form.meal,
            onChange = { onChange(form.copy(meal = it)) },
            invalid = SalaryField.MEAL in invalid,
            hint = stringResource(R.string.salary_meal_hint),
        )
    }
}

/**
 * Flexible benefits, paid in four quarterly payments: a share of the annual gross (gross mode, with a withdrawal
 * fee) or a plain yearly amount (net mode).
 */
@Composable
internal fun FlexSection(
    form: SalaryForm,
    invalid: Set<SalaryField>,
    onChange: (SalaryForm) -> Unit,
) {
    SalaryGroup(stringResource(R.string.salary_group_flex)) {
        if (form.mode == SalaryInputMode.GROSS) {
            SalaryTextField(
                label = stringResource(R.string.salary_field_flex_percent),
                value = form.flexPercent,
                onChange = { onChange(form.copy(flexPercent = it)) },
                invalid = SalaryField.FLEX_PERCENT in invalid,
            )
            SalaryTextField(
                label = stringResource(R.string.salary_field_flex_fee),
                value = form.flexFee,
                onChange = { onChange(form.copy(flexFee = it)) },
                invalid = SalaryField.FLEX_FEE in invalid,
            )
        } else {
            SalaryTextField(
                label = stringResource(R.string.salary_field_flex_annual),
                value = form.flexAnnual,
                onChange = { onChange(form.copy(flexAnnual = it)) },
                invalid = SalaryField.FLEX_ANNUAL in invalid,
            )
        }
        SalaryMonthPicker(
            title = stringResource(R.string.salary_flex_first_month),
            selected = form.flexFirstMonth,
            onChange = { onChange(form.copy(flexFirstMonth = it)) },
        )
    }
}

/** An optional profit share (a multiple of the base salary, gross mode only) and an optional bonus. */
@Composable
internal fun ExtrasSection(
    form: SalaryForm,
    invalid: Set<SalaryField>,
    onChange: (SalaryForm) -> Unit,
) {
    SalaryGroup(stringResource(R.string.salary_group_extras)) {
        if (form.mode == SalaryInputMode.GROSS) {
            SalaryTextField(
                label = stringResource(R.string.salary_field_profit_share),
                value = form.profitMultiplier,
                onChange = { onChange(form.copy(profitMultiplier = it)) },
                invalid = SalaryField.PROFIT_SHARE in invalid,
            )
            if (form.profitMultiplier.isNotBlank()) {
                SalaryMonthPicker(
                    title = stringResource(R.string.salary_profit_month),
                    selected = form.profitMonths,
                    onChange = { onChange(form.copy(profitMonths = it)) },
                )
            }
        }
        SalaryTextField(
            label = stringResource(R.string.salary_field_bonus),
            value = form.bonus,
            onChange = { onChange(form.copy(bonus = it)) },
            invalid = SalaryField.BONUS in invalid,
        )
        if (form.bonus.isNotBlank()) {
            SalaryMonthPicker(
                title = stringResource(R.string.salary_bonus_month),
                selected = form.bonusMonth,
                onChange = { onChange(form.copy(bonusMonth = it)) },
            )
        }
    }
}
