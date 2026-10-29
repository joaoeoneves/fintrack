package com.joaoeoneves.fintrack.ui.plan.salary.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
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
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryDuplicatesDialog
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryEffectiveFrom
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryField
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryGroup
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryMoneyField
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryTextField
import com.joaoeoneves.fintrack.ui.plan.salary.SubsidyMonthsSection
import com.joaoeoneves.fintrack.ui.plan.salary.sectorLabel
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
    BackHandler(enabled = state.step != SalarySetupStep.SECTOR) { viewModel.onBack() }
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
                    onSectorSelected = viewModel::onSectorSelected,
                    onMealAnswerChange = viewModel::onMealAnswerChange,
                    onPayDayChange = viewModel::onPayDayChange,
                    onIrsJovemChange = viewModel::onIrsJovemChange,
                    onAdseChange = viewModel::onAdseChange,
                    onEffectiveFromChange = viewModel::onEffectiveFromChange,
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
            hasMeal = viewModel.hasMeal,
            onRemove = viewModel::onRemoveDuplicates,
            onKeep = viewModel::onKeepDuplicates,
        )
    }
}

/** What the wizard can trigger, bundled for detekt's LongParameterList. */
private data class SalarySetupActions(
    val onChange: (SalaryForm) -> Unit,
    val onNetLumpsChange: (Boolean) -> Unit,
    val onSectorSelected: (EmploymentSector) -> Unit,
    val onMealAnswerChange: (MealAnswer) -> Unit,
    val onPayDayChange: (PayDayDraft) -> Unit,
    val onIrsJovemChange: (Boolean) -> Unit,
    val onAdseChange: (Boolean) -> Unit,
    val onEffectiveFromChange: (YearMonth) -> Unit,
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
            SalarySetupStep.SECTOR -> SectorStep(actions.onSectorSelected)
            SalarySetupStep.PAY -> PayStep(state, actions)
            SalarySetupStep.SUBSIDIES -> SubsidiesStep(state, actions.onChange, actions.onNetLumpsChange)
            SalarySetupStep.MEAL -> MealStep(state, actions.onChange, actions.onMealAnswerChange)
            SalarySetupStep.SUMMARY -> SummaryStep(state, actions)
        }
        // The first step advances with the tap on an answer, so it has no button.
        if (state.step != SalarySetupStep.SECTOR) {
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
}

@Composable
private fun PayDeductionSwitches(
    state: SalarySetupUiState,
    actions: SalarySetupActions,
) {
    val form = state.form
    if (state.sector == EmploymentSector.PUBLIC) {
        SettingsSwitchRow(
            title = stringResource(R.string.salary_setup_adse),
            supportingText = stringResource(R.string.salary_setup_adse_hint),
            checked = form.hasAdse,
            onCheckedChange = actions.onAdseChange,
        )
    }
    SettingsSwitchRow(
        title = stringResource(R.string.salary_setup_irs_jovem),
        checked = state.irsJovem,
        onCheckedChange = actions.onIrsJovemChange,
    )
    if (state.irsJovem) {
        SalaryTextField(
            label = stringResource(R.string.salary_field_irs_jovem),
            value = form.exempt,
            onChange = { actions.onChange(form.copy(exempt = it)) },
            invalid = SalaryField.EXEMPT in state.invalid,
        )
    }
}

@Composable
private fun SectorStep(
    onSelect: (EmploymentSector) -> Unit,
) {
    SettingsGroup(label = stringResource(R.string.salary_setup_sector_question)) {
        EmploymentSector.entries.forEachIndexed { index, option ->
            if (index > 0) Hairline()
            SettingsRow(
                title = stringResource(sectorLabel(option)),
                onClick = { onSelect(option) },
                showChevron = true,
            )
        }
    }
}

@Composable
private fun ColumnScope.PayStep(
    state: SalarySetupUiState,
    actions: SalarySetupActions,
) {
    val form = state.form
    val onChange = actions.onChange
    val public = state.sector == EmploymentSector.PUBLIC
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
                hint =
                    stringResource(
                        if (public) R.string.salary_setup_irs_hint_public else R.string.salary_setup_irs_hint,
                    ),
            )
            PayDeductionSwitches(state, actions)
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
        onClick = actions.onOpenAdvanced,
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
private fun MealStep(
    state: SalarySetupUiState,
    onChange: (SalaryForm) -> Unit,
    onAnswerChange: (MealAnswer) -> Unit,
) {
    val form = state.form
    SalaryGroup(stringResource(R.string.salary_setup_meal_question)) {
        SegmentedTextTabs(
            options = MealAnswer.entries,
            selected = state.mealAnswer,
            label = {
                stringResource(
                    when (it) {
                        MealAnswer.NO -> R.string.salary_setup_meal_no
                        MealAnswer.CARD -> R.string.salary_setup_meal_card
                        MealAnswer.WITH_SALARY -> R.string.salary_setup_meal_salary
                    },
                )
            },
            onSelect = onAnswerChange,
        )
        if (state.mealAnswer != MealAnswer.NO) {
            SalaryMoneyField(
                label = stringResource(R.string.salary_field_meal),
                cents = form.meal,
                onChange = { onChange(form.copy(meal = it)) },
                hint = stringResource(R.string.salary_meal_hint),
            )
        }
    }
}

@Composable
private fun PayDayRow(
    draft: PayDayDraft,
    onChange: (PayDayDraft) -> Unit,
) {
    SalaryGroup(stringResource(R.string.plan_payday_label)) {
        SegmentedTextTabs(
            options = listOf(false, true),
            selected = draft.fixed,
            label = { stringResource(if (it) R.string.plan_payday_fixed else R.string.plan_payday_last_working_day) },
            onSelect = { onChange(draft.copy(fixed = it, earlierIfClosed = it && draft.earlierIfClosed)) },
        )
        if (draft.fixed) {
            MinimalTextField(
                value = draft.dayText,
                onValueChange = { onChange(draft.copy(dayText = it)) },
                label = stringResource(R.string.plan_payday_label),
                isError = draft.dayText.isNotEmpty() && draft.day == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsSwitchRow(
                title = stringResource(R.string.plan_payday_earlier_if_closed),
                checked = draft.earlierIfClosed,
                onCheckedChange = { onChange(draft.copy(earlierIfClosed = it)) },
            )
        }
    }
}

@Composable
private fun SummaryStep(
    state: SalarySetupUiState,
    actions: SalarySetupActions,
) {
    val onEffectiveFromChange = actions.onEffectiveFromChange
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
    PayDayRow(state.payDay, actions.onPayDayChange)
    SalaryEffectiveFrom(state.effectiveFrom, onEffectiveFromChange)
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
        holidaySubsidy = stringResource(R.string.salary_line_holiday),
        christmasSubsidy = stringResource(R.string.salary_line_christmas),
    )
