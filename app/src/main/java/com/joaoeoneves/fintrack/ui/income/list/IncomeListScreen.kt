package com.joaoeoneves.fintrack.ui.income.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.ui.common.DaySectionHeader
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.SearchField
import com.joaoeoneves.fintrack.ui.common.SortDropdown
import com.joaoeoneves.fintrack.ui.common.SwipeToDeleteRow
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.dayHeaderOf
import com.joaoeoneves.fintrack.ui.common.groupByDay
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.theme.Dimens
import java.time.LocalDate
import java.time.ZoneId

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

    // Re-subscribes on resume so "This month" re-resolves after the calendar rolls over.
    OnResumeEffect(onResume = viewModel::onRetry)
    val undoLabel = stringResource(R.string.action_undo)
    val deletedSnackbarTemplate = stringResource(R.string.income_list_deleted_snackbar)
    val locale = currentLocale()

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { income ->
            val result =
                snackbarHostState.showSnackbar(
                    message = String.format(locale, deletedSnackbarTemplate, income.source),
                    actionLabel = undoLabel,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete(income)
            }
        }
    }

    LargeTitleScaffold(
        title = stringResource(R.string.income_list_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
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
                            periodActions = viewModel.periodActions(),
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

private fun IncomeListViewModel.periodActions(): PeriodActions =
    PeriodActions(onPrevious = ::onPreviousMonth, onNext = ::onNextMonth, onSelect = ::onPeriodSelected)

/** Bundles the income list's row-level and filter actions to keep [IncomeListContent]'s own parameter list short. */
private data class IncomeListActions(
    val periodActions: PeriodActions,
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
        PeriodSelector(
            period = state.period,
            canGoToNextMonth = state.canGoToNextMonth,
            actions = actions.periodActions,
        )

        Row(
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = query,
                onQueryChanged = actions.onQueryChanged,
                label = stringResource(R.string.income_list_search_label),
                modifier = Modifier.weight(1f),
            )
            SortDropdown(
                options = IncomeSortOption.entries,
                selected = state.sortOption,
                onSelected = actions.onSortSelected,
                optionLabel = { it.label },
            )
        }

        IncomeListItems(
            income = state.income,
            sortOption = state.sortOption,
            query = state.query,
            onOpenIncome = actions.onOpenIncome,
            onDelete = actions.onDelete,
        )
    }
}

@Composable
private fun IncomeListItems(
    income: List<Income>,
    sortOption: IncomeSortOption,
    query: String,
    onOpenIncome: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (income.isEmpty()) {
        EmptyState(
            title =
                if (query.isBlank()) {
                    stringResource(R.string.dashboard_no_income)
                } else {
                    stringResource(R.string.income_list_no_match, query)
                },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(income) { LocalDate.now(zone) }
    val grouped = shouldGroupByDay(sortOption)
    val sections = remember(income, grouped) { if (grouped) groupByDay(income, zone) else emptyList() }
    val deleteLabel = stringResource(R.string.cd_delete)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (grouped) {
            sections.forEach { section ->
                item(key = "day_${section.date}") {
                    DaySectionHeader(
                        header = dayHeaderOf(section.date, today),
                        modifier = Modifier.animateItem().padding(horizontal = Dimens.ScreenPadding),
                    )
                }
                items(section.items, key = { it.id }) { entry ->
                    IncomeListRow(entry, false, deleteLabel, onOpenIncome, onDelete, Modifier.animateItem())
                }
            }
        } else {
            items(income, key = { it.id }) { entry ->
                IncomeListRow(entry, true, deleteLabel, onOpenIncome, onDelete, Modifier.animateItem())
            }
        }
    }
}

@Suppress("LongParameterList") // row content plus its two callbacks and modifier
@Composable
private fun IncomeListRow(
    income: Income,
    showDate: Boolean,
    deleteLabel: String,
    onOpenIncome: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDeleteRow(onDelete = { onDelete(income.id) }, deleteLabel = deleteLabel, modifier = modifier) {
        IncomeRow(
            income = income,
            showDate = showDate,
            onClick = { onOpenIncome(income.id) },
            modifier = Modifier.weight(1f).padding(horizontal = Dimens.ScreenPadding),
        )
    }
}

/** Date sorts group the list under day headers; amount sorts interleave days, so they stay flat. */
fun shouldGroupByDay(sort: IncomeSortOption): Boolean {
    val dateSorts = setOf(IncomeSortOption.DATE_DESC, IncomeSortOption.DATE_ASC)
    return sort in dateSorts
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
