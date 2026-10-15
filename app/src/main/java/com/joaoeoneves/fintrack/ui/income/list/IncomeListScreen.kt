package com.joaoeoneves.fintrack.ui.income.list

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.SearchField
import com.joaoeoneves.fintrack.ui.common.SortDropdown
import com.joaoeoneves.fintrack.ui.common.SwipeToDeleteRow
import com.joaoeoneves.fintrack.ui.common.TimeRangeFilterRow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeListScreen(
    onBack: () -> Unit,
    onOpenIncome: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncomeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Bound directly to the search field below instead of Content.query, which only updates
    // after a round trip through the repository's live Firestore listener and is too slow to
    // drive a controlled text field without dropping/reordering fast-typed characters.
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val deletedSnackbarTemplate = stringResource(R.string.income_list_deleted_snackbar)

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { income ->
            val result =
                snackbarHostState.showSnackbar(
                    message = String.format(Locale.getDefault(), deletedSnackbarTemplate, income.source),
                    actionLabel = undoLabel,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete(income)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { IncomeListTopBar(onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            is IncomeListUiState.Loading -> {
                // No content yet; keep the scaffold empty while loading.
            }

            is IncomeListUiState.Content -> {
                IncomeListContent(
                    state = state,
                    query = query,
                    actions =
                        IncomeListActions(
                            onTimeRangeSelected = viewModel::onTimeRangeSelected,
                            onQueryChanged = viewModel::onQueryChanged,
                            onSortSelected = viewModel::onSortSelected,
                            onOpenIncome = onOpenIncome,
                            onDelete = viewModel::onDeleteIncome,
                        ),
                    contentPadding = innerPadding,
                )
            }

            is IncomeListUiState.Error -> {
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
private fun IncomeListTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.income_list_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
    )
}

/** Bundles the income list's row-level and filter actions to keep [IncomeListContent]'s own parameter list short. */
private data class IncomeListActions(
    val onTimeRangeSelected: (TimeRange) -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onSortSelected: (IncomeSortOption) -> Unit,
    val onOpenIncome: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

@Composable
private fun IncomeListContent(
    state: IncomeListUiState.Content,
    query: String,
    actions: IncomeListActions,
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
            label = stringResource(R.string.income_list_search_label),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SortDropdown(
            options = IncomeSortOption.entries,
            selected = state.sortOption,
            onSelected = actions.onSortSelected,
            optionLabel = { it.label },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        IncomeListItems(
            income = state.income,
            query = state.query,
            onOpenIncome = actions.onOpenIncome,
            onDelete = actions.onDelete,
        )
    }
}

@Composable
private fun IncomeListItems(
    income: List<Income>,
    query: String,
    onOpenIncome: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (income.isEmpty()) {
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
                        stringResource(R.string.dashboard_no_income)
                    } else {
                        stringResource(R.string.income_list_no_match, query)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(income, key = { it.id }) { incomeItem ->
                SwipeToDeleteRow(onDelete = { onDelete(incomeItem.id) }) {
                    IncomeRow(
                        income = incomeItem,
                        onClick = { onOpenIncome(incomeItem.id) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDelete(incomeItem.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private val IncomeSortOption.label: String
    @Composable
    get() =
        when (this) {
            IncomeSortOption.DATE_DESC -> stringResource(R.string.sort_newest_first)
            IncomeSortOption.DATE_ASC -> stringResource(R.string.sort_oldest_first)
            IncomeSortOption.AMOUNT_DESC -> stringResource(R.string.sort_highest_amount)
            IncomeSortOption.AMOUNT_ASC -> stringResource(R.string.sort_lowest_amount)
        }
