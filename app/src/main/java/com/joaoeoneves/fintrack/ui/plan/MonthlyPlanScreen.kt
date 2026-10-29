package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.AccountAvatar
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.period.PeriodHeader
import com.joaoeoneves.fintrack.ui.common.period.PeriodHeaderText
import com.joaoeoneves.fintrack.ui.common.period.PeriodNavigation
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.designsystem.CardsSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.plan.annual.AnnualPlanActions
import com.joaoeoneves.fintrack.ui.plan.annual.AnnualPlanUiState
import com.joaoeoneves.fintrack.ui.plan.annual.AnnualPlanView
import com.joaoeoneves.fintrack.ui.plan.annual.AnnualPlanViewModel
import com.joaoeoneves.fintrack.ui.plan.annual.PlanTab
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The Plan tab: the month's tiles, its envelope cards, and the set-up section. */
@Composable
fun PlanScreen(
    onOpenSettings: () -> Unit,
    onOpenConfigure: () -> Unit,
    navigation: PlanNavigation,
    viewModel: MonthlyPlanViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CollectPlanErrors(
        snackbarHostState = snackbarHostState,
        events = listOf(viewModel.errorEvent),
    )
    CollectDeletedIncomeLines(viewModel, snackbarHostState)
    PlanScaffold(
        onOpenSettings = onOpenSettings,
        onOpenConfigure = onOpenConfigure,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        navigation = navigation,
    )
}

@Composable
private fun PlanScaffold(
    onOpenSettings: () -> Unit,
    onOpenConfigure: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: MonthlyPlanViewModel,
    navigation: PlanNavigation,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LargeTitleScaffold(
        title = stringResource(R.string.nav_plan),
        navigationIcon = { AccountAvatar(onClick = onOpenSettings) },
        actions = {
            LabeledIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.plan_configure),
                onClick = onOpenConfigure,
            )
        },
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val annualViewModel: AnnualPlanViewModel = hiltViewModel()
        val tab by annualViewModel.selectedTab.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            SegmentedTextTabs(
                options = PlanTab.entries,
                selected = tab,
                label = { stringResource(it.labelRes) },
                onSelect = annualViewModel::onSelectTab,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            )
            if (tab == PlanTab.YEAR) {
                PlanYearView(annualViewModel, navigation)
            } else {
                PlanMonthView(uiState, viewModel, navigation)
            }
        }
    }
}

/** The sticky year header and the Year view. */
@Composable
private fun PlanYearView(
    annualViewModel: AnnualPlanViewModel,
    navigation: PlanNavigation,
) {
    val annualState by annualViewModel.uiState.collectAsStateWithLifecycle()
    (annualState as? AnnualPlanUiState.Content)?.let { content ->
        PeriodHeader(
            navigation =
                PeriodNavigation(
                    onPrevious = annualViewModel::onPreviousYear,
                    onNext = annualViewModel::onNextYear,
                    canGoPrevious = content.canGoPrevious,
                    canGoNext = content.canGoNext,
                    isYear = true,
                ),
            modifier = Modifier.padding(horizontal = Dimens.Space8),
        ) { PeriodHeaderText(content.year.toString()) }
    }
    AnnualPlanView(
        state = annualState,
        actions =
            AnnualPlanActions(
                onPreviousYear = annualViewModel::onPreviousYear,
                onNextYear = annualViewModel::onNextYear,
                onRetry = annualViewModel::onRetry,
                onOpenEnvelope = navigation.onOpenEnvelope,
            ),
        modifier = Modifier.fillMaxSize(),
    )
}

/** The sticky month header and the Month view. */
@Composable
private fun PlanMonthView(
    uiState: MonthlyPlanUiState,
    viewModel: MonthlyPlanViewModel,
    navigation: PlanNavigation,
) {
    (uiState as? MonthlyPlanUiState.Content)?.let { content ->
        PeriodSelector(
            period = content.period,
            canGoToNextMonth = content.canGoToNextMonth,
            actions = viewModel.periodActions,
            monthOnly = true,
            monthsAhead = PLAN_MONTHS_AHEAD,
            modifier = Modifier.padding(horizontal = Dimens.Space8),
        )
    }
    MonthPlanBody(
        state = uiState,
        viewModel = viewModel,
        navigation = navigation,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MonthPlanBody(
    state: MonthlyPlanUiState,
    viewModel: MonthlyPlanViewModel,
    navigation: PlanNavigation,
    modifier: Modifier,
) {
    StateCrossfade(state) { state ->
        when (state) {
            MonthlyPlanUiState.Loading -> CardsSkeleton(modifier)

            is MonthlyPlanUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = viewModel::onRetry, modifier = modifier)

            is MonthlyPlanUiState.Content ->
                MonthlyPlanContent(
                    state = state,
                    navigation = navigation,
                    modifier = modifier,
                )
        }
    }
}

@Composable
private fun MonthlyPlanContent(
    state: MonthlyPlanUiState.Content,
    navigation: PlanNavigation,
    modifier: Modifier = Modifier,
) {
    val cardModifier = Modifier.fillMaxWidth()
    LazyColumn(
        modifier = modifier,
        contentPadding =
            PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.Space32),
    ) {
        item {
            MonthSummarySection(
                figures = state.figures,
                extraIncomeCents = state.extraIncomeCents,
                actions =
                    MonthSummaryActions(
                        onOpenIncome = { navigation.onOpenIncome(state.period) },
                        onOpenRecurring = navigation.onOpenRecurring,
                        onOpenEnvelopes = navigation.onOpenEnvelopes,
                        onOpenSavings = navigation.onOpenSavings,
                    ),
                modifier = cardModifier.padding(top = Dimens.Space16),
            )
        }
        item {
            EnvelopeCardsSection(
                cards = state.envelopeCards,
                actions =
                    EnvelopeCardActions(
                        onOpenEnvelope = navigation.onOpenEnvelope,
                        onOpenEnvelopes = navigation.onOpenEnvelopes,
                        onOpenCategories = navigation.onOpenCategories,
                    ),
                modifier = cardModifier,
            )
        }
    }
}
