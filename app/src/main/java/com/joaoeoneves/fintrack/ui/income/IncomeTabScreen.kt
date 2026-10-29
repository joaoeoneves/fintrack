package com.joaoeoneves.fintrack.ui.income

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MealCardBalance
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.YearIncomeOutlook
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.TabScaffold
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.history.IncomeHeadline
import com.joaoeoneves.fintrack.ui.common.history.IncomeHistory
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.AddTransactionPill
import com.joaoeoneves.fintrack.ui.designsystem.CardsSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.plan.ChangeSheet
import com.joaoeoneves.fintrack.ui.plan.CollectDeletedIncomeLines
import com.joaoeoneves.fintrack.ui.plan.CollectPlanErrors
import com.joaoeoneves.fintrack.ui.plan.ExpectedIncomeActions
import com.joaoeoneves.fintrack.ui.plan.ExpectedIncomeCard
import com.joaoeoneves.fintrack.ui.plan.ExpectedIncomeModel
import com.joaoeoneves.fintrack.ui.plan.IncomeLineSheet
import com.joaoeoneves.fintrack.ui.plan.IncomeSetupGroup
import com.joaoeoneves.fintrack.ui.plan.LocalReceiveFeedback
import com.joaoeoneves.fintrack.ui.plan.WorkingDaysDialog
import com.joaoeoneves.fintrack.ui.plan.WorkingDaysState
import com.joaoeoneves.fintrack.ui.plan.rememberReceiveFeedback
import com.joaoeoneves.fintrack.ui.theme.Dimens

// How many months past the current one the tab can show (kept in step with the ViewModel's limit).
private const val INCOME_MONTHS_AHEAD = 120

/**
 * The Income tab. Month scale: the month's expected vs received income per line (receive/undo, working days, salary
 * profile, manual lines). Year scale: the year's income by source. Both: the bars, "By source" and "Since January".
 */
@Suppress("LongParameterList") // tab callbacks: navigation targets plus the add pill's two
@Composable
fun IncomeTabScreen(
    onOpenSettings: () -> Unit,
    onOpenSalaryProfile: (hasProfile: Boolean) -> Unit,
    onOpenSource: (String, Period) -> Unit,
    onAddExpense: (Period) -> Unit,
    onAddIncome: (Period) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncomeTabViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CollectPlanErrors(snackbarHostState = snackbarHostState, events = listOf(viewModel.errorEvent))
    CollectDeletedIncomeLines(viewModel, snackbarHostState)
    val receiveFeedback = rememberReceiveFeedback(snackbarHostState)
    receiveFeedback.ChangeSheet()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalReceiveFeedback provides receiveFeedback) {
        TabScaffold(
            title = stringResource(R.string.nav_income),
            onOpenSettings = onOpenSettings,
            modifier = modifier,
            snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
            floatingActionButton = {
                (state as? IncomeTabUiState.Content)?.let { content ->
                    AddTransactionPill(
                        visible = true,
                        onAddExpense = { onAddExpense(content.period) },
                        onAddIncome = { onAddIncome(content.period) },
                    )
                }
            },
        ) { innerPadding ->
            val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
            StateCrossfade(state) { current ->
                when (current) {
                    IncomeTabUiState.Loading -> CardsSkeleton(contentModifier)

                    is IncomeTabUiState.Error ->
                        ErrorState(
                            message = current.message.asString(),
                            onRetry = viewModel::onRetry,
                            modifier = contentModifier,
                        )

                    is IncomeTabUiState.Content ->
                        IncomeContent(current, viewModel, onOpenSalaryProfile, onOpenSource, contentModifier)
                }
            }
        }
    }
}

@Composable
private fun IncomeContent(
    state: IncomeTabUiState.Content,
    viewModel: IncomeTabViewModel,
    onOpenSalaryProfile: (hasProfile: Boolean) -> Unit,
    onOpenSource: (String, Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingWorkingDays by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Dimens.AddPillClearance),
    ) {
        PeriodSelector(
            period = state.period,
            canGoToNextMonth = state.canGoToNextMonth,
            actions = viewModel.periodActions,
            monthOnly = true,
            monthsAhead = INCOME_MONTHS_AHEAD,
            showScaleSwitch = true,
            modifier = Modifier.padding(horizontal = Dimens.Space8),
        )
        IncomeHeadline(history = state.history)
        val month = state.month
        if (month != null) {
            MonthCard(state, month, viewModel, onOpenSalaryProfile) { editingWorkingDays = true }
        }
        state.mealCard?.let { MealCardRow(it) }
        state.outlook?.let { YearOutlook(it) }
        IncomeHistory(
            history = state.history,
            onSourceClick = { onOpenSource(it, state.period) },
        )
        if (month != null) {
            IncomeSetupGroup(
                workingDays = month.workingDays,
                actions = incomeActions(state, viewModel, onOpenSalaryProfile) { editingWorkingDays = true },
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Dimens.Space16),
            )
        } else {
            IncomeConfigure(state, viewModel, onOpenSalaryProfile)
        }
    }

    val workingDaysMonth = state.month?.workingDays
    if (editingWorkingDays && workingDaysMonth != null) {
        WorkingDaysDialog(
            state = workingDaysMonth,
            onSave = viewModel::onSetWorkingDays,
            onDismiss = { editingWorkingDays = false },
        )
    }
    IncomeLineEditor(state, workingDaysMonth, viewModel)
}

@Composable
private fun IncomeLineEditor(
    state: IncomeTabUiState.Content,
    workingDaysMonth: WorkingDaysState?,
    viewModel: IncomeTabViewModel,
) {
    state.editor?.let {
        IncomeLineSheet(
            editor = it,
            workingDays = workingDaysMonth ?: viewModel.fallbackWorkingDays(),
            onSave = { label, cents, schedule, from ->
                val month = (workingDaysMonth ?: viewModel.fallbackWorkingDays()).month
                viewModel.onSaveIncomeLine(label, cents, schedule, month, from)
            },
            onDelete = viewModel::onDeleteIncomeLine,
            onDismiss = viewModel::onDismissEditor,
        )
    }
}

private fun incomeActions(
    state: IncomeTabUiState.Content,
    viewModel: IncomeTabViewModel,
    onOpenSalaryProfile: (hasProfile: Boolean) -> Unit,
    onEditWorkingDays: () -> Unit,
) = ExpectedIncomeActions(
    onAdd = viewModel::onAddIncomeLine,
    onEdit = viewModel::onEditIncomeLine,
    onEditWorkingDays = onEditWorkingDays,
    onOpenSalaryProfile = { onOpenSalaryProfile(state.hasProfile) },
    onReceive = viewModel::onReceive,
    onUndoReceive = viewModel::onUndoReceive,
)

@Composable
private fun MonthCard(
    state: IncomeTabUiState.Content,
    month: IncomeMonthView,
    viewModel: IncomeTabViewModel,
    onOpenSalaryProfile: (hasProfile: Boolean) -> Unit,
    onEditWorkingDays: () -> Unit,
) {
    ExpectedIncomeCard(
        model =
            ExpectedIncomeModel(
                rows = month.incomeRows,
                canAdd = state.canAddIncomeLine,
                workingDays = month.workingDays,
                receipts = month.receipts,
                duplicateKeys = month.duplicateKeys,
                incomeMonth = month.incomeMonth,
            ),
        actions = incomeActions(state, viewModel, onOpenSalaryProfile, onEditWorkingDays),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
    )
}

/** A current or future year: each month's income, received or expected, and the year's total. */
@Composable
private fun YearOutlook(outlook: YearIncomeOutlook) {
    val locale = currentLocale()
    SettingsGroup(
        label = stringResource(R.string.income_outlook),
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Dimens.Space16),
    ) {
        outlook.months.forEach { entry ->
            SettingsRow(
                title = formatShortMonthName(entry.month, locale),
                supportingText = stringResource(outlookLabel(entry.received, entry.partial)),
                value = formatAmountCents(entry.cents),
            )
            Hairline()
        }
        SettingsRow(
            title = stringResource(R.string.income_outlook_total),
            supportingText =
                stringResource(
                    R.string.income_outlook_total_detail,
                    formatAmountCents(outlook.receivedCents),
                    formatAmountCents(outlook.expectedCents),
                ),
            value = formatAmountCents(outlook.totalCents),
        )
    }
}

private fun outlookLabel(
    done: Boolean,
    partial: Boolean,
) = when {
    partial -> R.string.income_outlook_partial
    done -> R.string.income_outlook_received
    else -> R.string.income_outlook_expected
}

/** The meal card: what it received and what was paid with it in the period, and what is left (may be negative). */
@Composable
private fun MealCardRow(balance: MealCardBalance) {
    SettingsGroup(
        label = stringResource(R.string.income_meal_card),
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Dimens.Space16),
    ) {
        SettingsRow(
            title = stringResource(R.string.income_meal_card_balance),
            supportingText =
                stringResource(
                    R.string.income_meal_card_detail,
                    formatAmountCents(balance.receivedCents),
                    formatAmountCents(balance.spentCents),
                ),
            value = formatAmountCents(balance.balanceCents),
        )
    }
}

/** The Year scale's in-place set-up: the salary profile and a new manual line (the Month card has both already). */
@Composable
private fun IncomeConfigure(
    state: IncomeTabUiState.Content,
    viewModel: IncomeTabViewModel,
    onOpenSalaryProfile: (hasProfile: Boolean) -> Unit,
) {
    SettingsGroup(
        label = stringResource(R.string.tab_configure),
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Dimens.Space16),
    ) {
        SettingsRow(
            title = stringResource(R.string.plan_salary_profile_row),
            onClick = { onOpenSalaryProfile(state.hasProfile) },
            showChevron = true,
        )
        if (state.canAddIncomeLine) {
            Hairline()
            GhostButton(
                text = stringResource(R.string.plan_add_income_line),
                onClick = viewModel::onAddIncomeLine,
                modifier = Modifier.padding(top = Dimens.Space12),
            )
        }
    }
}
