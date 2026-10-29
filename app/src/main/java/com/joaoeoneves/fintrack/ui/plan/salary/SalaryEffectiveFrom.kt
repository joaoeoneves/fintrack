package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.runtime.Composable
import com.joaoeoneves.fintrack.ui.plan.EffectiveFromRow
import java.time.YearMonth

/** The salary's "Valid from" row. */
@Composable
internal fun SalaryEffectiveFrom(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
) {
    EffectiveFromRow(month = month, onChange = onChange)
}
