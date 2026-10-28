package com.joaoeoneves.fintrack.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.formatPercent
import com.joaoeoneves.fintrack.ui.dashboard.percentageOf
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val SplitBarHeight = 10.dp
private val SplitGap = 2.dp
private val MarkerSize = 8.dp
private val PercentColumnWidth = 48.dp

/** Fixed vs variable spending: a two-segment bar (fixed in the text colour, variable muted) and amounts. */
@Composable
fun SpendingSplitCard(
    split: SpendingSplit,
    modifier: Modifier = Modifier,
) {
    val total = split.spendingCents
    val description =
        stringResource(
            R.string.cd_insights_split,
            formatAmountCents(split.fixedCents, typographic = false),
            formatAmountCents(split.variableCents, typographic = false),
        )
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = stringResource(R.string.insights_split_title))
        SplitBar(
            split = split,
            modifier =
                Modifier
                    .padding(top = Dimens.Space8)
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Image
                    },
        )
        SplitRow(
            label = stringResource(R.string.insights_split_fixed),
            cents = split.fixedCents,
            total = total,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SplitRow(
            label = stringResource(R.string.insights_split_variable),
            cents = split.variableCents,
            total = total,
            color = FinTrackTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SplitBar(
    split: SpendingSplit,
    modifier: Modifier = Modifier,
) {
    val total = split.spendingCents.toFloat()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SplitBarHeight)
                .clip(CircleShape)
                .background(FinTrackTheme.colors.hairline),
        horizontalArrangement = Arrangement.spacedBy(SplitGap),
    ) {
        if (split.fixedCents > 0) {
            Box(
                Modifier
                    .weight(split.fixedCents / total)
                    .height(SplitBarHeight)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
        if (split.variableCents > 0) {
            Box(
                Modifier
                    .weight(split.variableCents / total)
                    .height(SplitBarHeight)
                    .background(FinTrackTheme.colors.textSecondary),
            )
        }
    }
}

@Composable
private fun SplitRow(
    label: String,
    cents: Long,
    total: Long,
    color: Color,
) {
    AmountRow(label = label, cents = cents, total = total, marker = color)
}

/** The envelope view of the breakdown: each envelope's share of the period's outflow, "No envelope" for the rest. */
@Composable
fun EnvelopeBreakdown(
    summary: EnvelopesSummary,
    modifier: Modifier = Modifier,
) {
    val noEnvelope = stringResource(R.string.plan_group_other)
    val shown =
        (
            summary.envelopes.map { it.envelope.id to (it.envelope.name to it.spentCents) } +
                listOf("" to (noEnvelope to summary.unenvelopedSpentCents))
        ).filter { it.second.second > 0 }
            .sortedByDescending { it.second.second }
    val total = shown.sumOf { it.second.second }
    Column(modifier = modifier.fillMaxWidth()) {
        shown.forEach { (id, named) ->
            key(id) {
                AmountRow(label = named.first, cents = named.second, total = total)
                ThinProgressBar(progress = named.second.toFloat() / total.toFloat())
            }
        }
    }
}

@Composable
private fun AmountRow(
    label: String,
    cents: Long,
    total: Long,
    modifier: Modifier = Modifier,
    marker: Color? = null,
) {
    val percent = percentageOf(cents, total)
    val spoken =
        stringResource(
            R.string.cd_pie_chart_category_item,
            label,
            percent,
            formatAmountCents(cents, typographic = false),
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.Space12)
                .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marker != null) {
            Box(Modifier.size(MarkerSize).background(marker, CircleShape))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (marker != null) Dimens.Space12 else 0.dp),
        )
        Text(
            text = formatPercent(percent, currentLocale()),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = FinTrackTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(PercentColumnWidth),
        )
        Text(
            text = formatAmountCents(cents),
            style = Amount,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Dimens.Space8),
        )
    }
}
