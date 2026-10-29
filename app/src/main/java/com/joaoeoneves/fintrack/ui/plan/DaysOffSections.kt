package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The "days off this month" stepper. */
@Composable
internal fun DaysOffFields(
    daysOff: Int,
    onChange: (Int) -> Unit,
) {
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
}
