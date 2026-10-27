package com.joaoeoneves.fintrack.ui.expense.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.optionsIncluding
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.ui.common.DaySectionHeader
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.SearchField
import com.joaoeoneves.fintrack.ui.common.SortDropdown
import com.joaoeoneves.fintrack.ui.common.SwipeToDeleteRow
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.dayHeaderOf
import com.joaoeoneves.fintrack.ui.common.displayName
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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Localized category labels, so the search query also matches the displayed category names.
    val labels = categories.associate { it.id to it.displayName }
    LaunchedEffect(labels) { viewModel.onCategoryLabelsChanged(labels) }

    // Re-subscribes on resume so "This month" re-resolves after the calendar rolls over.
    OnResumeEffect(onResume = viewModel::onRetry)
    val undoLabel = stringResource(R.string.action_undo)
    val deletedSnackbarTemplate = stringResource(R.string.expense_list_deleted_snackbar)
    val locale = currentLocale()

    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { expense ->
            val result =
                snackbarHostState.showSnackbar(
                    message = String.format(locale, deletedSnackbarTemplate, expense.name),
                    actionLabel = undoLabel,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete(expense)
            }
        }
    }

    LargeTitleScaffold(
        title = stringResource(R.string.expense_list_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            is ExpenseListUiState.Loading -> {
                // No content yet; keep the scaffold empty while loading.
            }

            is ExpenseListUiState.Content -> {
                ExpenseListContent(
                    state = state,
                    query = query,
                    categories = categories,
                    actions = viewModel.listActions(onOpenExpense),
                    contentPadding = innerPadding,
                )
            }

            is ExpenseListUiState.Error -> {
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

private fun ExpenseListViewModel.listActions(onOpenExpense: (String) -> Unit): ExpenseListActions =
    ExpenseListActions(
        periodActions = periodActions(),
        onQueryChanged = ::onQueryChanged,
        onCategorySelected = ::onCategorySelected,
        onSortSelected = ::onSortSelected,
        onOpenExpense = onOpenExpense,
        onDelete = ::onDeleteExpense,
    )

private fun ExpenseListViewModel.periodActions(): PeriodActions =
    PeriodActions(onPrevious = ::onPreviousMonth, onNext = ::onNextMonth, onSelect = ::onPeriodSelected)

/** Bundles the expense list's row-level and filter actions to keep [ExpenseListContent]'s own parameter list short. */
private data class ExpenseListActions(
    val periodActions: PeriodActions,
    val onQueryChanged: (String) -> Unit,
    val onCategorySelected: (CategoryId?) -> Unit,
    val onSortSelected: (SortOption) -> Unit,
    val onOpenExpense: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

@Composable
private fun ExpenseListContent(
    state: ExpenseListUiState.Content,
    query: String,
    categories: List<Category>,
    actions: ExpenseListActions,
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

        SearchField(
            query = query,
            onQueryChanged = actions.onQueryChanged,
            label = stringResource(R.string.expense_list_search_label),
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
        )

        CategoryFilterRow(
            // Archived categories are hidden unless one is the active filter.
            categories = categories.optionsIncluding(state.categoryFilter?.let(categories::resolve)),
            selected = state.categoryFilter,
            onSelected = actions.onCategorySelected,
            leading = {
                SortDropdown(
                    options = SortOption.entries,
                    selected = state.sortOption,
                    onSelected = actions.onSortSelected,
                    optionLabel = { it.label },
                )
            },
            modifier = Modifier.padding(bottom = Dimens.Space8),
        )

        ExpenseListItems(
            state = state,
            onOpenExpense = actions.onOpenExpense,
            onDelete = actions.onDelete,
        )
    }
}

@Composable
private fun ExpenseListItems(
    state: ExpenseListUiState.Content,
    onOpenExpense: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val expenses = state.expenses
    val sortOption = state.sortOption
    val query = state.query
    val categoryFilter = state.categoryFilter
    if (expenses.isEmpty()) {
        EmptyState(
            title =
                when {
                    query.isNotBlank() -> stringResource(R.string.expense_list_no_match, query)
                    categoryFilter != null -> stringResource(R.string.expense_list_no_match_filter)
                    else -> stringResource(R.string.dashboard_no_expenses)
                },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(expenses) { LocalDate.now(zone) }
    val grouped = shouldGroupByDay(sortOption)
    val sections = remember(expenses, grouped) { if (grouped) groupByDay(expenses, zone) else emptyList() }
    val deleteLabel = stringResource(R.string.cd_delete)
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(TestTags.EXPENSE_LIST)) {
        if (grouped) {
            sections.forEach { section ->
                item(key = "day_${section.date}") {
                    DaySectionHeader(
                        header = dayHeaderOf(section.date, today),
                        modifier = Modifier.animateItem().padding(horizontal = Dimens.ScreenPadding),
                    )
                }
                items(section.items, key = { it.id }) { expense ->
                    ExpenseListRow(expense, false, deleteLabel, onOpenExpense, onDelete, Modifier.animateItem())
                }
            }
        } else {
            items(expenses, key = { it.id }) { expense ->
                ExpenseListRow(expense, true, deleteLabel, onOpenExpense, onDelete, Modifier.animateItem())
            }
        }
    }
}

@Suppress("LongParameterList") // row content plus its two callbacks and modifier
@Composable
private fun ExpenseListRow(
    expense: Expense,
    showDate: Boolean,
    deleteLabel: String,
    onOpenExpense: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDeleteRow(onDelete = { onDelete(expense.id) }, deleteLabel = deleteLabel, modifier = modifier) {
        ExpenseRow(
            expense = expense,
            showDate = showDate,
            onClick = { onOpenExpense(expense.id) },
            modifier = Modifier.weight(1f).padding(horizontal = Dimens.ScreenPadding),
        )
    }
}

/** Date sorts group the list under day headers; amount sorts interleave days, so they stay flat. */
fun shouldGroupByDay(sort: SortOption): Boolean = sort == SortOption.DATE_DESC || sort == SortOption.DATE_ASC

private val SortOption.label: String
    @Composable
    get() =
        when (this) {
            SortOption.DATE_DESC -> stringResource(R.string.sort_newest_first)
            SortOption.DATE_ASC -> stringResource(R.string.sort_oldest_first)
            SortOption.AMOUNT_DESC -> stringResource(R.string.sort_highest_amount)
            SortOption.AMOUNT_ASC -> stringResource(R.string.sort_lowest_amount)
        }
