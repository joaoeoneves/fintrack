package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringCard
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringModel
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringSnackbars
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringUiState
import com.joaoeoneves.fintrack.ui.recurring.UpcomingRecurringViewModel
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate
import java.time.ZoneId

private val SectionSpacing = Dimens.Space32

@Composable
fun DashboardScreen(
    callbacks: DashboardCallbacks,
    viewModel: DashboardViewModel = hiltViewModel(),
    upcomingViewModel: UpcomingRecurringViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val upcomingState by upcomingViewModel.uiState.collectAsStateWithLifecycle()

    OnResumeEffect(
        onResume = {
            viewModel.onRetry()
            upcomingViewModel.onRetry()
        },
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.budgetErrorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    UpcomingRecurringSnackbars(upcomingViewModel, snackbarHostState)
    val actions = dashboardActions(viewModel, upcomingViewModel, upcomingState, callbacks)

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
    when (uiState) {
        is DashboardUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
            }
        }

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
 * Period selector on the left (once content is loaded), settings on the right; no title. Solid
 * background, with a hairline along its bottom edge that fades in once content is [scrolled] under it.
 */
@Composable
private fun DashboardTopBar(
    state: DashboardUiState.Content?,
    actions: PeriodActions,
    onOpenSettings: () -> Unit,
    scrolled: Boolean,
) {
    val separator by animateFloatAsState(if (scrolled) 1f else 0f, label = "topBarSeparator")
    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (state != null) {
                    PeriodSelector(
                        period = state.period,
                        canGoToNextMonth = state.canGoToNextMonth,
                        actions = actions,
                        alignment = Alignment.Start,
                    )
                }
            }
            LabeledIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                onClick = onOpenSettings,
                modifier = Modifier.testTag(TestTags.DASHBOARD_SETTINGS),
            )
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

/** Bundles the budget write callbacks to keep [DashboardContent] under detekt's LongParameterList. */
private data class BudgetActions(
    val onSetBudget: (Category, Long) -> Unit,
    val onClearBudget: (Category) -> Unit,
)

private fun dashboardActions(
    viewModel: DashboardViewModel,
    upcomingViewModel: UpcomingRecurringViewModel,
    upcomingState: UpcomingRecurringUiState,
    callbacks: DashboardCallbacks,
) = DashboardActions(
    period =
        PeriodActions(
            onPrevious = viewModel::onPreviousMonth,
            onNext = viewModel::onNextMonth,
            onSelect = viewModel::onPeriodSelected,
        ),
    budget = BudgetActions(onSetBudget = viewModel::onSetBudget, onClearBudget = viewModel::onClearBudget),
    upcoming =
        UpcomingRecurringModel(
            state = upcomingState,
            onConfirm = upcomingViewModel::onConfirm,
            onSkip = upcomingViewModel::onSkip,
            onManage = callbacks.onOpenRecurring,
        ),
    onRetry = viewModel::onRetry,
)

/** Everything the dashboard content can do besides navigation, bundled for detekt's LongParameterList. */
private data class DashboardActions(
    val period: PeriodActions,
    val budget: BudgetActions,
    val upcoming: UpcomingRecurringModel,
    val onRetry: () -> Unit,
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
    LaunchedEffect(listState, pill) {
        pill.canScrollForward = { listState.canScrollForward }
        // Keyed on "at end while hidden" so it re-fires if a late scroll delta hides the pill again.
        snapshotFlow { !listState.canScrollForward && !pill.visible }.collect { stuck -> if (stuck) pill.show() }
    }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .nestedScroll(pill.connection)
                .testTag(TestTags.DASHBOARD_LIST),
        contentPadding =
            PaddingValues(bottom = Dimens.FloatingBarBottomPadding + contentPadding.calculateBottomPadding()),
    ) {
        balanceItems(state = state, chart = chart, onSelectPeriod = actions.period.onSelect)
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
        modifier =
            Modifier
                .animateItem()
                .padding(horizontal = Dimens.ScreenPadding)
                .then(modifier),
    ) { content() }
}

private fun LazyListScope.balanceItems(
    state: DashboardUiState.Content,
    chart: ChartModel,
    onSelectPeriod: (Period) -> Unit,
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
    if (state.expenses.isNotEmpty() || state.income.isNotEmpty()) {
        item(key = "chart") {
            BalanceLineChart(
                series = chart.series,
                startDate = chart.startDate,
                onScrub = chart.onScrub,
                modifier = Modifier.animateItem().padding(top = Dimens.Space8),
            )
        }
    }
    item(key = "tabs") {
        ScreenItem(Modifier.padding(top = Dimens.Space8)) {
            SegmentedTextTabs(
                options = PeriodTab.entries,
                selected = state.period.toTab(),
                label = { stringResource(it.labelRes) },
                onSelect = { onSelectPeriod(it.toPeriod()) },
                tabContentDescription = { stringResource(it.descriptionRes) },
            )
        }
    }
    item(key = "stats") {
        ScreenItem(Modifier.padding(top = Dimens.Space24)) {
            DashboardStatStrip(
                incomeCents = state.incomeCents,
                spendingCents = state.spendingCents,
                savedCents = state.savedCents,
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
            SafeToSpendSection(state = state, onOpenMonthlyPlan = callbacks.onOpenMonthlyPlan)
        }
    }
    item(key = "upcoming") {
        ScreenItem {
            UpcomingRecurringCard(model = actions.upcoming, modifier = Modifier.padding(top = SectionSpacing))
        }
    }
    item(key = "breakdown") {
        ScreenItem(Modifier.padding(top = SectionSpacing)) { CategoryBreakdownSection(state = state) }
    }
    item(key = "budgets") {
        ScreenItem(Modifier.padding(top = SectionSpacing)) {
            BudgetSection(
                budgets = state.budgets,
                title = budgetSectionTitle(state),
                onSetBudget = actions.budget.onSetBudget,
                onClearBudget = actions.budget.onClearBudget,
            )
        }
    }
}

private fun LazyListScope.activityItems(
    state: DashboardUiState.Content,
    callbacks: DashboardCallbacks,
) {
    item(key = "recent_expenses") {
        ScreenItem(Modifier.padding(top = SectionSpacing)) {
            RecentExpensesSection(expenses = state.recentExpenses, period = state.period, callbacks = callbacks)
        }
    }
    item(key = "recent_income") {
        ScreenItem(Modifier.padding(top = SectionSpacing)) {
            RecentIncomeSection(income = state.recentIncome, period = state.period, callbacks = callbacks)
        }
    }
}

@Composable
private fun budgetSectionTitle(state: DashboardUiState.Content): String =
    if (state.budgetMonthIsCurrent) {
        stringResource(R.string.dashboard_budgets_title)
    } else {
        stringResource(R.string.dashboard_budgets_title_month, formatMonthLabel(state.budgetMonth, currentLocale()))
    }
