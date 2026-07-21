package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import kotlin.math.roundToInt

// Same ring diameter as the original restyle, but a thinner stroke (26dp vs 32dp) so the inner
// hole has more room for the total-spent amount at realistic (even large) values.
//
// An earlier attempt at this also grew RING_SIZE itself (160dp -> 176dp) to guarantee no overlap
// for large totals, but that ate directly into the legend Column's width (same Row, legend has
// `Modifier.weight(1f)`) and made legend rows with longer category names (e.g.
// "Investments  $1000.00") wrap their amount onto a second line. That's unnecessary: the center
// label's TextAutoSize (below) already shrinks the amount font as needed to fit
// RING_HOLE_CONTENT_SIZE, so the thinner stroke alone is enough to stop the *common* case (e.g.
// "$1,242.00"-scale totals) from touching the ring at full font size, while autoSize handles
// genuinely large totals gracefully. Keeping RING_SIZE at its original value leaves the legend at
// its original, un-cramped width.
private val RING_SIZE = 160.dp
private val RING_STROKE_WIDTH = 26.dp

// The diameter of the ring's inner hole, i.e. the space available for the centered total text.
private val RING_HOLE_SIZE = RING_SIZE - RING_STROKE_WIDTH * 2

// A small safety margin so the amount text never visually touches the ring, even with rounded
// stroke caps.
private val RING_HOLE_CONTENT_SIZE = RING_HOLE_SIZE - 12.dp

// The multiplier used to convert a fractional share into a whole-number percentage.
private const val PERCENT_SCALE = 100

/**
 * The "Total Spent" label and formatted amount centered inside the ring's hole. The amount uses
 * auto-sizing text bounded to the hole's diameter so it always fits regardless of how many digits
 * the total has, instead of a fixed style that can overflow into the ring for larger totals.
 */
@Composable
private fun PieChartCenterLabel(totalCents: Long) {
    Column(
        modifier = Modifier.width(RING_HOLE_CONTENT_SIZE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.dashboard_total_spent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatAmountCents(totalCents),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 22.sp),
        )
    }
}

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
 * Builds the accessibility content description for the whole donut chart: the total spent amount
 * followed by a per-category breakdown (name, percentage, and amount) for each slice.
 */
@Composable
private fun buildChartContentDescription(
    slices: List<CategoryTotal>,
    total: Long,
): String {
    val totalText = formatAmountCents(total)
    val perCategoryCd =
        slices.map { slice ->
            stringResource(
                R.string.cd_pie_chart_category_item,
                slice.category.displayName,
                percentageOf(slice.totalCents, total),
                formatAmountCents(slice.totalCents),
            )
        }
    return stringResource(R.string.cd_pie_chart_total, totalText) +
        if (perCategoryCd.isNotEmpty()) " " + perCategoryCd.joinToString(separator = ". ") else ""
}

/**
 * Custom Canvas-based donut chart showing the share of spending per category, with the total spent
 * centered inside the ring and a legend rendered alongside it.
 */
@Composable
fun PieChart(
    slices: List<CategoryTotal>,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.totalCents }
    val sliceColors = slices.map { it.category.color }
    val strokeWidthPx = with(LocalDensity.current) { RING_STROKE_WIDTH.toPx() }

    val chartContentDescription = buildChartContentDescription(slices, total)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(RING_SIZE)
                    .clearAndSetSemantics {
                        contentDescription = chartContentDescription
                        role = Role.Image
                    },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(RING_SIZE)) {
                if (total <= 0L) return@Canvas
                val inset = strokeWidthPx / 2
                val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                var startAngle = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = (slice.totalCents.toFloat() / total.toFloat()) * 360f
                    if (sweep > 0f) {
                        drawArc(
                            color = sliceColors[index],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                        )
                        startAngle += sweep
                    }
                }
            }

            PieChartCenterLabel(total)
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
        ) {
            slices.forEach { slice ->
                PieChartLegendRow(slice, totalCents = total)
            }
        }
    }
}

@Composable
private fun PieChartLegendRow(
    slice: CategoryTotal,
    totalCents: Long,
) {
    val rowContentDescription =
        stringResource(
            R.string.cd_pie_chart_category_item,
            slice.category.displayName,
            percentageOf(slice.totalCents, totalCents),
            formatAmountCents(slice.totalCents),
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clearAndSetSemantics { contentDescription = rowContentDescription },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .background(color = slice.category.color, shape = CircleShape),
            )
            Text(
                text = slice.category.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = formatAmountCents(slice.totalCents),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
