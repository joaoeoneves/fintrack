package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.TabScaffold
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.designsystem.AddTransactionPill
import com.joaoeoneves.fintrack.ui.designsystem.BindTo
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.HeroListSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.PillVisibility
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringCard
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringModel
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringUiState
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringViewModel
import com.joaoeoneves.fintrack.ui.theme.Dimens

private val SectionSpacing = Dimens.SectionGap

@Composable
fun DashboardScreen(
    callbacks: DashboardCallbacks,
    viewModel: DashboardViewModel = hiltViewModel(),
    upcomingViewModel: UpcomingRecurringViewModel = hiltViewModel(),
    startViewModel: StartChecklistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val upcomingState by upcomingViewModel.uiState.collectAsStateWithLifecycle()
    val startState by startViewModel.uiState.collectAsStateWithLifecycle()

    OnResumeEffect(
        onResume = {
            viewModel.onRetry()
            upcomingViewModel.onRetry()
        },
    )

    val snackbarHostState = remember { SnackbarHostState() }
    StartDismissedSnackbar(startViewModel, snackbarHostState)
    val start = StartChecklistModel(startState, callbacks.onOpenStartStep, startViewModel::onDismiss)
    val blocks = rememberHomeBlocks(start, callbacks, snackbarHostState)
    val actions = dashboardActions(viewModel, upcomingState, blocks, callbacks)

    val pill = remember { PillVisibility() }
    val listState = rememberLazyListState()
    val content = uiState as? DashboardUiState.Content
    LaunchedEffect(content?.period) { pill.show() }

    TabScaffold(
        title = stringResource(R.string.nav_home),
        onOpenSettings = callbacks.onOpenSettings,
        scrolled = listState.canScrollBackward,
        headerExtra = {
            if (content != null) {
                PeriodSelector(
                    period = content.period,
                    canGoToNextMonth = content.canGoToNextMonth,
                    actions = actions.period,
                    monthOnly = true,
                    showScaleSwitch = true,
                    modifier = Modifier.padding(horizontal = Dimens.Space8),
                )
            }
        },
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (content != null) {
                AddTransactionPill(
                    visible = pill.visible,
                    onAddExpense = { callbacks.onAddExpense(content.period) },
                    onAddIncome = { callbacks.onAddIncome(content.period) },
                )
            }
        },
    ) { innerPadding ->
        DashboardBody(
            uiState = uiState,
            actions = actions,
            callbacks = callbacks,
            scroll = DashboardScroll(pill, listState),
            contentPadding = innerPadding,
        )
    }
}

@Composable
private fun DashboardBody(
    uiState: DashboardUiState,
    actions: DashboardActions,
    callbacks: DashboardCallbacks,
    scroll: DashboardScroll,
    contentPadding: PaddingValues,
) {
    StateCrossfade(uiState) { current ->
        DashboardBodyFor(current, actions, callbacks, scroll, contentPadding)
    }
}

@Composable
private fun DashboardBodyFor(
    uiState: DashboardUiState,
    actions: DashboardActions,
    callbacks: DashboardCallbacks,
    scroll: DashboardScroll,
    contentPadding: PaddingValues,
) {
    when (uiState) {
        is DashboardUiState.Loading -> HeroListSkeleton(Modifier.padding(contentPadding))

        is DashboardUiState.Content -> {
            DashboardContent(
                state = uiState,
                actions = actions,
                callbacks = callbacks,
                scroll = scroll,
                contentPadding = contentPadding,
            )
        }

        is DashboardUiState.Error -> {
            ErrorState(
                message = uiState.message.asString(),
                onRetry = actions.onRetry,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
        }
    }
}

/** The list's scroll state plus the add-pill visibility driven by it. */
private data class DashboardScroll(
    val pill: PillVisibility,
    val listState: LazyListState,
)

private fun dashboardActions(
    viewModel: DashboardViewModel,
    upcomingState: UpcomingRecurringUiState,
    blocks: HomeBlocks,
    callbacks: DashboardCallbacks,
) = DashboardActions(
    period =
        PeriodActions(
            onPrevious = viewModel::onPreviousMonth,
            onNext = viewModel::onNextMonth,
            onSelect = viewModel::onPeriodSelected,
            onSelectScale = viewModel::onSelectScale,
        ),
    upcoming =
        UpcomingRecurringModel(
            state = upcomingState,
            onManage = callbacks.onOpenRecurring,
        ),
    blocks = blocks,
    onRetry = viewModel::onRetry,
)

/** Everything Home's content can do besides navigation, bundled for detekt's LongParameterList. */
private data class DashboardActions(
    val period: PeriodActions,
    val upcoming: UpcomingRecurringModel,
    val blocks: HomeBlocks,
    val onRetry: () -> Unit,
)

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    actions: DashboardActions,
    callbacks: DashboardCallbacks,
    scroll: DashboardScroll,
    contentPadding: PaddingValues,
) {
    val (pill, listState) = scroll
    pill.BindTo(listState)

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .nestedScroll(pill.connection)
                .testTag(TestTags.DASHBOARD_LIST),
        contentPadding =
            PaddingValues(bottom = Dimens.AddPillClearance + Dimens.Space16 + contentPadding.calculateBottomPadding()),
    ) {
        startItems(start = actions.blocks.start)
        reviewItems(review = actions.blocks.review, visible = state.showsCurrentMonthBlocks)
        summaryItems(state = state, callbacks = callbacks)
        quickAddItems(blocks = actions.blocks, visible = state.showsCurrentMonthBlocks, callbacks = callbacks)
        item(key = "upcoming") {
            ScreenItem {
                UpcomingRecurringCard(model = actions.upcoming, modifier = Modifier.padding(top = SectionSpacing))
            }
        }
    }
}

private fun LazyListScope.summaryItems(
    state: DashboardUiState.Content,
    callbacks: DashboardCallbacks,
) {
    item(key = "income") {
        ScreenItem(Modifier.padding(top = Dimens.Space8)) {
            IncomeSummaryCard(state = state, onClick = { callbacks.onOpenIncomeTab(state.period) })
        }
    }
    item(key = "spending") {
        ScreenItem(Modifier.padding(top = Dimens.Space16)) {
            SpendingSummaryCard(state = state, onClick = { callbacks.onOpenSpendingTab(state.period) })
        }
    }
    state.invested?.let { invested ->
        item(key = "invested") {
            ScreenItem(Modifier.padding(top = Dimens.Space16)) {
                InvestedRow(
                    figure = invested,
                    onClick = { callbacks.onOpenInvestments(state.period) },
                )
            }
        }
    }
}
