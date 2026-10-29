package com.joaoeoneves.fintrack.ui.activity.analytics

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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * The top of the Income tab, mirroring the Expenses header: period controls, the big "Received" total (by funding
 * month), "Expected X - Difference Y" when lines expect something, and behind "Show details" the per-source rows
 * (tap one to filter the list and chart to it), the Evolution bars and "Since January".
 */
@Suppress("LongParameterList") // fed straight from the screen state, like the Expenses header
@Composable
fun IncomeAnalyticsHeader(
    period: Period,
    canGoToNextMonth: Boolean,
    history: ActivityHistory?,
    selectedSource: String?,
    actions: ActivityAnalyticsActions,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val analytics = remember(history) { history?.incomeAnalytics() }
    val figures = remember(history, selectedSource) { history?.incomeFigures(selectedSource) ?: HistoryFigures() }
    Column(modifier = modifier.fillMaxWidth()) {
        AnalyticsPeriodControls(period, canGoToNextMonth, actions)
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
        val hasDetails = analytics?.sources?.isNotEmpty() == true || figures.hasDetails
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
            analytics?.sources?.takeIf { it.isNotEmpty() }?.let {
                SourceRows(it, selectedSource, actions.onIncomeSourceSelected)
            }
            HistoryDetails(figures)
        }
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
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8)) {
        SectionTitle(title = stringResource(R.string.activity_by_source))
        sources.forEach { row ->
            SourceRow(row, row.key == selected) { onSelected(if (row.key == selected) null else row.key) }
        }
    }
}

@Composable
private fun SourceRow(
    row: IncomeSourceRow,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FinTrackTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics { selected = isSelected }
                .padding(vertical = Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label ?: stringResource(R.string.activity_source_other),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) colors.accentText else MaterialTheme.colorScheme.onSurface,
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
