package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    callbacks: DashboardCallbacks,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OnResumeEffect(onResume = viewModel::onRetry)

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.budgetErrorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DashboardTopBar(onOpenSettings = callbacks.onOpenSettings) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is DashboardUiState.Content -> {
                DashboardContent(
                    state = state,
                    periodActions =
                        PeriodActions(
                            onPrevious = viewModel::onPreviousMonth,
                            onNext = viewModel::onNextMonth,
                            onSelect = viewModel::onPeriodSelected,
                        ),
                    callbacks = callbacks,
                    budgetActions =
                        BudgetActions(
                            onSetBudget = viewModel::onSetBudget,
                            onClearBudget = viewModel::onClearBudget,
                        ),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }

            is DashboardUiState.Error -> {
                ErrorState(
                    message = state.message.asString(),
                    onRetry = viewModel::onRetry,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
    )
}

/** Bundles the budget write callbacks to keep [DashboardContent] under detekt's LongParameterList. */
private data class BudgetActions(
    val onSetBudget: (Category, Long) -> Unit,
    val onClearBudget: (Category) -> Unit,
)

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    periodActions: PeriodActions,
    callbacks: DashboardCallbacks,
    budgetActions: BudgetActions,
    modifier: Modifier = Modifier,
) {
    val cardModifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            PeriodSelector(
                period = state.period,
                canGoToNextMonth = state.canGoToNextMonth,
                actions = periodActions,
            )
        }

        item { BalanceSummaryCard(state = state, modifier = cardModifier) }

        item { DashboardSummary(state = state, modifier = cardModifier) }

        item {
            BudgetSection(
                budgets = state.budgets,
                title = budgetSectionTitle(state),
                onSetBudget = budgetActions.onSetBudget,
                onClearBudget = budgetActions.onClearBudget,
                modifier = cardModifier,
            )
        }

        item {
            RecentExpensesCard(state = state, callbacks = callbacks, modifier = cardModifier)
        }

        item {
            RecentIncomeCard(state = state, callbacks = callbacks, modifier = cardModifier)
        }
    }
}

@Composable
private fun budgetSectionTitle(state: DashboardUiState.Content): String =
    if (state.budgetMonthIsCurrent) {
        stringResource(R.string.dashboard_budgets_title)
    } else {
        stringResource(R.string.dashboard_budgets_title_month, formatMonthLabel(state.budgetMonth, currentLocale()))
    }

@Composable
private fun RecentIncomeCard(
    state: DashboardUiState.Content,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val onOpenIncomeList = callbacks.onOpenIncomeList
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenIncomeList(state.period) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_income),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                LabeledIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_income),
                    onClick = callbacks.onAddIncome,
                )
            }

            if (state.recentIncome.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_no_income),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                state.recentIncome.forEach { income ->
                    IncomeRow(
                        income = income,
                        onClick = { callbacks.onOpenIncome(income.id) },
                    )
                    HorizontalDivider()
                }

                Text(
                    text = stringResource(R.string.dashboard_see_all_income),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenIncomeList(state.period) }
                            .padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RecentExpensesCard(
    state: DashboardUiState.Content,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val onOpenList = callbacks.onOpenList
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenList(state.period) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_expenses),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                LabeledIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_expense),
                    onClick = callbacks.onAddExpense,
                )
            }

            if (state.recentExpenses.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_recent_expenses_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                state.recentExpenses.forEach { expense ->
                    ExpenseRow(
                        expense = expense,
                        onClick = { callbacks.onOpenExpense(expense.id) },
                    )
                    HorizontalDivider()
                }

                Text(
                    text = stringResource(R.string.dashboard_see_all_expenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenList(state.period) }
                            .padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DashboardSummary(
    state: DashboardUiState.Content,
    modifier: Modifier = Modifier,
) {
    if (state.expenses.isEmpty()) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.dashboard_no_expenses),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            PieChart(
                slices = state.categoryTotals,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            )
        }
    }
}
