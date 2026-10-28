package com.joaoeoneves.fintrack.ui.savings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.GoalStatus
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.ui.common.AccountAvatar
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.formatPercent
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter
import com.joaoeoneves.fintrack.ui.designsystem.AnimatedAmountText
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.ProgressRing
import com.joaoeoneves.fintrack.ui.designsystem.rememberAnimatedCents
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.util.Locale

private val CardRadius = 16.dp
private val RingSize = 64.dp
private const val BASIS_POINTS = 10_000f

/** The Savings tab: the year's saved total and rate, then a ring card per savings plan; "+" adds a plan. */
@Composable
fun SavingsScreen(
    onOpenSettings: () -> Unit,
    onOpenPlan: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LargeTitleScaffold(
        title = stringResource(R.string.nav_savings),
        modifier = modifier,
        navigationIcon = { AccountAvatar(onClick = onOpenSettings) },
        actions = {
            LabeledIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.savings_new_plan),
                onClick = { onOpenPlan(null) },
            )
        },
    ) { innerPadding ->
        if (state.loaded) {
            SavingsContent(state, onOpenPlan, Modifier.fillMaxSize().padding(innerPadding))
        }
    }
}

@Composable
private fun SavingsContent(
    state: SavingsUiState,
    onOpenPlan: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
    ) {
        item { SavingsHeader(state) }
        if (state.cards.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.savings_empty),
                    action = {
                        GhostButton(text = stringResource(R.string.savings_new_plan), onClick = { onOpenPlan(null) })
                    },
                )
            }
        } else {
            items(state.cards, key = { it.id }) { card ->
                SavingsPlanCard(card = card, onClick = { onOpenPlan(card.id) })
            }
        }
    }
}

@Composable
private fun SavingsHeader(
    state: SavingsUiState,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Column(modifier = modifier.fillMaxWidth().padding(bottom = Dimens.Space8)) {
        Text(
            text = stringResource(R.string.savings_saved_in_year, state.year),
            style = MaterialTheme.typography.labelLarge,
            color = FinTrackTheme.colors.textSecondary,
        )
        AnimatedAmountText(
            cents = state.savedCents,
            style = AmountLarge,
            modifier = Modifier.padding(top = Dimens.Space4),
        )
        state.savingsRate?.let { rate ->
            val formatted = remember(rate, locale) { formatPercent(rate, locale) }
            Text(
                text = stringResource(R.string.savings_rate_of_income, formatted),
                style = MaterialTheme.typography.bodyMedium,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Dimens.Space4),
            )
        }
    }
}

@Composable
private fun SavingsPlanCard(
    card: SavingsCardUi,
    onClick: () -> Unit,
) {
    val progress = card.progress
    val locale = currentLocale()
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = FinTrackTheme.colors.surface1,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.Space16),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(progress = progress.fraction, size = RingSize) {
                AnimatedPercentText(fraction = progress.fraction, locale = locale)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                Text(
                    text = progress.goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(progress.status.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (progress.status == GoalStatus.BEHIND) {
                            FinTrackTheme.colors.negative
                        } else {
                            FinTrackTheme.colors.textSecondary
                        },
                )
                targetLine(card)?.let { Text(text = it, style = Amount) }
                scheduleLine(card)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTrackTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** Ring centre label: counts in basis points so the text follows the draw-in; semantics hold the final percent. */
@Composable
private fun AnimatedPercentText(
    fraction: Float,
    locale: Locale,
) {
    val basisPoints by rememberAnimatedCents(Math.round(fraction * BASIS_POINTS).toLong())
    val finalText = remember(fraction, locale) { formatPercent(fraction.toDouble(), locale) }
    Text(
        text = formatPercent(basisPoints / BASIS_POINTS.toDouble(), locale),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(finalText) },
    )
}

@Composable
private fun targetLine(card: SavingsCardUi): String? {
    val progress = card.progress
    val target = progress.targetCents
    if (target <= 0L) return null
    val saved = formatAmountCents(progress.savedCents)
    val goal = formatAmountCents(target)
    val date = progress.goal.targetDate
    val formatter = rememberLocaleAwareDateFormatter()
    return when {
        progress.goal.targetType == GoalTargetType.ANNUAL -> stringResource(R.string.savings_target_year, saved, goal)
        date != null -> stringResource(R.string.savings_target_by, saved, goal, date.format(formatter))
        else -> stringResource(R.string.budget_spent_of_limit, saved, goal)
    }
}

@Composable
private fun scheduleLine(card: SavingsCardUi): String? {
    val monthly = card.monthlyCents
    val day = card.dayOfMonth
    val suggested = card.suggestedCents
    return when {
        monthly != null && day != null ->
            stringResource(
                R.string.savings_schedule,
                card.category?.displayName.orEmpty(),
                formatAmountCents(monthly),
                day,
            )
        suggested != null -> stringResource(R.string.savings_suggestion, formatAmountCents(suggested))
        else -> null
    }
}

private fun GoalStatus.labelRes(): Int =
    when (this) {
        GoalStatus.ON_TRACK -> R.string.goal_status_on_track
        GoalStatus.BEHIND -> R.string.goal_status_behind
        GoalStatus.DONE -> R.string.goal_status_done
        GoalStatus.NO_MONTHLY -> R.string.goal_status_no_monthly
    }
