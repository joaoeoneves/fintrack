package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.runtime.Composable
import com.joaoeoneves.fintrack.ui.plan.AdvancedEffectiveFrom
import java.time.YearMonth

/** The salary's "Valid from" row, under "Advanced". */
@Composable
internal fun SalaryEffectiveFrom(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
) {
    AdvancedEffectiveFrom(month = month, onChange = onChange)
}
