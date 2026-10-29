package com.joaoeoneves.fintrack.ui.activity.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.common.sliceColorFor
import com.joaoeoneves.fintrack.ui.dashboard.CategoryBreakdownSection
import com.joaoeoneves.fintrack.ui.dashboard.PeriodTab
import com.joaoeoneves.fintrack.ui.dashboard.toPeriod
import com.joaoeoneves.fintrack.ui.dashboard.toTab
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
    showSpent: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        SegmentedTextTabs(
            options = PeriodTab.entries,
            selected = period.toTab(),
            label = { stringResource(it.labelRes) },
            onSelect = { actions.period.onSelect(it.toPeriod()) },
            tabContentDescription = { stringResource(it.descriptionRes) },
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
        )
        PeriodSelector(
            period = period,
            canGoToNextMonth = canGoToNextMonth,
            actions = actions.period,
            alignment = Alignment.Start,
            modifier = Modifier.padding(horizontal = Dimens.Space8),
        )
        if (showSpent) {
            Column(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding)) {
                Text(
                    text = stringResource(R.string.activity_spent_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinTrackTheme.colors.textSecondary,
                )
                Text(text = formatAmountCents(analytics.spentCents), style = AmountLarge, maxLines = 1)
            }
        }
        val hasDetails =
            (showSpent && analytics.split.spendingCents > 0) ||
                analytics.envelopeChips.isNotEmpty() ||
                !analytics.breakdown.isEmpty
        if (hasDetails) {
            SectionAction(
                text =
                    stringResource(
                        if (expanded) R.string.activity_hide_breakdown else R.string.activity_show_breakdown,
                    ),
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            )
        }
        if (hasDetails && expanded) {
            AnalyticsDetails(analytics, selectedEnvelope, actions, showSpent)
        }
    }
}

/** The collapsible detail: fixed vs variable, the envelope bar with its filter chips and the breakdown. */
@Composable
private fun AnalyticsDetails(
    analytics: ActivityAnalytics,
    selectedEnvelope: String?,
    actions: ActivityAnalyticsActions,
    showSpent: Boolean,
) {
    Column {
        if (showSpent && analytics.split.spendingCents > 0) {
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
    EnvelopeSpendBar(
        segments = chips.mapIndexed { index, chip -> BarSegmentData(chip.spentCents, sliceColorFor(index, dark)) },
        description = description,
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
    )
    EnvelopeFilterRow(chips = chips, selected = selected, onSelected = onSelected)
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
