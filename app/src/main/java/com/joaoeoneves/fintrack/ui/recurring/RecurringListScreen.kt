package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.ListSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.SectionLabel
import com.joaoeoneves.fintrack.ui.theme.motionAnimateItem

@Composable
fun RecurringListScreen(
    onBack: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    onOpenIncome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    OnResumeEffect(onResume = viewModel::onRetry)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    LaunchedEffect(Unit) {
        viewModel.deletedEvent.collect { item ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                snackbarHostState.showActionSnackbar(
                    resources.getString(R.string.recurring_deleted),
                    resources.getString(R.string.action_undo),
                )
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete(item)
        }
    }

    RecurringListContent(
        state = uiState,
        actions =
            RecurringListActions(
                onOpenEditor = onOpenEditor,
                onDeleteRequested = viewModel::onDeleteRequested,
                onRetry = viewModel::onRetry,
                onOpenIncome = onOpenIncome,
            ),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless recurring list screen, hostable with plain state. */
@Composable
internal fun RecurringListContent(
    state: RecurringListUiState,
    actions: RecurringListActions,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(
        title = stringResource(R.string.recurring_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        bottomBar = {
            PrimaryPillButton(
                text = stringResource(R.string.recurring_add),
                onClick = { actions.onOpenEditor(null) },
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space12)
                        .testTag(TestTags.RECURRING_ADD),
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        StateCrossfade(state) { state ->
            when (state) {
                RecurringListUiState.Loading -> ListSkeleton(contentModifier)

                is RecurringListUiState.Error ->
                    ErrorState(
                        message = state.message.asString(),
                        onRetry = actions.onRetry,
                        modifier = contentModifier,
                    )

                is RecurringListUiState.Content -> RecurringItems(state, actions, contentModifier)
            }
        }
    }
}

@Composable
private fun RecurringItems(
    state: RecurringListUiState.Content,
    actions: RecurringListActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding)) {
        item(key = "totals") { RecurringTotals(state) }
        if (state.active.isEmpty() && state.inactive.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.recurring_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTrackTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = Dimens.Space16),
                )
            }
        }
        if (state.active.isNotEmpty()) {
            item(key = "active-header") { SectionHeader(R.string.recurring_section_active) }
            items(state.active, key = { it.item.id }) { RecurringRowItem(it, actions, motionAnimateItem()) }
        }
        if (state.inactive.isNotEmpty()) {
            item(key = "inactive-header") { SectionHeader(R.string.recurring_section_inactive) }
            items(state.inactive, key = { it.item.id }) { RecurringRowItem(it, actions, motionAnimateItem()) }
        }
        item(key = "income-hint") {
            Text(
                text = stringResource(R.string.recurring_income_hint),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = actions.onOpenIncome)
                        .padding(vertical = Dimens.Space16),
            )
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = SectionLabel,
        color = FinTrackTheme.colors.textSecondary,
        modifier =
            Modifier
                .padding(top = Dimens.Space24, bottom = Dimens.Space8)
                .semantics { heading() },
    )
}
