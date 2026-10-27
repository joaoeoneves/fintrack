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
import androidx.compose.ui.platform.testTag
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
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringCard
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringModel
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringSnackbars
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringUiState
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    callbacks: DashboardCallbacks,
    viewModel: DashboardViewModel = hiltViewModel(),
    upcomingViewModel: UpcomingRecurringViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val upcomingState by upcomingViewModel.uiState.collectAsStateWithLifecycle()

    OnResumeEffect(
        onResume = {
            viewModel.onRetry()
            upcomingViewModel.onRetry()
        },
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.budgetErrorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    UpcomingRecurringSnackbars(upcomingViewModel, snackbarHostState)
    val actions = dashboardActions(viewModel, upcomingViewModel, upcomingState, callbacks)

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
                    actions = actions,
                    callbacks = callbacks,
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
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(TestTags.DASHBOARD_SETTINGS)) {
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

private fun dashboardActions(
    viewModel: DashboardViewModel,
    upcomingViewModel: UpcomingRecurringViewModel,
    upcomingState: UpcomingRecurringUiState,
    callbacks: DashboardCallbacks,
) = DashboardActions(
    period =
        PeriodActions(
            onPrevious = viewModel::onPreviousMonth,
            onNext = viewModel::onNextMonth,
            onSelect = viewModel::onPeriodSelected,
        ),
    budget = BudgetActions(onSetBudget = viewModel::onSetBudget, onClearBudget = viewModel::onClearBudget),
    upcoming =
        UpcomingRecurringModel(
            state = upcomingState,
            onConfirm = upcomingViewModel::onConfirm,
            onSkip = upcomingViewModel::onSkip,
            onManage = callbacks.onOpenRecurring,
        ),
)

/** Everything the dashboard content can do besides navigation, bundled for detekt's LongParameterList. */
private data class DashboardActions(
    val period: PeriodActions,
    val budget: BudgetActions,
    val upcoming: UpcomingRecurringModel,
)

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    actions: DashboardActions,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val cardModifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)

    LazyColumn(
        modifier = modifier.testTag(TestTags.DASHBOARD_LIST),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            PeriodSelector(
                period = state.period,
                canGoToNextMonth = state.canGoToNextMonth,
                actions = actions.period,
            )
        }

        item { BalanceSummaryCard(state = state, modifier = cardModifier) }

        item {
            MonthlyPlanCard(
                state = state,
                onOpenMonthlyPlan = callbacks.onOpenMonthlyPlan,
                modifier = cardModifier,
            )
        }

        item { UpcomingRecurringCard(model = actions.upcoming, modifier = cardModifier) }

        item { SpendingBreakdownCard(state = state, modifier = cardModifier) }

        item {
            BudgetSection(
                budgets = state.budgets,
                title = budgetSectionTitle(state),
                onSetBudget = actions.budget.onSetBudget,
                onClearBudget = actions.budget.onClearBudget,
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
                    modifier = Modifier.testTag(TestTags.DASHBOARD_ADD_EXPENSE),
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

                SeeAllExpensesLink(onClick = { onOpenList(state.period) })
            }
        }
    }
}

@Composable
private fun SeeAllExpensesLink(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.dashboard_see_all_expenses),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(TestTags.SEE_ALL_EXPENSES)
                .clickable(onClick = onClick)
                .padding(16.dp),
        textAlign = TextAlign.Center,
    )
}
