package com.joaoeoneves.fintrack.ui.common.history

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityBreakdown
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.activity.analytics.EvolutionCaptions
import com.joaoeoneves.fintrack.ui.activity.analytics.EvolutionSection
import com.joaoeoneves.fintrack.ui.activity.analytics.GroupBreakdown
import com.joaoeoneves.fintrack.ui.activity.analytics.HistoryFigures
import com.joaoeoneves.fintrack.ui.activity.analytics.IncomeSourceRow
import com.joaoeoneves.fintrack.ui.activity.analytics.MonthlyAverageLine
import com.joaoeoneves.fintrack.ui.activity.analytics.SpendingComparisonLines
import com.joaoeoneves.fintrack.ui.activity.analytics.SpendingSplitCard
import com.joaoeoneves.fintrack.ui.activity.analytics.groupTotalsOf
import com.joaoeoneves.fintrack.ui.activity.analytics.incomeAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.incomeFigures
import com.joaoeoneves.fintrack.ui.activity.analytics.spendingFigures
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.dashboard.CategoryBreakdownSection
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

/** The Gastos side: Spent with its comparison lines, fixed/variable, bars and breakdowns. */
@Suppress("LongParameterList") // the chart's Year-scale wiring is optional and defaulted
@Composable
fun SpendingHistory(
    analytics: ActivityAnalytics,
    history: ActivityHistory?,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    yearScale: Boolean = false,
    onMonthClick: (YearMonth) -> Unit = {},
    aboveAverage: Set<CategoryId> = emptySet(),
) {
    val figures = remember(history) { history?.spendingFigures(null, null) ?: HistoryFigures() }
    val padding = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8)
    Column(modifier = modifier.fillMaxWidth()) {
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
        if (analytics.split.spendingCents > 0) SpendingSplitCard(analytics.split, padding)
        figures.evolution?.let {
            if (yearScale) {
                EvolutionSection(it, padding, EvolutionCaptions(R.string.evolution_caption_spent), onMonthClick)
            } else {
                EvolutionSection(it, padding)
            }
        }
        if (!analytics.breakdown.isEmpty) {
            SpendingBreakdown(analytics.breakdown, onCategoryClick, aboveAverage)
        }
    }
}

/** The two ways to read the spending breakdown, switched when the user has any group. */
private enum class BreakdownMode(
    @param:StringRes val label: Int,
) {
    CATEGORY(R.string.breakdown_mode_category),
    GROUP(R.string.breakdown_mode_group),
}

@Composable
private fun SpendingBreakdown(
    breakdown: ActivityBreakdown,
    onCategoryClick: (Category) -> Unit,
    aboveAverage: Set<CategoryId>,
) {
    val padding = Modifier.padding(horizontal = Dimens.ScreenPadding)
    var mode by rememberSaveable { mutableStateOf(BreakdownMode.CATEGORY) }
    val tabs: @Composable () -> Unit = {
        SegmentedTextTabs(
            options = BreakdownMode.entries,
            selected = mode,
            label = { stringResource(it.label) },
            onSelect = { mode = it },
            modifier = Modifier.padding(vertical = Dimens.Space8),
        )
    }
    if (!breakdown.hasEnvelopes || mode == BreakdownMode.CATEGORY) {
        CategoryBreakdownSection(
            breakdown = breakdown,
            showTotal = false,
            modifier = padding,
            onCategoryClick = onCategoryClick,
            aboveAverage = aboveAverage,
            belowTitle = tabs.takeIf { breakdown.hasEnvelopes },
        )
    } else {
        val noGroup = stringResource(R.string.plan_group_other)
        val groups =
            remember(breakdown, noGroup) {
                groupTotalsOf(breakdown.categoryTotals, breakdown.envelopes.envelopes.map { it.envelope }, noGroup)
            }
        Column(modifier = padding) {
            SectionTitle(title = stringResource(R.string.insights_by_group))
            tabs()
            GroupBreakdown(groups)
        }
    }
}

/** The Income tab's headline: Received with "of X expected", or nothing when nothing was received. */
@Composable
fun IncomeHeadline(
    history: ActivityHistory?,
    modifier: Modifier = Modifier,
) {
    val analytics = remember(history) { history?.incomeAnalytics() }
    if (analytics != null && analytics.receivedCents != 0L) {
        Column(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding)) {
                Text(
                    text = stringResource(R.string.activity_received_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinTrackTheme.colors.textSecondary,
                )
                Text(text = formatAmountCents(analytics.receivedCents), style = AmountLarge, maxLines = 1)
                if (analytics.expectedCents != null) {
                    val expected = formatAmountCents(analytics.expectedCents)
                    Text(
                        text =
                            if (analytics.missingCents != null) {
                                stringResource(
                                    R.string.activity_expected_missing,
                                    expected,
                                    formatAmountCents(analytics.missingCents),
                                )
                            } else {
                                stringResource(R.string.activity_expected, expected)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTrackTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * The Income tab's bars and "By source"; the [IncomeHeadline] is drawn separately, above the lines. On the Year
 * scale ([yearScale]) a bar's caption reads "Received X" (plus "· Expected Y" for the months in [expectedByMonth])
 * and a tap opens that month through [onMonthClick].
 */
@Suppress("LongParameterList") // the chart's Year-scale wiring is optional and defaulted
@Composable
fun IncomeHistory(
    history: ActivityHistory?,
    onSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    yearScale: Boolean = false,
    expectedByMonth: Map<YearMonth, Long> = emptyMap(),
    onMonthClick: (YearMonth) -> Unit = {},
) {
    val analytics = remember(history) { history?.incomeAnalytics() }
    val figures = remember(history) { history?.incomeFigures(null) ?: HistoryFigures() }
    val padding = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8)
    Column(modifier = modifier.fillMaxWidth()) {
        figures.evolution?.let {
            if (yearScale) {
                val captions =
                    EvolutionCaptions(
                        labelRes = R.string.evolution_caption_received,
                        expectedLabelRes = R.string.evolution_caption_received_expected,
                        expectedByMonth = expectedByMonth,
                    )
                EvolutionSection(it, padding, captions, onMonthClick)
            } else {
                EvolutionSection(it, padding)
            }
        }
        analytics?.sources?.takeIf { it.isNotEmpty() }?.let { SourceRows(it, onSourceClick, padding) }
    }
}

@Composable
private fun SourceRows(
    sources: List<IncomeSourceRow>,
    onSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = stringResource(R.string.activity_by_source))
        sources.forEach { row -> SourceRow(row) { onSourceClick(row.key) } }
    }
}

@Composable
private fun SourceRow(
    row: IncomeSourceRow,
    onClick: () -> Unit,
) {
    val colors = FinTrackTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label ?: stringResource(R.string.activity_source_other),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.expectedCents?.let { expected ->
                val color =
                    when {
                        row.pastShortCents > 0L -> colors.negative
                        row.receivedCents > expected -> colors.positive
                        else -> colors.textSecondary
                    }
                Text(
                    text = stringResource(R.string.activity_source_expected, formatAmountCents(expected)),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
        Text(
            text = formatAmountCents(row.receivedCents),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.padding(start = Dimens.Space8),
        )
    }
}
