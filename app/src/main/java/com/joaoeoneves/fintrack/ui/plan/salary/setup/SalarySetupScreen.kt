package com.joaoeoneves.fintrack.ui.plan.salary.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SUBSIDY_SPREADS_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.plan.salary.MealSection
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryDuplicatesDialog
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryField
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryGroup
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryMoneyField
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryTextField
import com.joaoeoneves.fintrack.ui.plan.salary.SubsidyMonthsSection
import com.joaoeoneves.fintrack.ui.plan.salary.spreadLabel
import com.joaoeoneves.fintrack.ui.plan.salary.subsidyMonthPair
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

/**
 * A short wizard that sets up the salary profile: pay, subsidies, meal allowance and a summary. It fills the same
 * form as the full editor ([onOpenAdvanced]) and never overwrites an existing profile.
 */
@Composable
fun SalarySetupScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenAdvanced: () -> Unit,
    viewModel: SalarySetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.error_plan_save_failed)
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                SalarySetupEvent.Done -> onDone()
                SalarySetupEvent.OpenAdvanced -> onOpenAdvanced()
                SalarySetupEvent.Failed -> snackbarHostState.showSnackbar(failedMessage)
            }
        }
    }
    BackHandler(enabled = state.step != SalarySetupStep.PAY) { viewModel.onBack() }
    val labels = setupLineLabels()

    LargeTitleScaffold(
        title = stringResource(R.string.salary_setup_title),
        onBack = { if (!viewModel.onBack()) onBack() },
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        SalarySetupContent(
            state = state,
            actions =
                SalarySetupActions(
                    onChange = viewModel::onFormChange,
                    onNetLumpsChange = viewModel::onNetLumpsChange,
                    onPrimary = {
                        if (state.step == SalarySetupStep.SUMMARY) viewModel.onSave(labels) else viewModel.onNext()
                    },
                    onOpenAdvanced = onOpenAdvanced,
                ),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
    if (duplicates.isNotEmpty()) {
        SalaryDuplicatesDialog(
            lines = duplicates,
            onRemove = viewModel::onRemoveDuplicates,
            onKeep = viewModel::onKeepDuplicates,
        )
    }
}

/** What the wizard can trigger, bundled for detekt's LongParameterList. */
private data class SalarySetupActions(
    val onChange: (SalaryForm) -> Unit,
    val onNetLumpsChange: (Boolean) -> Unit,
    val onPrimary: () -> Unit,
    val onOpenAdvanced: () -> Unit,
)

@Composable
private fun SalarySetupContent(
    state: SalarySetupUiState,
    actions: SalarySetupActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.Space32),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        Text(
            text = stringResource(R.string.salary_setup_step, state.stepNumber, state.stepCount),
            style = MaterialTheme.typography.bodyMedium,
            color = FinTrackTheme.colors.textSecondary,
        )
        when (state.step) {
            SalarySetupStep.PAY -> PayStep(state, actions.onChange, actions.onOpenAdvanced)
            SalarySetupStep.SUBSIDIES -> SubsidiesStep(state, actions.onChange, actions.onNetLumpsChange)
            SalarySetupStep.MEAL -> MealSection(state.form, actions.onChange)
            SalarySetupStep.SUMMARY -> SummaryStep(state)
        }
        PrimaryPillButton(
            text =
                stringResource(
                    if (state.step == SalarySetupStep.SUMMARY) R.string.action_save else R.string.salary_setup_next,
                ),
            onClick = actions.onPrimary,
            enabled = state.canContinue,
            loading = state.saving,
            modifier = Modifier.padding(top = Dimens.Space24),
        )
    }
}

@Composable
private fun ColumnScope.PayStep(
    state: SalarySetupUiState,
    onChange: (SalaryForm) -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val form = state.form
    SalaryGroup(stringResource(R.string.salary_setup_pay_question)) {
        SegmentedTextTabs(
            options = SalaryInputMode.entries,
            selected = form.mode,
            label = {
                stringResource(
                    if (it == SalaryInputMode.GROSS) R.string.salary_setup_gross else R.string.salary_setup_net,
                )
            },
            onSelect = { onChange(form.copy(mode = it)) },
        )
        if (form.mode == SalaryInputMode.GROSS) {
            SalaryMoneyField(
                label = stringResource(R.string.salary_field_base),
                cents = form.base,
                onChange = { onChange(form.copy(base = it)) },
            )
            SalaryTextField(
                label = stringResource(R.string.salary_field_irs_base),
                value = form.irsBase,
                onChange = { onChange(form.copy(irsBase = it)) },
                invalid = SalaryField.IRS_BASE in state.invalid,
                hint = stringResource(R.string.salary_setup_irs_hint),
            )
        } else {
            SalaryMoneyField(
                label = stringResource(R.string.salary_field_net_monthly),
                cents = form.netMonthly,
                onChange = { onChange(form.copy(netMonthly = it)) },
                hint = stringResource(R.string.salary_net_monthly_hint),
            )
        }
    }
    GhostButton(
        text = stringResource(R.string.salary_setup_advanced),
        onClick = onOpenAdvanced,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun SubsidiesStep(
    state: SalarySetupUiState,
    onChange: (SalaryForm) -> Unit,
    onNetLumpsChange: (Boolean) -> Unit,
) {
    val form = state.form
    if (form.mode == SalaryInputMode.GROSS) {
        SalaryGroup(stringResource(R.string.salary_setup_subsidies_title)) {
            SegmentedTextTabs(
                options = SUBSIDY_SPREADS_PPM,
                selected = form.spreadPpm,
                label = { stringResource(spreadLabel(it)) },
                onSelect = { onChange(form.copy(spreadPpm = it)) },
            )
            Text(
                text = stringResource(R.string.salary_duo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
        SubsidyMonthsSection(form, onChange)
    } else {
        SalaryGroup(stringResource(R.string.salary_setup_twelfths_question)) {
            SegmentedTextTabs(
                options = listOf(false, true),
                selected = state.netLumps,
                label = {
                    stringResource(if (it) R.string.salary_setup_no_lumps else R.string.salary_setup_yes)
                },
                onSelect = onNetLumpsChange,
            )
            if (state.netLumps) {
                SalaryMoneyField(
                    label = stringResource(R.string.salary_field_net_holiday),
                    cents = form.netHoliday,
                    onChange = { onChange(form.copy(netHoliday = it)) },
                )
                SalaryMoneyField(
                    label = stringResource(R.string.salary_field_net_christmas),
                    cents = form.netChristmas,
                    onChange = { onChange(form.copy(netChristmas = it)) },
                )
            }
        }
        if (state.netLumps) SubsidyMonthsSection(form, onChange)
    }
}

@Composable
private fun SummaryStep(state: SalarySetupUiState) {
    val breakdown = state.breakdown
    SettingsGroup(label = stringResource(R.string.salary_setup_summary)) {
        if (breakdown != null) {
            val locale = currentLocale()
            val (holidayMonth, christmasMonth) = state.form.subsidyMonthPair()

            fun monthName(month: Int) = formatShortMonthName(YearMonth.of(state.month.year, month), locale)
            val rows = mutableListOf(stringResource(R.string.salary_setup_net_per_month) to breakdown.netMonthlyCents)
            if (breakdown.holidayLumpNetCents > 0) {
                val label = stringResource(R.string.salary_field_net_holiday)
                rows += "$label · ${monthName(holidayMonth)}" to breakdown.holidayLumpNetCents
            }
            if (breakdown.christmasLumpNetCents > 0) {
                val label = stringResource(R.string.salary_field_net_christmas)
                rows += "$label · ${monthName(christmasMonth)}" to breakdown.christmasLumpNetCents
            }
            if (breakdown.mealRatePerDayCents > 0) {
                val label = stringResource(R.string.salary_breakdown_meal, monthName(state.month.monthValue))
                rows += label to breakdown.mealRatePerDayCents * state.workingDays
            }
            rows.forEachIndexed { index, (label, cents) ->
                if (index > 0) Hairline()
                SettingsRow(title = label, value = formatAmountCents(cents))
            }
        }
    }
    Text(
        text = stringResource(R.string.salary_setup_later_hint),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.textSecondary,
    )
}

@Composable
private fun setupLineLabels() =
    SalaryLineLabels(
        netSalary = stringResource(R.string.salary_line_net),
        duodecimos = stringResource(R.string.salary_line_duodecimos),
        meal = stringResource(R.string.salary_group_meal),
        subsidy = stringResource(R.string.salary_line_subsidy),
    )
