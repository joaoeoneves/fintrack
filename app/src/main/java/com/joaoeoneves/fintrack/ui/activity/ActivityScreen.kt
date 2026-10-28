package com.joaoeoneves.fintrack.ui.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.AccountAvatar
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.designsystem.AddTransactionPill
import com.joaoeoneves.fintrack.ui.designsystem.BindTo
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.PillVisibility
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The Activity tab: one filterable, searchable, day-grouped list of expenses and income. */
@Composable
fun ActivityScreen(
    callbacks: ActivityCallbacks,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Bound directly to the search field instead of Content.filters.query, which only updates after
    // a round trip through the derived state and would drop/reorder fast-typed characters.
    val query by viewModel.query.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Localized category labels, so the search query also matches the displayed category names.
    val labels = categories.associate { it.id to it.displayName }
    LaunchedEffect(labels) { viewModel.onCategoryLabelsChanged(labels) }

    // Re-subscribes on resume so "This month" re-resolves after the calendar rolls over.
    OnResumeEffect(onResume = viewModel::onRetry)
    DeletedSnackbar(viewModel, snackbarHostState)

    LargeTitleScaffold(
        title = stringResource(R.string.activity_title),
        modifier = modifier,
        navigationIcon = { AccountAvatar(onClick = callbacks.onOpenSettings) },
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            is ActivityUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ActivityUiState.Content -> {
                ActivityContent(
                    state = state,
                    query = query,
                    categories = categories,
                    actions = viewModel.activityActions(callbacks),
                    contentPadding = innerPadding,
                )
            }

            is ActivityUiState.Error -> {
                ErrorState(
                    message = state.message.asString(),
                    onRetry = viewModel::onRetry,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun DeletedSnackbar(
    viewModel: ActivityViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val undoLabel = stringResource(R.string.action_undo)
    val deletedTemplate = stringResource(R.string.activity_deleted_snackbar)
    val locale = currentLocale()
    LaunchedEffect(Unit) {
        viewModel.undoEvent.collect { transaction ->
            val result =
                snackbarHostState.showSnackbar(
                    message = String.format(locale, deletedTemplate, transaction.title()),
                    actionLabel = undoLabel,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete(transaction)
            }
        }
    }
}

private fun Transaction.title(): String =
    when (this) {
        is Expense -> name
        is Income -> source
    }

private fun ActivityViewModel.activityActions(callbacks: ActivityCallbacks): ActivityActions =
    ActivityActions(
        period = periodActions,
        onTypeSelected = ::onTypeSelected,
        onQueryChanged = ::onQueryChanged,
        onCategorySelected = ::onCategorySelected,
        onEnvelopeSelected = ::onEnvelopeSelected,
        onSortSelected = ::onSortSelected,
        onOpen = { transaction ->
            when (transaction) {
                is Expense -> callbacks.onOpenExpense(transaction.id)
                is Income -> callbacks.onOpenIncome(transaction.id)
            }
        },
        onDelete = ::onDelete,
        onAddExpense = callbacks.onAddExpense,
        onAddIncome = callbacks.onAddIncome,
    )

@Composable
private fun ActivityContent(
    state: ActivityUiState.Content,
    query: String,
    categories: List<Category>,
    actions: ActivityActions,
    contentPadding: PaddingValues,
) {
    val pill = remember { PillVisibility() }
    val listState = rememberLazyListState()
    pill.BindTo(listState)

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(pill.connection).testTag(TestTags.ACTIVITY_LIST),
            contentPadding = PaddingValues(bottom = Dimens.FloatingBarBottomPadding),
        ) {
            activityHeaderItems(state = state, query = query, categories = categories, actions = actions)
            activityRowItems(state = state, actions = actions)
        }
        AddTransactionPill(
            visible = pill.visible,
            onAddExpense = actions.onAddExpense,
            onAddIncome = actions.onAddIncome,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.Space16),
        )
    }
}
