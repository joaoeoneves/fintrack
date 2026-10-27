package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow

/** The "Salary profile" entry of the Plan tab, opening the profile the managed income lines come from. */
@Composable
fun SalaryProfileSection(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.plan_salary_section), modifier = modifier) {
        SettingsRow(title = stringResource(R.string.plan_salary_profile_row), onClick = onOpen, showChevron = true)
    }
}
