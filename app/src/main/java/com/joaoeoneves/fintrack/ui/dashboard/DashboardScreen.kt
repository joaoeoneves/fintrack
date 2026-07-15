package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onOpenList: (TimeRange) -> Unit,
    onAddExpense: () -> Unit,
    onOpenIncomeList: (TimeRange) -> Unit,
    onAddIncome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("FinTrack") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("Add expense")
            }
        },
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
                    onOpenList = onOpenList,
                    onSetBudget = viewModel::onSetBudget,
                    onOpenIncomeList = onOpenIncomeList,
                    onAddIncome = onAddIncome,
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

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onOpenList: (TimeRange) -> Unit,
    onSetBudget: (ExpenseCategory, Long) -> Unit,
    onOpenIncomeList: (TimeRange) -> Unit,
    onAddIncome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            TimeRangeFilterRow(selected = state.timeRange, onSelected = onTimeRangeSelected)
        }

        item {
            BalanceSummaryCard(
                state = state,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item {
            DashboardSummary(
                state = state,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item {
            BudgetSection(
                budgets = state.budgets,
                onSetBudget = onSetBudget,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (!state.expenses.isEmpty()) {
            item {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column {
                        Text(
                            text = "Recent expenses",
                            style = MaterialTheme.typography.titleMedium,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenList(state.timeRange) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        )

                        state.recentExpenses.forEach { expense ->
                            ExpenseRow(
                                expense = expense,
                                onClick = { onOpenList(state.timeRange) },
                            )
                            HorizontalDivider()
                        }

                        Text(
                            text = "See all expenses",
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

        item {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            text = "Recent income",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onAddIncome) {
                            Icon(Icons.Default.Add, contentDescription = "Add income")
                        }
                    }

                    if (state.recentIncome.isEmpty()) {
                        Text(
                            text = "No income in this period yet.",
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
                            text = "See all income",
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

        item {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {}
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
                text = "Net Balance",
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
                    label = "Income",
                    amountCents = state.incomeCents,
                    icon = Icons.Filled.TrendingUp,
                    tint = MaterialTheme.colorScheme.primary,
                    amountColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                BalanceSummaryColumn(
                    label = "Expenses",
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
                text = "No expenses in this period yet.",
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
