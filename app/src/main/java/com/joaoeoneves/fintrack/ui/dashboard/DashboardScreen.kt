package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.TimeRangeFilterRow
import com.joaoeoneves.fintrack.ui.common.formatAmountCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    callbacks: DashboardCallbacks,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onRetry()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DashboardTopBar(onOpenSettings = callbacks.onOpenSettings) },
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
                    onTimeRangeSelected = viewModel::onTimeRangeSelected,
                    callbacks = callbacks,
                    onSetBudget = viewModel::onSetBudget,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }

            is DashboardUiState.Error -> {
                ErrorState(
                    message = state.message,
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

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    onTimeRangeSelected: (TimeRange) -> Unit,
    callbacks: DashboardCallbacks,
    onSetBudget: (ExpenseCategory, Long) -> Unit,
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
            TimeRangeFilterRow(selected = state.timeRange, onSelected = onTimeRangeSelected)
        }

        item { BalanceSummaryCard(state = state, modifier = cardModifier) }

        item { DashboardSummary(state = state, modifier = cardModifier) }

        item {
            BudgetSection(budgets = state.budgets, onSetBudget = onSetBudget, modifier = cardModifier)
        }

        item {
            RecentExpensesCard(
                state = state,
                onOpenList = callbacks.onOpenList,
                onAddExpense = callbacks.onAddExpense,
                modifier = cardModifier,
            )
        }

        item {
            RecentIncomeCard(
                state = state,
                onOpenIncomeList = callbacks.onOpenIncomeList,
                onAddIncome = callbacks.onAddIncome,
                modifier = cardModifier,
            )
        }
    }
}

@Composable
private fun RecentIncomeCard(
    state: DashboardUiState.Content,
    onOpenIncomeList: (TimeRange) -> Unit,
    onAddIncome: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        .clickable { onOpenIncomeList(state.timeRange) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_income),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAddIncome) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_income))
                }
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
                        onClick = { onOpenIncomeList(state.timeRange) },
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
                            .clickable { onOpenIncomeList(state.timeRange) }
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
    onOpenList: (TimeRange) -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        .clickable { onOpenList(state.timeRange) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_recent_expenses),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAddExpense) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_expense))
                }
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
                        onClick = { onOpenList(state.timeRange) },
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
                            .clickable { onOpenList(state.timeRange) }
                            .padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BalanceSummaryCard(
    state: DashboardUiState.Content,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.dashboard_net_balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatAmountCents(state.netCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color =
                    if (state.netCents < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            HorizontalDivider()

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            ) {
                BalanceSummaryColumn(
                    label = stringResource(R.string.dashboard_income_label),
                    amountCents = state.incomeCents,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tint = MaterialTheme.colorScheme.primary,
                    amountColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                BalanceSummaryColumn(
                    label = stringResource(R.string.dashboard_expenses_label),
                    amountCents = state.totalCents,
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    tint = MaterialTheme.colorScheme.error,
                    amountColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BalanceSummaryColumn(
    label: String,
    amountCents: Long,
    icon: ImageVector,
    tint: Color,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = formatAmountCents(amountCents),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )
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
