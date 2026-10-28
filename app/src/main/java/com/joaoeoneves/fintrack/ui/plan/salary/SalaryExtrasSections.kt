package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExtraBreakdown
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The meal allowance per working day (paid every month; days off are set in the Plan's working days). */
@Composable
internal fun MealSection(
    form: SalaryForm,
    onChange: (SalaryForm) -> Unit,
) {
    SalaryGroup(stringResource(R.string.salary_group_meal)) {
        SalaryMoneyField(
            label = stringResource(R.string.salary_field_meal),
            cents = form.meal,
            onChange = { onChange(form.copy(meal = it)) },
            hint = stringResource(R.string.salary_meal_hint),
        )
    }
}

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

/** What an extra pays: its yearly total, or the amount of each payment and the total when it has several. */
@Composable
internal fun extraAmountText(breakdown: ExtraBreakdown): String =
    if (breakdown.payments.size > 1) {
        stringResource(
            R.string.salary_extra_per_payment_and_year,
            formatAmountCents(breakdown.payments.first().second),
            formatAmountCents(breakdown.totalCents),
        )
    } else {
        formatAmountCents(breakdown.totalCents)
    }
