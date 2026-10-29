package com.joaoeoneves.fintrack.ui.common.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityHistory
import com.joaoeoneves.fintrack.ui.activity.analytics.EnvelopeBreakdown
import com.joaoeoneves.fintrack.ui.activity.analytics.EvolutionSection
import com.joaoeoneves.fintrack.ui.activity.analytics.HistoryFigures
import com.joaoeoneves.fintrack.ui.activity.analytics.IncomeSourceRow
import com.joaoeoneves.fintrack.ui.activity.analytics.MonthlyAverageLine
import com.joaoeoneves.fintrack.ui.activity.analytics.SpendingComparisonLines
import com.joaoeoneves.fintrack.ui.activity.analytics.SpendingSplitCard
import com.joaoeoneves.fintrack.ui.activity.analytics.YearToDateRow
import com.joaoeoneves.fintrack.ui.activity.analytics.incomeAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.incomeFigures
import com.joaoeoneves.fintrack.ui.activity.analytics.spendingFigures
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.dashboard.CategoryBreakdownSection
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The Gastos side: Spent with its comparison lines, fixed/variable, bars, breakdowns, year to date. */
@Composable
fun SpendingHistory(
    analytics: ActivityAnalytics,
    history: ActivityHistory?,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
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
        figures.evolution?.let { EvolutionSection(it, padding) }
        if (!analytics.breakdown.isEmpty) {
            SectionTitle(
                title = stringResource(R.string.insights_by_category),
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            )
            CategoryBreakdownSection(
                breakdown = analytics.breakdown,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
                onCategoryClick = onCategoryClick,
            )
            if (analytics.breakdown.hasEnvelopes) {
                SectionTitle(
                    title = stringResource(R.string.insights_by_group),
                    modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
                )
                EnvelopeBreakdown(
                    summary = analytics.breakdown.envelopes,
                    modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
                )
            }
        }
        figures.yearToDate?.let { YearToDateRow(it, padding, figures.leftover) }
    }
}

/** The Rendimentos side (both scales): Received with Expected/Difference, the bars, "By source" and year to date. */
@Composable
fun IncomeHistory(
    history: ActivityHistory?,
    onSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = remember(history) { history?.incomeAnalytics() }
    val figures = remember(history) { history?.incomeFigures(null) ?: HistoryFigures() }
    val padding = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8)
    Column(modifier = modifier.fillMaxWidth()) {
        if (analytics != null && analytics.receivedCents != 0L) {
            Column(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding)) {
                Text(
                    text = stringResource(R.string.activity_received_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinTrackTheme.colors.textSecondary,
                )
                Text(text = formatAmountCents(analytics.receivedCents), style = AmountLarge, maxLines = 1)
                if (analytics.expectedCents != null && analytics.differenceCents != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.activity_expected_difference,
                                formatAmountCents(analytics.expectedCents),
                                signedAmount(analytics.differenceCents),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTrackTheme.colors.textSecondary,
                    )
                }
            }
        }
        figures.evolution?.let { EvolutionSection(it, padding) }
        analytics?.sources?.takeIf { it.isNotEmpty() }?.let { SourceRows(it, onSourceClick, padding) }
        figures.yearToDate?.let { YearToDateRow(it, padding, figures.leftover) }
    }
}

@Composable
private fun signedAmount(cents: Long): String =
    when {
        cents > 0 -> "+" + formatAmountCents(cents)
        cents < 0 -> "−" + formatAmountCents(-cents)
        else -> formatAmountCents(0L)
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
                        row.receivedCents < expected -> colors.negative
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
