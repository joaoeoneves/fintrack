package com.joaoeoneves.fintrack.ui.expense.list

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.TimeRangeFilterRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onBack: () -> Unit,
    onOpenExpense: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { expense ->
            val result =
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.expense_list_deleted_snackbar, expense.name),
                    actionLabel = undoLabel,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete(expense)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expense_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    onDelete = viewModel::onDeleteExpense,
                    contentPadding = innerPadding,
                )
            }

            is ExpenseListUiState.Error -> {
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
private fun ExpenseListContent(
    state: ExpenseListUiState.Content,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onOpenExpense: (String) -> Unit,
    onDelete: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        TimeRangeFilterRow(selected = state.timeRange, onSelected = onTimeRangeSelected)

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            label = { Text(stringResource(R.string.expense_list_search_label)) },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SortDropdown(
            selected = state.sortOption,
            onSelected = onSortSelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (state.expenses.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (state.query.isBlank()) {
                            stringResource(R.string.dashboard_no_expenses)
                        } else {
                            stringResource(R.string.expense_list_no_match, state.query)
                        },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.expenses, key = { it.id }) { expense ->
                    val dismissState = rememberSwipeToDismissBoxState()

                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd ||
                            dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                        ) {
                            onDelete(expense.id)
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val alignment =
                                when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    SwipeToDismissBoxValue.Settled -> Alignment.CenterEnd
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 16.dp),
                                contentAlignment = alignment,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExpenseRow(
                                expense = expense,
                                onClick = { onOpenExpense(expense.id) },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDelete(expense.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private val SortOption.label: String
    @Composable
    get() =
        when (this) {
            SortOption.DATE_DESC -> stringResource(R.string.sort_newest_first)
            SortOption.DATE_ASC -> stringResource(R.string.sort_oldest_first)
            SortOption.AMOUNT_DESC -> stringResource(R.string.sort_highest_amount)
            SortOption.AMOUNT_ASC -> stringResource(R.string.sort_lowest_amount)
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    selected: SortOption,
    onSelected: (SortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selected.label,
            onValueChange = {},
            label = { Text(stringResource(R.string.sort_by_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
