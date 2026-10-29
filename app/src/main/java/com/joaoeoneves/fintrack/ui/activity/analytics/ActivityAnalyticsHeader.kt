package com.joaoeoneves.fintrack.ui.activity.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.categoryColorFor
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.dashboard.CategoryBreakdownSection
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalDarkTheme

/**
 * The top of Activity: period tabs and selector, the big "Spent" total, fixed vs variable, the envelope bar with
 * its filter chips and the category/envelope breakdown; everything below the total sits behind a "Show details" toggle.
 */
@Suppress("LongParameterList") // the header is fed straight from the screen state
@Composable
fun ActivityAnalyticsHeader(
    period: Period,
    canGoToNextMonth: Boolean,
    analytics: ActivityAnalytics,
    selectedEnvelope: String?,
    actions: ActivityAnalyticsActions,
    modifier: Modifier = Modifier,
    figures: HistoryFigures = HistoryFigures(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        AnalyticsPeriodControls(period, canGoToNextMonth, actions)
        Column(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding)) {
            Text(
                text = stringResource(R.string.activity_spent_total),
                style = MaterialTheme.typography.labelMedium,
                color = FinTrackTheme.colors.textSecondary,
            )
            Text(text = formatAmountCents(analytics.spentCents), style = AmountLarge, maxLines = 1)
            figures.comparison?.let { SpendingComparisonLines(it) }
            figures.monthlyAverageCents?.let { MonthlyAverageLine(it) }
        }
        val hasDetails =
            analytics.split.spendingCents > 0 ||
                analytics.envelopeChips.isNotEmpty() ||
                !analytics.breakdown.isEmpty ||
                figures.hasDetails
        if (hasDetails) {
            SectionAction(
                text =
                    stringResource(
                        if (expanded) R.string.activity_hide_breakdown else R.string.activity_show_breakdown,
                    ),
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
                startPadding = 0.dp,
            )
        }
        if (hasDetails && expanded) {
            AnalyticsDetails(analytics, selectedEnvelope, actions, figures)
        }
    }
}

/** The collapsible detail: fixed vs variable, the envelope bar with its filter chips and the breakdown. */
@Composable
private fun AnalyticsDetails(
    analytics: ActivityAnalytics,
    selectedEnvelope: String?,
    actions: ActivityAnalyticsActions,
    figures: HistoryFigures,
) {
    Column {
        if (analytics.split.spendingCents > 0) {
            SpendingSplitCard(
                split = analytics.split,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
            )
        }
        if (analytics.envelopeChips.isNotEmpty()) {
            EnvelopeFilterSection(analytics.envelopeChips, selectedEnvelope, actions.onEnvelopeSelected)
        }
        if (!analytics.breakdown.isEmpty) {
            BreakdownCard(
                breakdown = analytics.breakdown,
                onCategoryClick = actions.onCategorySelected,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            )
        }
        HistoryDetails(figures)
    }
}

/** The period selector, shared by the Expenses and the Income headers. */
@Composable
internal fun AnalyticsPeriodControls(
    period: Period,
    canGoToNextMonth: Boolean,
    actions: ActivityAnalyticsActions,
) {
    PeriodSelector(
        period = period,
        canGoToNextMonth = canGoToNextMonth,
        actions = actions.period,
        alignment = Alignment.Start,
        modifier = Modifier.padding(horizontal = Dimens.Space8),
    )
}

/** The Evolution bars and the "Since January" row (each only when it has data). */
@Composable
internal fun HistoryDetails(figures: HistoryFigures) {
    figures.evolution?.let {
        EvolutionSection(it, modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8))
    }
    figures.yearToDate?.let {
        YearToDateRow(it, modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8))
    }
}

@Composable
private fun EnvelopeFilterSection(
    chips: List<EnvelopeChip>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    val dark = LocalDarkTheme.current
    val noEnvelope = stringResource(R.string.plan_group_other)
    val spoken = chips.map { "${it.name ?: noEnvelope} ${formatAmountCents(it.spentCents, false)}" }.joinToString("; ")
    val description = stringResource(R.string.cd_envelope_bar, spoken)
    val colors = chips.map { categoryColorFor(it.colorKey, dark) }
    // Tapping a bar segment only highlights it (and names it below); the chips are what filter.
    var highlightedRaw by rememberSaveable { mutableStateOf<Int?>(null) }
    val highlighted = highlightedRaw?.takeIf { it in chips.indices }
    EnvelopeSpendBar(
        segments = chips.mapIndexed { index, chip -> BarSegmentData(chip.spentCents, colors[index]) },
        description = description,
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
        selectedIndex = highlighted,
        onSegmentClick = { index -> highlightedRaw = if (highlighted == index) null else index },
    )
    val shown = highlighted?.let { chips.getOrNull(it) }
    if (shown != null) {
        Text(
            text = "${shown.name ?: noEnvelope} · ${formatAmountCents(shown.spentCents)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors[highlighted ?: 0],
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding).padding(bottom = Dimens.Space8),
        )
    }
    Spacer(Modifier.height(Dimens.Space12))
    EnvelopeFilterRow(chips = chips, selected = selected, onSelected = onSelected, dotColors = colors)
    Spacer(Modifier.height(Dimens.Space16))
}

/** The breakdown with a Category | Envelope switch (only when envelopes exist; otherwise just by category). */
@Composable
private fun BreakdownCard(
    breakdown: ActivityBreakdown,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    var byGroup by rememberSaveable { mutableStateOf(false) }
    val showGroups = byGroup && breakdown.hasEnvelopes
    Column(modifier = modifier) {
        if (breakdown.hasEnvelopes) {
            SegmentedTextTabs(
                options = listOf(false, true),
                selected = showGroups,
                label = { stringResource(if (it) R.string.insights_by_group else R.string.insights_by_category) },
                onSelect = { byGroup = it },
                modifier = Modifier.padding(bottom = Dimens.Space8),
            )
        }
        if (showGroups) {
            EnvelopeBreakdown(summary = breakdown.envelopes)
        } else {
            CategoryBreakdownSection(breakdown = breakdown, onCategoryClick = onCategoryClick)
        }
    }
}
