package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.model.salaryComponent

/** The localized name of a salary component as shown in the income lists. */
@Composable
fun SalaryComponent.label(): String =
    stringResource(
        when (this) {
            SalaryComponent.SALARY -> R.string.plan_income_salary
            SalaryComponent.MEAL -> R.string.plan_income_meal
            SalaryComponent.SUBSIDIES -> R.string.plan_income_subsidies
            SalaryComponent.HOLIDAY -> R.string.salary_line_holiday
            SalaryComponent.CHRISTMAS -> R.string.salary_line_christmas
            SalaryComponent.EXTRA -> R.string.salary_group_extras
        },
    )

/** The row's name: an extra's own name (renames follow), the component name of a salary row, else the line's label. */
@Composable
fun IncomeRow.displayLabel(): String = component?.takeIf { it != SalaryComponent.EXTRA }?.label() ?: lines.first().label

/** The funding line's name: a salary component's localized name, else the line's own label. */
@Composable
fun PlannedIncome.fundingLabel(): String {
    val component = salaryComponent?.takeIf { it != SalaryComponent.EXTRA }
    return component?.label() ?: label
}
