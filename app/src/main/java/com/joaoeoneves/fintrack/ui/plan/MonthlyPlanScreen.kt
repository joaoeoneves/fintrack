package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.carnivalWeekdayIn
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.SettingsIconButton
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.dashboard.BudgetSection
import com.joaoeoneves.fintrack.ui.dashboard.EditBudgetDialog
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.plan.annual.AnnualPlanActions
import com.joaoeoneves.fintrack.ui.plan.annual.AnnualPlanView
import com.joaoeoneves.fintrack.ui.plan.annual.AnnualPlanViewModel
import com.joaoeoneves.fintrack.ui.plan.annual.PlanTab
import com.joaoeoneves.fintrack.ui.plan.goals.CollectPlanErrors
import com.joaoeoneves.fintrack.ui.plan.goals.GoalsSection
import com.joaoeoneves.fintrack.ui.plan.goals.GoalsViewModel
import com.joaoeoneves.fintrack.ui.settings.RecurringSection
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The Plan tab: the monthly plan, the selected month's planned-vs-actual progress and the recurring list link. */
@Composable
fun PlanScreen(
    onOpenSettings: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenEnvelopes: () -> Unit,
    onOpenSalaryProfile: () -> Unit,
    viewModel: MonthlyPlanViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val goalsViewModel: GoalsViewModel = hiltViewModel()
    CollectPlanErrors(
        snackbarHostState = snackbarHostState,
        events = listOf(viewModel.errorEvent, goalsViewModel.errorEvent),
    )

    val receiveFeedback = rememberReceiveFeedback(snackbarHostState)
    receiveFeedback.ChangeSheet()

    CompositionLocalProvider(LocalReceiveFeedback provides receiveFeedback) {
        PlanScaffold(
            onOpenSettings = onOpenSettings,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            navigation = PlanNavigation(onOpenRecurring, onOpenEnvelopes, onOpenSalaryProfile),
        )
    }
}

@Composable
private fun PlanScaffold(
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: MonthlyPlanViewModel,
    navigation: PlanNavigation,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LargeTitleScaffold(
        title = stringResource(R.string.nav_plan),
        actions = { SettingsIconButton(onClick = onOpenSettings) },
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val annualViewModel: AnnualPlanViewModel = hiltViewModel()
        val tab by annualViewModel.selectedTab.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            SegmentedTextTabs(
                options = PlanTab.entries,
                selected = tab,
                label = { stringResource(it.labelRes) },
                onSelect = annualViewModel::onSelectTab,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            )
            val contentModifier = Modifier.fillMaxSize()
            if (tab == PlanTab.YEAR) {
                val annualState by annualViewModel.uiState.collectAsStateWithLifecycle()
                AnnualPlanView(
                    state = annualState,
                    actions =
                        AnnualPlanActions(
                            onPreviousYear = annualViewModel::onPreviousYear,
                            onNextYear = annualViewModel::onNextYear,
                            onRetry = annualViewModel::onRetry,
                        ),
                    modifier = contentModifier,
                )
            } else {
                MonthPlanBody(
                    state = uiState,
                    viewModel = viewModel,
                    navigation = navigation,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun MonthPlanBody(
    state: MonthlyPlanUiState,
    viewModel: MonthlyPlanViewModel,
    navigation: PlanNavigation,
    modifier: Modifier,
) {
    when (state) {
        MonthlyPlanUiState.Loading ->
            Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        is MonthlyPlanUiState.Error ->
            ErrorState(message = state.message.asString(), onRetry = viewModel::onRetry, modifier = modifier)

        is MonthlyPlanUiState.Content ->
            MonthlyPlanContent(
                state = state,
                viewModel = viewModel,
                navigation = navigation,
                modifier = modifier,
            )
    }
}

/** Where the Plan tab can navigate to, bundled for detekt's LongParameterList. */
private class PlanNavigation(
    val onOpenRecurring: () -> Unit,
    val onOpenEnvelopes: () -> Unit,
    val onOpenSalaryProfile: () -> Unit,
)

@Composable
private fun MonthlyPlanContent(
    state: MonthlyPlanUiState.Content,
    viewModel: MonthlyPlanViewModel,
    navigation: PlanNavigation,
    modifier: Modifier = Modifier,
) {
    var editingAllocation by remember { mutableStateOf<CategoryBudget?>(null) }
    var editingWorkingDays by remember { mutableStateOf(false) }
    val cardModifier = Modifier.fillMaxWidth()

    LazyColumn(
        modifier = modifier,
        contentPadding =
            PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.Space32),
    ) {
        item {
            PeriodSelector(
                period = state.period,
                canGoToNextMonth = state.canGoToNextMonth,
                actions = viewModel.periodActions,
                monthOnly = true,
            )
        }
        item {
            PlanSummaryCard(
                summary = state.summary,
                modifier = cardModifier,
                fixedThisMonthCents = state.fixedThisMonthCents,
            )
        }
        expectedIncomeItem(state, viewModel, navigation, { editingWorkingDays = true }, cardModifier)
        limitsItems(
            state = state,
            viewModel = viewModel,
            actions = LimitsActions(onEdit = { editingAllocation = it }, onManage = navigation.onOpenEnvelopes),
            modifier = cardModifier,
        )
        item { GoalsSection(modifier = cardModifier) }
        item { RecurringSection(onOpen = navigation.onOpenRecurring, modifier = cardModifier) }
    }

    if (editingWorkingDays) {
        WorkingDaysDialog(
            state = state.workingDays,
            carnival = carnivalWeekdayIn(state.workingDays.month),
            onSave = viewModel::onSetWorkingDays,
            onDismiss = { editingWorkingDays = false },
        )
    }
    PlanDialogs(
        state = state,
        editingAllocation = editingAllocation,
        onCloseAllocation = { editingAllocation = null },
        viewModel = viewModel,
    )
}

@Composable
private fun PlanDialogs(
    state: MonthlyPlanUiState.Content,
    editingAllocation: CategoryBudget?,
    onCloseAllocation: () -> Unit,
    viewModel: MonthlyPlanViewModel,
) {
    state.editor?.let {
        IncomeLineSheet(
            editor = it,
            workingDays = state.workingDays,
            onSave = { label, cents, schedule ->
                viewModel.onSaveIncomeLine(label, cents, schedule, state.workingDays.month)
            },
            onDelete = viewModel::onDeleteIncomeLine,
            onDismiss = viewModel::onDismissEditor,
        )
    }
    editingAllocation?.let { budget ->
        EditBudgetDialog(
            budget = budget,
            onSave = { limitCents, skipMonths ->
                viewModel.onSetAllocation(budget.category, limitCents, skipMonths)
                onCloseAllocation()
            },
            onRemove = {
                viewModel.onClearAllocation(budget.category)
                onCloseAllocation()
            },
            onDismiss = onCloseAllocation,
        )
    }
}

private fun LazyListScope.expectedIncomeItem(
    state: MonthlyPlanUiState.Content,
    viewModel: MonthlyPlanViewModel,
    navigation: PlanNavigation,
    onEditWorkingDays: () -> Unit,
    modifier: Modifier,
) {
    item {
        ExpectedIncomeCard(
            model =
                ExpectedIncomeModel(
                    rows = state.incomeRows,
                    canAdd = state.canAddIncomeLine,
                    workingDays = state.workingDays,
                    receipts = state.receipts,
                ),
            actions =
                ExpectedIncomeActions(
                    onAdd = viewModel::onAddIncomeLine,
                    onEdit = viewModel::onEditIncomeLine,
                    onEditWorkingDays = onEditWorkingDays,
                    onOpenSalaryProfile = navigation.onOpenSalaryProfile,
                    onReceive = viewModel::onReceive,
                    onUndoReceive = viewModel::onUndoReceive,
                ),
            modifier = modifier,
        )
    }
}

/** The "Planned vs actual" card: the planned categories with how much of each limit the month has used. */
private fun LazyListScope.plannedVsActualItem(
    progress: List<CategoryBudget>,
    viewModel: MonthlyPlanViewModel,
    modifier: Modifier,
) {
    val planned = progress.filter { it.planned != null }
    if (planned.isEmpty()) return
    item {
        BudgetSection(
            budgets = planned,
            title = stringResource(R.string.plan_planned_vs_actual),
            onSetBudget = viewModel::onSetAllocation,
            onClearBudget = viewModel::onClearAllocation,
            modifier = modifier.padding(top = Dimens.Space24),
        )
    }
}

/** What the limits cards can trigger, bundled for detekt's LongParameterList. */
private class LimitsActions(
    val onEdit: (CategoryBudget) -> Unit,
    val onManage: () -> Unit,
)

/**
 * Without groups: the flat "Limits" card, "Planned vs actual" and the groups empty state (the suggested starter
 * set is created with the names in the current language). With groups: the single "Limits" card by group.
 */
private fun LazyListScope.limitsItems(
    state: MonthlyPlanUiState.Content,
    viewModel: MonthlyPlanViewModel,
    actions: LimitsActions,
    modifier: Modifier,
) {
    if (state.envelopes.envelopes.isNotEmpty()) {
        item {
            LimitsByGroupCard(
                groups = state.limitGroups,
                onEdit = actions.onEdit,
                onManage = actions.onManage,
                modifier = modifier,
            )
        }
        return
    }
    item { AllocationsCard(allocations = state.allocations, onEdit = actions.onEdit, modifier = modifier) }
    plannedVsActualItem(state.progress, viewModel, modifier)
    item {
        SettingsGroup(label = stringResource(R.string.plan_envelopes_title), modifier = modifier) {
            SettingsRow(
                title = stringResource(R.string.plan_envelopes_create),
                onClick = actions.onManage,
                showChevron = true,
            )
        }
    }
}
