package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.ui.common.AccountAvatar
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
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
import com.joaoeoneves.fintrack.ui.plan.ChangeSheet
import com.joaoeoneves.fintrack.ui.plan.LocalReceiveFeedback
import com.joaoeoneves.fintrack.ui.plan.rememberReceiveFeedback
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringCard
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringModel
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringSnackbars
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringUiState
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringViewModel
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.Motion
import com.joaoeoneves.fintrack.ui.theme.motionAnimateItem
import com.joaoeoneves.fintrack.ui.theme.motionSpec
import java.time.LocalDate
import java.time.ZoneId

private val SectionSpacing = Dimens.Space32

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
    UpcomingRecurringSnackbars(upcomingViewModel, snackbarHostState)
    val receiveFeedback = rememberReceiveFeedback(snackbarHostState)
    receiveFeedback.ChangeSheet()
    val start = StartChecklistModel(startState, callbacks.onOpenStartStep, startViewModel::onDismiss)
    val actions = dashboardActions(viewModel, upcomingViewModel, upcomingState, start, callbacks)

    val pill = remember { PillVisibility() }
    val listState = rememberLazyListState()
    val content = uiState as? DashboardUiState.Content

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DashboardTopBar(
                state = content,
                actions = actions.period,
                onOpenSettings = callbacks.onOpenSettings,
                scrolled = listState.canScrollBackward,
            )
        },
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (content != null) {
                AddTransactionPill(
                    visible = pill.visible,
                    onAddExpense = callbacks.onAddExpense,
                    onAddIncome = callbacks.onAddIncome,
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        CompositionLocalProvider(LocalReceiveFeedback provides receiveFeedback) {
            DashboardBody(
                uiState = uiState,
                actions = actions,
                callbacks = callbacks,
                scroll = DashboardScroll(pill, listState),
                contentPadding = innerPadding,
            )
        }
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

/**
 * Account avatar on the left, then the period selector (once content is loaded); no title. Solid
 * background, with a hairline along its bottom edge that fades in once content is [scrolled] under it.
 */
@Composable
private fun DashboardTopBar(
    state: DashboardUiState.Content?,
    actions: PeriodActions,
    onOpenSettings: () -> Unit,
    scrolled: Boolean,
) {
    val separator by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = motionSpec(tween(Motion.Short)),
        label = "topBarSeparator",
    )
    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(onClick = onOpenSettings)
            Box(modifier = Modifier.weight(1f)) {
                if (state != null) {
                    PeriodSelector(
                        period = state.period,
                        canGoToNextMonth = state.canGoToNextMonth,
                        actions = actions,
                        alignment = Alignment.Start,
                        monthOnly = true,
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(Dimens.HairlineThickness)
                    .graphicsLayer { alpha = separator }
                    .background(FinTrackTheme.colors.hairline),
        )
    }
}

/** The list's scroll state plus the add-pill visibility driven by it. */
private data class DashboardScroll(
    val pill: PillVisibility,
    val listState: LazyListState,
)

private fun dashboardActions(
    viewModel: DashboardViewModel,
    upcomingViewModel: UpcomingRecurringViewModel,
    upcomingState: UpcomingRecurringUiState,
    start: StartChecklistModel,
    callbacks: DashboardCallbacks,
) = DashboardActions(
    period =
        PeriodActions(
            onPrevious = viewModel::onPreviousMonth,
            onNext = viewModel::onNextMonth,
            onSelect = viewModel::onPeriodSelected,
        ),
    upcoming =
        UpcomingRecurringModel(
            state = upcomingState,
            onConfirm = upcomingViewModel::onConfirm,
            onSkip = upcomingViewModel::onSkip,
            onReceiveIncome = upcomingViewModel::onReceiveIncome,
            onManage = callbacks.onOpenRecurring,
        ),
    start = start,
    onRetry = viewModel::onRetry,
)

/** Everything Home's content can do besides navigation, bundled for detekt's LongParameterList. */
private data class DashboardActions(
    val period: PeriodActions,
    val upcoming: UpcomingRecurringModel,
    val start: StartChecklistModel,
    val onRetry: () -> Unit,
)

/** The "Get started" card's state and callbacks. */
private data class StartChecklistModel(
    val state: StartChecklistUiState,
    val onOpenStep: (StartStep) -> Unit,
    val onDismiss: () -> Unit,
)

/** What the balance chart needs, plus the scrub position shared with the hero. */
private data class ChartModel(
    val series: List<Long>,
    val startDate: LocalDate,
    val scrubbedIndex: Int?,
    val onScrub: (Int?) -> Unit,
)

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    actions: DashboardActions,
    callbacks: DashboardCallbacks,
    scroll: DashboardScroll,
    contentPadding: PaddingValues,
) {
    val zone = remember { ZoneId.systemDefault() }
    val series =
        remember(state.expenses, state.income, state.range) {
            dailyNetSeries(state.expenses, state.income, state.range, zone, LocalDate.now(zone))
        }
    var scrubbedIndex by remember(series) { mutableStateOf<Int?>(null) }
    val chart =
        ChartModel(
            series = series,
            startDate =
                state.range.startInclusive
                    .atZone(zone)
                    .toLocalDate(),
            scrubbedIndex = scrubbedIndex,
            onScrub = { scrubbedIndex = it },
        )

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
            PaddingValues(bottom = Dimens.AddPillClearance + contentPadding.calculateBottomPadding()),
    ) {
        balanceItems(state = state, chart = chart, start = actions.start)
        planItems(state = state, actions = actions, callbacks = callbacks)
        activityItems(state = state, callbacks = callbacks)
    }
}

/** A list item padded to the screen margin, with the standard reorder animation. */
@Composable
private fun LazyItemScope.ScreenItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = motionAnimateItem(Modifier.padding(horizontal = Dimens.ScreenPadding).then(modifier)),
    ) { content() }
}

private fun LazyListScope.balanceItems(
    state: DashboardUiState.Content,
    chart: ChartModel,
    start: StartChecklistModel,
) {
    item(key = "hero") {
        ScreenItem(Modifier.padding(top = Dimens.Space8)) {
            DashboardHero(
                netCents = state.netCents,
                savingsRate = state.savingsRate,
                scrubbedCents = chart.scrubbedIndex?.let { chart.series.getOrNull(it) },
            )
        }
    }
    if (start.state is StartChecklistUiState.Visible) {
        item(key = "start_checklist") {
            ScreenItem(Modifier.padding(top = Dimens.Space16)) {
                StartChecklistCard(state = start.state, onOpenStep = start.onOpenStep, onDismiss = start.onDismiss)
            }
        }
    }
    if (state.expenses.isNotEmpty() || state.income.isNotEmpty()) {
        item(key = "chart") {
            BalanceLineChart(
                series = chart.series,
                startDate = chart.startDate,
                onScrub = chart.onScrub,
                modifier = motionAnimateItem(Modifier.padding(top = Dimens.Space8)),
            )
        }
    }
    item(key = "stats") {
        ScreenItem(Modifier.padding(top = Dimens.Space24)) {
            DashboardStatStrip(
                incomeCents = state.incomeCents,
                spendingCents = state.spendingCents,
                savedCents = state.savedCents,
                fixedSpendingCents = state.fixedSpendingCents,
            )
        }
    }
}

private fun LazyListScope.planItems(
    state: DashboardUiState.Content,
    actions: DashboardActions,
    callbacks: DashboardCallbacks,
) {
    item(key = "safe_to_spend") {
        ScreenItem(Modifier.padding(top = SectionSpacing)) {
            SafeToSpendSection(state = state, onOpenMonthlyPlan = callbacks.onOpenPlan)
        }
    }
    item(key = "upcoming") {
        ScreenItem {
            UpcomingRecurringCard(model = actions.upcoming, modifier = Modifier.padding(top = SectionSpacing))
        }
    }
}

private fun LazyListScope.activityItems(
    state: DashboardUiState.Content,
    callbacks: DashboardCallbacks,
) {
    item(key = "recent_activity") {
        ScreenItem(Modifier.padding(top = SectionSpacing)) {
            RecentActivitySection(items = state.recent, period = state.period, callbacks = callbacks)
        }
    }
}
