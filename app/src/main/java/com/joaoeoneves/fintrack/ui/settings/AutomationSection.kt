package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The Automation group on Settings: record the planned income as real income on payday. */
@Composable
fun AutomationSection(
    modifier: Modifier = Modifier,
    viewModel: AutomationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsGroup(label = stringResource(R.string.settings_section_automation), modifier = modifier) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_payday_title),
            supportingText = stringResource(R.string.settings_payday_subtitle),
            checked = state.paydayEnabled,
            onCheckedChange = viewModel::onPaydayToggled,
        )
        if (state.hasRecurringIncome) {
            Text(
                text = stringResource(R.string.settings_payday_recurring_hint),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = Dimens.Space8),
            )
        }
    }
}
