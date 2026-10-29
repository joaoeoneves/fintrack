package com.joaoeoneves.fintrack.ui.plan.annual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.CardsSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.HeroAmount

/** The Plan tab's Year view: a year navigator, then only the sections that have data. */
@Composable
fun AnnualPlanView(
    state: AnnualPlanUiState,
    actions: AnnualPlanActions,
    modifier: Modifier = Modifier,
) {
    StateCrossfade(state) { state ->
        when (state) {
            AnnualPlanUiState.Loading -> CardsSkeleton(modifier)

            is AnnualPlanUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = actions.onRetry, modifier = modifier)

            is AnnualPlanUiState.Content -> AnnualPlanContent(state, actions, modifier)
        }
    }
}

@Composable
private fun AnnualPlanContent(
    state: AnnualPlanUiState.Content,
    actions: AnnualPlanActions,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    val cardModifier = Modifier.fillMaxWidth()
    LazyColumn(
        modifier = modifier,
        contentPadding =
            PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.Space32),
    ) {
        item { YearNavigator(state, actions) }
        if (!state.hasData) {
            item {
                EmptyState(
                    title = stringResource(R.string.annual_empty_title, state.year),
                    message = stringResource(R.string.annual_empty_message),
                )
            }
            return@LazyColumn
        }
        item { LeftoverHero(year = state.year, leftoverCents = plan.leftoverCents, modifier = cardModifier) }
        if (plan.income.totalCents > 0) item { AnnualIncomeCard(plan.income, state.year, cardModifier) }
        item { AnnualEnvelopesCard(plan.envelopes, cardModifier, plan.savings) }
        if (plan.funding.isNotEmpty()) item { AnnualFundingCard(plan.funding, cardModifier) }
        if (plan.goals.isNotEmpty()) item { AnnualGoalsCard(plan.goals, cardModifier) }
        if (plan.subscriptions.isNotEmpty()) {
            item { AnnualSubscriptionsCard(plan.subscriptions, state.year, cardModifier) }
        }
    }
}

@Composable
private fun YearNavigator(
    state: AnnualPlanUiState.Content,
    actions: AnnualPlanActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_previous_year),
            onClick = actions.onPreviousYear,
            enabled = state.canGoPrevious,
        )
        Text(text = state.year.toString(), style = MaterialTheme.typography.titleMedium)
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_next_year),
            onClick = actions.onNextYear,
            enabled = state.canGoNext,
        )
    }
}

/** The year's leftover (income minus planned spending) as the big number; negative colour when overrun. */
@Composable
private fun LeftoverHero(
    year: Int,
    leftoverCents: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = Dimens.Space8)) {
        Text(
            text = stringResource(R.string.annual_leftover_title),
            style = MaterialTheme.typography.labelLarge,
            color = FinTrackTheme.colors.textSecondary,
        )
        Text(
            text = formatAmountCents(leftoverCents),
            style = HeroAmount,
            color = if (leftoverCents < 0) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.padding(top = Dimens.Space4),
        )
        Text(
            text = stringResource(R.string.annual_leftover_caption, year),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = Dimens.Space4),
        )
    }
}
