package ptech.joaoe.agenticusage.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.model.TimeRange
import ptech.joaoe.agenticusage.ui.common.ErrorState
import ptech.joaoe.agenticusage.ui.common.ExpenseRow
import ptech.joaoe.agenticusage.ui.common.IncomeRow
import ptech.joaoe.agenticusage.ui.common.TimeRangeFilterRow
import ptech.joaoe.agenticusage.ui.common.formatAmountCents
import ptech.joaoe.agenticusage.ui.importexport.ExportUiState
import ptech.joaoe.agenticusage.ui.importexport.ImportExportViewModel
import ptech.joaoe.agenticusage.ui.importexport.ImportPreviewDialog
import ptech.joaoe.agenticusage.ui.importexport.ImportUiState
import ptech.joaoe.agenticusage.ui.importexport.MessageDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenList: (TimeRange) -> Unit,
    onAddExpense: () -> Unit,
    onOpenIncomeList: (TimeRange) -> Unit,
    onAddIncome: () -> Unit,
    onSignOut: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    importExportViewModel: ImportExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by importExportViewModel.importState.collectAsStateWithLifecycle()
    val exportState by importExportViewModel.exportState.collectAsStateWithLifecycle()

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(importExportViewModel::onImportFileSelected)
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let(importExportViewModel::onExportTargetSelected)
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            var menuExpanded by remember { mutableStateOf(false) }
            TopAppBar(
                title = { Text("AgenticUsage") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Import CSV") },
                            onClick = {
                                menuExpanded = false
                                importLauncher.launch(
                                    arrayOf("text/csv", "text/comma-separated-values", "*/*"),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV") },
                            onClick = {
                                menuExpanded = false
                                exportLauncher.launch("agenticusage-expenses.csv")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isDarkTheme) "Switch to light mode" else "Switch to dark mode") },
                            onClick = {
                                menuExpanded = false
                                onToggleTheme()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                menuExpanded = false
                                onSignOut()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddExpense) {
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

    when (val state = importState) {
        is ImportUiState.Preview -> {
            ImportPreviewDialog(
                state = state,
                onConfirm = importExportViewModel::onConfirmImport,
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        is ImportUiState.Done -> {
            MessageDialog(
                title = "Import complete",
                message = "Imported ${state.importedCount} expense(s).",
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        is ImportUiState.Error -> {
            MessageDialog(
                title = "Import failed",
                message = state.message,
                onDismiss = importExportViewModel::onDismissImport,
            )
        }

        ImportUiState.Idle, ImportUiState.Loading, ImportUiState.Importing -> Unit
    }

    when (val state = exportState) {
        is ExportUiState.Done -> {
            MessageDialog(
                title = "Export complete",
                message = "Exported ${state.exportedCount} expense(s).",
                onDismiss = importExportViewModel::onDismissExport,
            )
        }

        is ExportUiState.Error -> {
            MessageDialog(
                title = "Export failed",
                message = state.message,
                onDismiss = importExportViewModel::onDismissExport,
            )
        }

        ExportUiState.Idle, ExportUiState.Exporting -> Unit
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
            DashboardSummary(state = state)
        }

        item {
            NetBalanceSummary(state = state)
        }

        item {
            BudgetSection(
                budgets = state.budgets,
                onSetBudget = onSetBudget,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!state.expenses.isEmpty()) {
            item {
                Text(
                    text = "Recent expenses",
                    style = MaterialTheme.typography.titleMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenList(state.timeRange) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(state.recentExpenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    onClick = { onOpenList(state.timeRange) },
                )
                HorizontalDivider()
            }

            item {
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

        item {
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
        }

        if (state.recentIncome.isEmpty()) {
            item {
                Text(
                    text = "No income in this period yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(state.recentIncome, key = { it.id }) { income ->
                IncomeRow(
                    income = income,
                    onClick = { onOpenIncomeList(state.timeRange) },
                )
                HorizontalDivider()
            }

            item {
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

        item {
            Box(modifier = Modifier.padding(bottom = 80.dp)) {}
        }
    }
}

@Composable
private fun NetBalanceSummary(state: DashboardUiState.Content) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Income: ${formatAmountCents(state.incomeCents)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Net balance: ${formatAmountCents(state.netCents)}",
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (state.netCents < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
        )
    }
}

@Composable
private fun DashboardSummary(state: DashboardUiState.Content) {
    if (state.expenses.isEmpty()) {
        Box(
            modifier =
                Modifier
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
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "Total: ${formatAmountCents(state.totalCents)}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            PieChart(
                slices = state.categoryTotals,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
