package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SalaryComponent

/** The localized name of a salary component as shown in the income lists. */
@Composable
fun SalaryComponent.label(): String =
    stringResource(
        when (this) {
            SalaryComponent.SALARY -> R.string.plan_income_salary
            SalaryComponent.MEAL -> R.string.plan_income_meal
            SalaryComponent.SUBSIDIES -> R.string.plan_income_subsidies
            SalaryComponent.FLEX -> R.string.plan_income_flex
            SalaryComponent.PROFIT_SHARE -> R.string.plan_income_profit_share
            SalaryComponent.BONUS -> R.string.plan_income_bonus
        },
    )
