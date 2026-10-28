package com.joaoeoneves.fintrack.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.SettingsIconButton
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.dashboard.CategoryBreakdownSection
import com.joaoeoneves.fintrack.ui.dashboard.PeriodTab
import com.joaoeoneves.fintrack.ui.dashboard.toPeriod
import com.joaoeoneves.fintrack.ui.dashboard.toTab
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The Insights tab: the period picker (month, rolling ranges, custom) over the category breakdown. */
@Composable
fun InsightsScreen(
    onOpenSettings: () -> Unit,
    onOpenActivity: (Period, CategoryId) -> Unit,
    onAddExpense: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OnResumeEffect(onResume = viewModel::onRetry)

    LargeTitleScaffold(
        title = stringResource(R.string.insights_title),
        actions = { SettingsIconButton(onClick = onOpenSettings) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val state = uiState) {
            InsightsUiState.Loading ->
                Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is InsightsUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = viewModel::onRetry, modifier = contentModifier)

            is InsightsUiState.Content ->
                InsightsContent(
                    state = state,
                    actions =
                        PeriodActions(
                            onPrevious = viewModel::onPreviousMonth,
                            onNext = viewModel::onNextMonth,
                            onSelect = viewModel::onPeriodSelected,
                        ),
                    onCategoryClick = { onOpenActivity(state.period, it.id) },
                    onAddExpense = onAddExpense,
                    modifier = contentModifier,
                )
        }
    }
}

/** The breakdown with a Category | Group switch (only when groups exist; otherwise just by category). */
@Composable
private fun BreakdownCard(
    breakdown: InsightsBreakdown,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    var byGroup by rememberSaveable { mutableStateOf(false) }
    val showGroups = byGroup && breakdown.hasGroups
    Column(modifier = modifier) {
        if (breakdown.hasGroups) {
            SegmentedTextTabs(
                options = listOf(false, true),
                selected = showGroups,
                label = { stringResource(if (it) R.string.insights_by_group else R.string.insights_by_category) },
                onSelect = { byGroup = it },
                modifier = Modifier.padding(bottom = Dimens.Space8),
            )
        }
        if (showGroups) {
            GroupBreakdown(groups = breakdown.groups)
        } else {
            CategoryBreakdownSection(breakdown = breakdown, onCategoryClick = onCategoryClick)
        }
    }
}

@Composable
private fun InsightsContent(
    state: InsightsUiState.Content,
    actions: PeriodActions,
    onCategoryClick: (Category) -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
    ) {
        item(key = "tabs") {
            SegmentedTextTabs(
                options = PeriodTab.entries,
                selected = state.period.toTab(),
                label = { stringResource(it.labelRes) },
                onSelect = { actions.onSelect(it.toPeriod()) },
                tabContentDescription = { stringResource(it.descriptionRes) },
            )
        }
        item(key = "period") {
            PeriodSelector(
                period = state.period,
                canGoToNextMonth = state.canGoToNextMonth,
                actions = actions,
            )
        }
        item(key = "breakdown") {
            if (state.isEmpty) {
                EmptyState(
                    title = stringResource(R.string.insights_empty),
                    modifier = Modifier.padding(top = Dimens.Space32),
                    action = {
                        PrimaryPillButton(text = stringResource(R.string.action_add_expense), onClick = onAddExpense)
                    },
                )
            } else {
                BreakdownCard(
                    breakdown = state.breakdown,
                    onCategoryClick = onCategoryClick,
                    modifier = Modifier.padding(top = Dimens.Space16),
                )
            }
        }
        if (state.split.spendingCents > 0) {
            item(key = "split") {
                SpendingSplitCard(split = state.split, modifier = Modifier.padding(top = Dimens.Space24))
            }
        }
    }
}
