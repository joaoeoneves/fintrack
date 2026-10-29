package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.carnivalWeekdayIn
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

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

/**
 * The Carnival Tuesday as an optional day off: a switch (shown only in a month it falls on a weekday) that adds it
 * to, or takes it from, [daysOff].
 */
@Composable
internal fun CarnivalShortcut(
    month: YearMonth,
    daysOff: Int,
    onChange: (Int) -> Unit,
) {
    val day = remember(month) { carnivalWeekdayIn(month) } ?: return
    var counted by remember { mutableStateOf(false) }
    SettingsSwitchRow(
        title = stringResource(R.string.plan_days_off_carnival, day.dayOfMonth),
        supportingText = stringResource(R.string.plan_days_off_carnival_hint),
        checked = counted,
        onCheckedChange = {
            counted = it
            onChange(withCarnival(daysOff, it))
        },
    )
}

/** The days off after the Carnival shortcut is switched [on] (one more) or off (one fewer), within 0..max. */
internal fun withCarnival(
    daysOff: Int,
    on: Boolean,
): Int = (if (on) daysOff + 1 else daysOff - 1).coerceIn(0, MAX_DAYS_OFF)
