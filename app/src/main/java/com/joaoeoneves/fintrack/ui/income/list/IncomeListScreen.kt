package com.joaoeoneves.fintrack.ui.income.list

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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.TimeRangeFilterRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeListScreen(
    onBack: () -> Unit,
    onOpenIncome: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncomeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { income ->
            val result =
                snackbarHostState.showSnackbar(
                    message = "Deleted \"${income.source}\"",
                    actionLabel = "Undo",
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete(income)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("All income") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            is IncomeListUiState.Loading -> {
                // No content yet; keep the scaffold empty while loading.
            }

            is IncomeListUiState.Content -> {
                IncomeListContent(
                    state = state,
                    onTimeRangeSelected = viewModel::onTimeRangeSelected,
                    onOpenIncome = onOpenIncome,
                    onDelete = viewModel::onDeleteIncome,
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
private fun IncomeListContent(
    state: IncomeListUiState.Content,
    onTimeRangeSelected: (TimeRange) -> Unit,
    onOpenIncome: (String) -> Unit,
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

        if (state.income.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No income in this period yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.income, key = { it.id }) { income ->
                    val dismissState =
                        rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd ||
                                    value == SwipeToDismissBoxValue.EndToStart
                                ) {
                                    onDelete(income.id)
                                    true
                                } else {
                                    false
                                }
                            },
                        )

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
                            IncomeRow(
                                income = income,
                                onClick = { onOpenIncome(income.id) },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDelete(income.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
