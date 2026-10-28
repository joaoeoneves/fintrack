package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.joaoeoneves.fintrack.domain.model.carnivalWeekdayIn
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * The income screen, pushed from the Plan tab's "Set up" section: the selected month's expected income (the
 * salary profile, working days and one row per component or manual line, with the green received check). It reuses
 * [MonthlyPlanViewModel] with its own instance, whose month starts at the route's `month`.
 */
@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    onOpenSalaryProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonthlyPlanViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CollectPlanErrors(snackbarHostState = snackbarHostState, events = listOf(viewModel.errorEvent))
    val receiveFeedback = rememberReceiveFeedback(snackbarHostState)
    receiveFeedback.ChangeSheet()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalReceiveFeedback provides receiveFeedback) {
        LargeTitleScaffold(
            title = stringResource(R.string.income_title),
            onBack = onBack,
            modifier = modifier,
            snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
            when (val current = state) {
                MonthlyPlanUiState.Loading ->
                    Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                is MonthlyPlanUiState.Error ->
                    ErrorState(
                        message = current.message.asString(),
                        onRetry = viewModel::onRetry,
                        modifier = contentModifier,
                    )

                is MonthlyPlanUiState.Content ->
                    IncomeContent(current, viewModel, onOpenSalaryProfile, contentModifier)
            }
        }
    }
}

@Composable
private fun IncomeContent(
    state: MonthlyPlanUiState.Content,
    viewModel: MonthlyPlanViewModel,
    onOpenSalaryProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingWorkingDays by remember { mutableStateOf(false) }
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
                        onEditWorkingDays = { editingWorkingDays = true },
                        onOpenSalaryProfile = onOpenSalaryProfile,
                        onReceive = viewModel::onReceive,
                        onUndoReceive = viewModel::onUndoReceive,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (editingWorkingDays) {
        WorkingDaysDialog(
            state = state.workingDays,
            carnival = carnivalWeekdayIn(state.workingDays.month),
            onSave = viewModel::onSetWorkingDays,
            onDismiss = { editingWorkingDays = false },
        )
    }
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
}
