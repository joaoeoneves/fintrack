package com.joaoeoneves.fintrack.ui.expense.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.SearchField
import com.joaoeoneves.fintrack.ui.common.SortDropdown
import com.joaoeoneves.fintrack.ui.common.SwipeToDeleteRow
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
    // Bound directly to the search field below instead of Content.query, which only updates
    // after a round trip through the repository's live Firestore listener and is too slow to
    // drive a controlled text field without dropping/reordering fast-typed characters.
    val query by viewModel.query.collectAsStateWithLifecycle()
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
        topBar = { ExpenseListTopBar(onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            is ExpenseListUiState.Loading -> {
                // No content yet; keep the scaffold empty while loading.
            }

            is ExpenseListUiState.Content -> {
                ExpenseListContent(
                    state = state,
                    query = query,
                    actions =
                        ExpenseListActions(
                            onTimeRangeSelected = viewModel::onTimeRangeSelected,
                            onQueryChanged = viewModel::onQueryChanged,
                            onSortSelected = viewModel::onSortSelected,
                            onOpenExpense = onOpenExpense,
                            onDelete = viewModel::onDeleteExpense,
                        ),
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
private fun ExpenseListTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.expense_list_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
    )
}

/** Bundles the expense list's row-level and filter actions to keep [ExpenseListContent]'s own parameter list short. */
private data class ExpenseListActions(
    val onTimeRangeSelected: (TimeRange) -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onSortSelected: (SortOption) -> Unit,
    val onOpenExpense: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

@Composable
private fun ExpenseListContent(
    state: ExpenseListUiState.Content,
    query: String,
    actions: ExpenseListActions,
    contentPadding: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        TimeRangeFilterRow(selected = state.timeRange, onSelected = actions.onTimeRangeSelected)

        SearchField(
            query = query,
            onQueryChanged = actions.onQueryChanged,
            label = stringResource(R.string.expense_list_search_label),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SortDropdown(
            options = SortOption.entries,
            selected = state.sortOption,
            onSelected = actions.onSortSelected,
            optionLabel = { it.label },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        ExpenseListItems(
            expenses = state.expenses,
            query = state.query,
            onOpenExpense = actions.onOpenExpense,
            onDelete = actions.onDelete,
        )
    }
}

@Composable
private fun ExpenseListItems(
    expenses: List<Expense>,
    query: String,
    onOpenExpense: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (expenses.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    if (query.isBlank()) {
                        stringResource(R.string.dashboard_no_expenses)
                    } else {
                        stringResource(R.string.expense_list_no_match, query)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(expenses, key = { it.id }) { expense ->
                SwipeToDeleteRow(onDelete = { onDelete(expense.id) }) {
                    ExpenseRow(
                        expense = expense,
                        onClick = { onOpenExpense(expense.id) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDelete(expense.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
                    }
                }
                HorizontalDivider()
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
