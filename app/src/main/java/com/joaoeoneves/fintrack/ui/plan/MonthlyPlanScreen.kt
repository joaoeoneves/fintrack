package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.SettingsIconButton
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.dashboard.BudgetSection
import com.joaoeoneves.fintrack.ui.dashboard.EditBudgetDialog
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.settings.RecurringSection
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The Plan tab: the monthly plan, the selected month's planned-vs-actual progress and the recurring list link. */
@Composable
fun PlanScreen(
    onOpenSettings: () -> Unit,
    onOpenRecurring: () -> Unit,
    viewModel: MonthlyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }

    LargeTitleScaffold(
        title = stringResource(R.string.nav_plan),
        actions = { SettingsIconButton(onClick = onOpenSettings) },
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val state = uiState) {
            MonthlyPlanUiState.Loading ->
                Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is MonthlyPlanUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = viewModel::onRetry, modifier = contentModifier)

            is MonthlyPlanUiState.Content -> MonthlyPlanContent(state, viewModel, onOpenRecurring, contentModifier)
        }
    }
}

@Composable
private fun MonthlyPlanContent(
    state: MonthlyPlanUiState.Content,
    viewModel: MonthlyPlanViewModel,
    onOpenRecurring: () -> Unit,
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
        expectedIncomeItem(state, viewModel, onEditWorkingDays = { editingWorkingDays = true }, modifier = cardModifier)
        item {
            AllocationsCard(
                allocations = state.allocations,
                onEdit = { editingAllocation = it },
                modifier = cardModifier,
            )
        }
        plannedVsActualItem(state.progress, viewModel, cardModifier)
        item { RecurringSection(onOpen = onOpenRecurring, modifier = cardModifier) }
    }

    if (editingWorkingDays) {
        WorkingDaysDialog(
            state = state.workingDays,
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
            onSave = {
                viewModel.onSetAllocation(budget.category, it)
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
    onEditWorkingDays: () -> Unit,
    modifier: Modifier,
) {
    item {
        ExpectedIncomeCard(
            lines = state.incomeLines,
            canAdd = state.canAddIncomeLine,
            workingDays = state.workingDays,
            actions =
                ExpectedIncomeActions(
                    onAdd = viewModel::onAddIncomeLine,
                    onEdit = viewModel::onEditIncomeLine,
                    onEditWorkingDays = onEditWorkingDays,
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
    val planned = progress.filter { it.limitCents != null }
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
