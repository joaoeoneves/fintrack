package ptech.joaoe.agenticusage.ui.expense.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.TimeRange
import ptech.joaoe.agenticusage.ui.common.ErrorState
import ptech.joaoe.agenticusage.ui.common.ExpenseRow
import ptech.joaoe.agenticusage.ui.common.TimeRangeFilterRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onBack: () -> Unit,
    onOpenExpense: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("All expenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is ExpenseListUiState.Loading -> {
                // No content yet; keep the scaffold empty while loading.
            }

            is ExpenseListUiState.Content -> {
                ExpenseListContent(
                    state = state,
                    onTimeRangeSelected = viewModel::onTimeRangeSelected,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSortSelected = viewModel::onSortSelected,
                    onOpenExpense = onOpenExpense,
                    onDeleteRequested = { expenseToDelete = it },
                    contentPadding = innerPadding
                )
            }

            is ExpenseListUiState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = viewModel::onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    val pendingDelete = expenseToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete this expense?") },
            text = { Text(pendingDelete.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteExpense(pendingDelete.id)
                    expenseToDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseListContent(
    state: ExpenseListUiState.Content,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onOpenExpense: (String) -> Unit,
    onDeleteRequested: (Expense) -> Unit,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        TimeRangeFilterRow(selected = state.timeRange, onSelected = onTimeRangeSelected)

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        SortDropdown(
            selected = state.sortOption,
            onSelected = onSortSelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (state.expenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.query.isBlank()) {
                        "No expenses in this period yet."
                    } else {
                        "No expenses match \"${state.query}\"."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.expenses, key = { it.id }) { expense ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpenseRow(
                            expense = expense,
                            onClick = { onOpenExpense(expense.id) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDeleteRequested(expense) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    selected: SortOption,
    onSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selected.label,
            onValueChange = {},
            label = { Text("Sort by") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
