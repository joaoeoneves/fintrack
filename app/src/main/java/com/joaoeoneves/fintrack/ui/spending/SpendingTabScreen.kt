package com.joaoeoneves.fintrack.ui.spending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.AccountAvatar
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.history.SpendingHistory
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.ListSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * The Spending tab: what was spent in a month or a year (comparisons, fixed/variable, bars, by category and group,
 * since January), then the in-place set-up rows for categories, groups and recurring items. Savings are not spending
 * and never show here.
 */
@Composable
fun SpendingTabScreen(
    onOpenSettings: () -> Unit,
    navigation: SpendingTabNavigation,
    modifier: Modifier = Modifier,
    viewModel: SpendingTabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LargeTitleScaffold(
        title = stringResource(R.string.nav_spending),
        navigationIcon = { AccountAvatar(onClick = onOpenSettings) },
        modifier = modifier,
    ) { innerPadding ->
        StateCrossfade(state) { current ->
            when (current) {
                SpendingTabUiState.Loading -> ListSkeleton(Modifier.padding(innerPadding))

                is SpendingTabUiState.Error ->
                    ErrorState(
                        message = current.message.asString(),
                        onRetry = viewModel::onRetry,
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    )

                is SpendingTabUiState.Content ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(bottom = Dimens.Space24),
                    ) {
                        PeriodSelector(
                            period = current.period,
                            canGoToNextMonth = current.canGoToNextMonth,
                            actions = viewModel.periodActions,
                            monthOnly = true,
                            showScaleSwitch = true,
                            modifier = Modifier.padding(horizontal = Dimens.Space8),
                        )
                        SpendingHistory(
                            analytics = current.analytics,
                            history = current.history,
                            onCategoryClick = { navigation.onOpenCategory(it.id.value, current.period) },
                        )
                        SpendingConfigure(current.groups, navigation)
                    }
            }
        }
    }
}

@Composable
private fun SpendingConfigure(
    groups: List<SpendingGroup>,
    navigation: SpendingTabNavigation,
) {
    SettingsGroup(
        label = stringResource(R.string.tab_configure),
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Dimens.Space16),
    ) {
        SettingsRow(
            title = stringResource(R.string.plan_configure_categories),
            onClick = navigation.onOpenCategories,
            showChevron = true,
        )
        groups.forEach { group ->
            Hairline()
            SettingsRow(title = group.name, onClick = { navigation.onOpenGroup(group.id) }, showChevron = true)
        }
        Hairline()
        SettingsRow(
            title = stringResource(R.string.spending_new_group),
            onClick = { navigation.onOpenGroup(null) },
            showChevron = true,
        )
        Hairline()
        SettingsRow(
            title = stringResource(R.string.plan_configure_bills),
            onClick = navigation.onOpenRecurring,
            showChevron = true,
        )
    }
}
