package com.joaoeoneves.fintrack.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.formatPercent
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.insights.InsightsBreakdown
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.AmountLarge
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt

// The multiplier used to convert a fractional share into a whole-number percentage.
private const val PERCENT_SCALE = 100

private val BarHeight = 10.dp
private val SegmentGap = 2.dp
private val DotSize = 8.dp
private val PercentColumnWidth = 48.dp
private val TabsWidth = 176.dp
private const val SEGMENT_MS = 600

/**
 * Plain (non-composable), unit-testable computation of a slice's share of the total, rounded to
 * the nearest whole percent. Returns 0 when [totalCents] is non-positive, avoiding a divide-by-zero.
 */
fun percentageOf(
    amountCents: Long,
    totalCents: Long,
): Int {
    if (totalCents <= 0L) return 0
    return ((amountCents.toDouble() / totalCents.toDouble()) * PERCENT_SCALE).roundToInt()
}

/**
 * The slices as drawn by the chart: a category whose refunds exceed its expenses has a negative net
 * total, which can't be drawn as a segment, so it is shown as zero instead.
 */
fun List<CategoryTotal>.clampedForChart(): List<CategoryTotal> =
    map { slice ->
        slice.copy(totalCents = slice.totalCents.coerceAtLeast(0))
    }

/** One ranked row of the breakdown: [percent] is whole-number rounded, [fraction] the exact 0..1 share. */
data class BreakdownEntry(
    val category: Category,
    val cents: Long,
    val percent: Int,
    val fraction: Float,
)

/**
 * The breakdown rows for [slices]: clamped at zero, empty categories dropped and the rest ranked
 * by amount, largest first (ties keep their input order).
 */
fun rankedBreakdown(slices: List<CategoryTotal>): List<BreakdownEntry> {
    val clamped = slices.clampedForChart()
    val total = clamped.sumOf { it.totalCents }
    return clamped
        .filter { it.totalCents > 0 }
        .sortedByDescending { it.totalCents }
        .map { slice ->
            BreakdownEntry(
                category = slice.category,
                cents = slice.totalCents,
                percent = percentageOf(slice.totalCents, total),
                fraction = slice.totalCents.toFloat() / total.toFloat(),
            )
        }
}

/**
 * The Insights tab's breakdown block: total outflow (or the planned total), an "Actual | Planned"
 * switch when the plan has planned allocations, and the stateless [CategoryBreakdown] below it. When
 * [onCategoryClick] is set, each category row is tappable.
 */
@Composable
fun CategoryBreakdownSection(
    breakdown: InsightsBreakdown,
    modifier: Modifier = Modifier,
    onCategoryClick: ((Category) -> Unit)? = null,
) {
    val hasPlanned = breakdown.plannedSlices.isNotEmpty()
    var showPlanned by rememberSaveable { mutableStateOf(false) }
    val planned = showPlanned && hasPlanned
    val slices = if (planned) breakdown.plannedSlices else breakdown.categoryTotals

    if (!breakdown.hasExpenses && !hasPlanned) {
        BreakdownEmpty(modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(
            title =
                stringResource(
                    if (planned) R.string.dashboard_planned_per_month else R.string.dashboard_total_outflow,
                ),
            trailing =
                if (hasPlanned) {
                    { ActualPlannedTabs(planned = planned, onSelect = { showPlanned = it }) }
                } else {
                    null
                },
        )
        if (!planned && !breakdown.hasExpenses) {
            BreakdownEmpty()
        } else {
            Text(
                text = formatAmountCents(slices.clampedForChart().sumOf { it.totalCents }),
                style = AmountLarge,
                maxLines = 1,
            )
            CategoryBreakdown(
                slices = slices,
                totalDescriptionRes = if (planned) R.string.cd_pie_chart_planned_total else R.string.cd_pie_chart_total,
                onCategoryClick = onCategoryClick,
                modifier = Modifier.padding(top = Dimens.Space16),
            )
        }
    }
}

@Composable
private fun ActualPlannedTabs(
    planned: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    SegmentedTextTabs(
        options = listOf(false, true),
        selected = planned,
        label = {
            stringResource(if (it) R.string.dashboard_breakdown_planned else R.string.dashboard_breakdown_actual)
        },
        onSelect = onSelect,
        modifier = Modifier.width(TabsWidth),
    )
}

@Composable
private fun BreakdownEmpty(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.dashboard_no_expenses),
        style = MaterialTheme.typography.bodyMedium,
        color = FinTrackTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.Space8),
    )
}

/**
 * Stateless breakdown: a segmented bar in the category colours with a ranked list underneath. The
 * bar is one accessibility node describing the total ([totalDescriptionRes]); each row is a single
 * merged node ("name, N percent, amount") so the bare category names stay unreachable. A row is a button
 * when [onCategoryClick] is set.
 */
@Composable
fun CategoryBreakdown(
    slices: List<CategoryTotal>,
    modifier: Modifier = Modifier,
    @StringRes totalDescriptionRes: Int = R.string.cd_pie_chart_total,
    onCategoryClick: ((Category) -> Unit)? = null,
) {
    val chartSlices = slices.clampedForChart()
    val entries = rankedBreakdown(slices)
    val total = chartSlices.sumOf { it.totalCents }

    fun totalOf(kind: CategoryKind) = chartSlices.filter { it.category.kind == kind }.sumOf { it.totalCents }
    val barDescription =
        stringResource(
            totalDescriptionRes,
            formatAmountCents(total, typographic = false),
            formatAmountCents(totalOf(CategoryKind.SPENDING), typographic = false),
            formatAmountCents(totalOf(CategoryKind.SAVING), typographic = false),
        )
    Column(modifier = modifier.fillMaxWidth()) {
        BreakdownBar(
            entries = entries,
            modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription = barDescription
                    role = Role.Image
                },
        )
        Column(modifier = Modifier.padding(top = Dimens.Space8)) {
            entries.forEach { entry -> key(entry.category.id) { BreakdownRow(entry, onCategoryClick) } }
        }
    }
}

@Composable
private fun BreakdownBar(
    entries: List<BreakdownEntry>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(CircleShape)
                .background(FinTrackTheme.colors.hairline),
    ) {
        val available = maxWidth - SegmentGap * (entries.size - 1).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
            entries.forEach { entry ->
                key(entry.category.id) {
                    BarSegment(fraction = entry.fraction, color = entry.category.color, availableWidth = available)
                }
            }
        }
    }
}

@Composable
private fun BarSegment(
    fraction: Float,
    color: Color,
    availableWidth: Dp,
) {
    val reducedMotion = LocalReducedMotion.current
    val animated = remember { Animatable(if (reducedMotion) fraction else 0f) }
    LaunchedEffect(fraction) {
        if (reducedMotion) {
            animated.snapTo(fraction)
        } else {
            animated.animateTo(fraction, tween(SEGMENT_MS, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier =
            Modifier
                .width(availableWidth * animated.value)
                .height(BarHeight)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
private fun BreakdownRow(
    entry: BreakdownEntry,
    onClick: ((Category) -> Unit)?,
) {
    val name = entry.category.displayName
    val amount = formatAmountCents(entry.cents)
    val spokenAmount = formatAmountCents(entry.cents, typographic = false)
    val description = stringResource(R.string.cd_pie_chart_category_item, name, entry.percent, spokenAmount)
    val tap = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button) { onClick(entry.category) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(tap)
                .padding(vertical = Dimens.Space12)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (onClick != null) {
                        role = Role.Button
                        onClick {
                            onClick(entry.category)
                            true
                        }
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(DotSize).background(entry.category.color, CircleShape))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Dimens.Space12),
        )
        Text(
            text = formatPercent(entry.percent, currentLocale()),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = FinTrackTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(PercentColumnWidth),
        )
        Text(
            text = amount,
            style = Amount,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = Dimens.Space8),
        )
    }
}
