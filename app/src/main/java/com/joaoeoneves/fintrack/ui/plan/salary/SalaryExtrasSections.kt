package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.DEFAULT_VACATION_DAYS
import com.joaoeoneves.fintrack.domain.model.ExtraBreakdown
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.VacationBalance
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The meal allowance per working day (paid every month; days off are set in the Plan's working days). */
@Composable
internal fun MealSection(
    form: SalaryForm,
    invalid: Set<SalaryField>,
    onChange: (SalaryForm) -> Unit,
    balance: VacationBalance? = null,
) {
    SalaryGroup(stringResource(R.string.salary_group_meal)) {
        SalaryMoneyField(
            label = stringResource(R.string.salary_field_meal),
            cents = form.meal,
            onChange = { onChange(form.copy(meal = it)) },
            hint = stringResource(R.string.salary_meal_hint),
        )
        if (form.mealTaxableWarning()) MealTaxWarning()
        if (form.meal > 0L) {
            VacationDaysField(form, invalid, onChange, balance)
            Text(
                text = stringResource(R.string.salary_meal_payment),
                style = MaterialTheme.typography.labelLarge,
                color = FinTrackTheme.colors.textSecondary,
            )
            SegmentedTextTabs(
                options = MealPayment.entries,
                selected = form.mealPayment,
                label = { stringResource(mealPaymentLabel(it)) },
                onSelect = { onChange(form.copy(mealPayment = it)) },
            )
        }
    }
}

/** About the daily meal allowance paid with the salary that stays free of IRS and SS (a hint, no tax math). */
internal const val MEAL_CASH_LIMIT_CENTS = 600L

/** Whether the meal is paid with the salary above [MEAL_CASH_LIMIT_CENTS] a day. */
internal fun SalaryForm.mealTaxableWarning(): Boolean {
    val withSalary = mealPayment == MealPayment.WITH_SALARY
    return withSalary && meal > MEAL_CASH_LIMIT_CENTS
}

/** The warning shown under the meal rate; it only informs. */
@Composable
internal fun MealTaxWarning() {
    Text(
        text = stringResource(R.string.salary_meal_tax_warning),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.negative,
    )
}

/** The optional "vacation days per year" field (placeholder: the default of 22). */
@Composable
internal fun VacationDaysField(
    form: SalaryForm,
    invalid: Set<SalaryField>,
    onChange: (SalaryForm) -> Unit,
    balance: VacationBalance? = null,
) {
    val isInvalid = SalaryField.VACATION_DAYS in invalid
    MinimalTextField(
        value = form.vacationDays,
        onValueChange = { onChange(form.copy(vacationDays = it.filter(Char::isDigit).take(2))) },
        label = stringResource(R.string.salary_field_vacation_days),
        isError = isInvalid,
        placeholder = { Text(DEFAULT_VACATION_DAYS.toString()) },
        supportingText = {
            Text(stringResource(if (isInvalid) R.string.salary_error_invalid else R.string.salary_vacation_days_hint))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    if (balance != null && !isInvalid) VacationBalanceLine(balance)
}

/** "Vacation left: 8 of 22", plus how many days over the allowance when more were taken. */
@Composable
private fun VacationBalanceLine(balance: VacationBalance) {
    val text =
        stringResource(R.string.salary_vacation_left, balance.remaining, balance.total) +
            if (balance.overBy > 0) {
                " · " + pluralStringResource(R.plurals.salary_vacation_over, balance.overBy, balance.overBy)
            } else {
                ""
            }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (balance.overBy > 0) FinTrackTheme.colors.negative else FinTrackTheme.colors.textSecondary,
    )
}

internal fun mealPaymentLabel(payment: MealPayment): Int =
    if (payment == MealPayment.CARD) R.string.salary_meal_payment_card else R.string.salary_meal_payment_salary

/** The user's extras: one tappable row each (its name and what it pays per year) and a button to add one. */
@Composable
internal fun SalaryExtrasSection(
    state: SalaryProfileUiState.Content,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val gross = state.form.mode == SalaryInputMode.GROSS
    SettingsGroup(label = stringResource(R.string.salary_group_extras)) {
        state.form.extras.forEach { extra ->
            val paid =
                state.breakdown
                    ?.extras
                    ?.find { it.extraId == extra.id }
            SettingsRow(
                title = extra.name,
                supportingText =
                    when {
                        !gross && extra.amountType != SalaryExtraAmountType.FIXED_CENTS ->
                            stringResource(R.string.salary_extra_gross_only)
                        paid != null -> extraAmountText(paid)
                        else -> null
                    },
                onClick = { onEdit(extra.id) },
                showChevron = true,
            )
            Hairline()
        }
        if (state.canAddExtra) {
            GhostButton(
                text = stringResource(R.string.salary_extras_add),
                onClick = onAdd,
                modifier = Modifier.padding(top = Dimens.Space8),
            )
        }
    }
}

/** What an extra pays: the amount of one payment, then (with several) the count and the yearly total below. */
@Composable
internal fun extraAmountText(breakdown: ExtraBreakdown): String =
    if (breakdown.payments.size > 1) {
        stringResource(R.string.salary_extra_per_payment, formatAmountCents(breakdown.payments.first().second)) +
            "\n" +
            stringResource(
                R.string.salary_extra_payments_total,
                breakdown.payments.size,
                formatAmountCents(breakdown.totalCents),
            )
    } else {
        formatAmountCents(breakdown.totalCents)
    }
