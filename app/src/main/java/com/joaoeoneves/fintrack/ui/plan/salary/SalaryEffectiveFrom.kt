package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import com.joaoeoneves.fintrack.ui.plan.EffectiveFromRow
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

/**
 * The salary's "Valid from" row. With the salary shift on, [shiftSource] is the month before [month]: a line
 * valid from plan month [month] is paid at the end of it, so the note says so.
 */
@Composable
internal fun SalaryEffectiveFrom(
    month: YearMonth,
    shiftSource: YearMonth?,
    onChange: (YearMonth) -> Unit,
) {
    EffectiveFromRow(month = month, onChange = onChange)
    if (shiftSource != null) {
        val locale = currentLocale()
        Text(
            text =
                stringResource(
                    R.string.salary_shift_effective_note,
                    formatMonthLabel(shiftSource, locale, titlecase = false),
                    formatMonthLabel(month, locale, titlecase = false),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
    }
}
