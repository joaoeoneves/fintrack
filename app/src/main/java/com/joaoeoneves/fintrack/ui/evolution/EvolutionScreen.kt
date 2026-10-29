package com.joaoeoneves.fintrack.ui.evolution

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.ListSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * Evolution, pushed from Activity's summary line (and Plan > Income's "View history"): the figures, bars and
 * breakdowns of one period, for either expenses or income. It has its own period selector.
 */
@Composable
fun EvolutionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCategoryClick: (String) -> Unit = {},
    onSourceClick: (String) -> Unit = {},
    viewModel: EvolutionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LargeTitleScaffold(
        title = stringResource(R.string.activity_evolution),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        StateCrossfade(state) { current ->
            when (current) {
                EvolutionUiState.Loading -> ListSkeleton(Modifier.padding(innerPadding))

                is EvolutionUiState.Error ->
                    ErrorState(
                        message = current.message.asString(),
                        onRetry = viewModel::onRetry,
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    )

                is EvolutionUiState.Content ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(bottom = Dimens.Space24),
                    ) {
                        EvolutionHeader(current, viewModel)
                        when (current.type) {
                            EvolutionType.EXPENSE ->
                                ExpensesEvolution(
                                    analytics = current.analytics,
                                    history = current.history,
                                    onCategoryClick = { onCategoryClick(it.id.value) },
                                )
                            EvolutionType.INCOME ->
                                IncomeEvolution(history = current.history, onSourceClick = onSourceClick)
                        }
                    }
            }
        }
    }
}

@Composable
private fun EvolutionHeader(
    state: EvolutionUiState.Content,
    viewModel: EvolutionViewModel,
) {
    PeriodSelector(
        period = state.period,
        canGoToNextMonth = state.canGoToNextMonth,
        actions = viewModel.periodActions,
        alignment = Alignment.Start,
        modifier = Modifier.padding(horizontal = Dimens.Space8),
    )
    SegmentedTextTabs(
        options = EvolutionType.entries,
        selected = state.type,
        label = {
            stringResource(
                if (it == EvolutionType.EXPENSE) R.string.dashboard_expenses_label else R.string.dashboard_income_label,
            )
        },
        onSelect = viewModel::onTypeSelected,
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
    )
}
