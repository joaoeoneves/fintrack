package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatDayMonth
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate

/**
 * The "days off this month" stepper plus, when Carnival falls on a weekday of the month, a chip for it.
 * Tapping the chip takes that day off (+1); tapping it again gives it back. Nothing is subtracted without a tap.
 */
@Composable
internal fun DaysOffFields(
    daysOff: Int,
    onChange: (Int) -> Unit,
    carnival: LocalDate?,
) {
    var taken by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.plan_days_off_title),
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
    )
    Stepper(
        value = daysOff,
        onChange = onChange,
        max = MAX_DAYS_OFF,
        labels = StepperLabels(R.string.cd_fewer_days_off, R.string.cd_more_days_off),
    )
    if (carnival != null) {
        CategoryChip(
            label =
                stringResource(
                    R.string.plan_carnival_chip,
                    formatDayMonth(carnival, currentLocale()),
                ),
            selected = taken,
            onClick = {
                onChange((daysOff + if (taken) -1 else 1).coerceIn(0, MAX_DAYS_OFF))
                taken = !taken
            },
        )
    }
}
