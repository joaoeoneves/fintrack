package com.joaoeoneves.fintrack.ui.plan.configure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.plan.ConfigureActions
import com.joaoeoneves.fintrack.ui.plan.ConfigureSection
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * The global set-up screen, opened from the gear icon on the Plan tab: income, envelopes, recurring items and
 * categories. It is not tied to a month; further global plan settings belong in [ConfigureSection]'s `extraRows`.
 */
@Composable
fun ConfigureScreen(
    onBack: () -> Unit,
    actions: ConfigureActions,
    modifier: Modifier = Modifier,
    viewModel: ConfigureViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    LargeTitleScaffold(
        title = stringResource(R.string.plan_configure),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.Space32),
        ) {
            val shift by viewModel.salaryPaysNextMonth.collectAsStateWithLifecycle()
            ConfigureSection(status = status, actions = actions) {
                shift?.let { enabled ->
                    Hairline()
                    SettingsSwitchRow(
                        title = stringResource(R.string.plan_salary_next_month),
                        supportingText = stringResource(R.string.plan_salary_next_month_hint),
                        checked = enabled,
                        onCheckedChange = viewModel::onSalaryPaysNextMonthChanged,
                    )
                }
            }
        }
    }
}
