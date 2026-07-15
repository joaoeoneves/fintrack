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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents

private val RING_SIZE = 160.dp
private val RING_STROKE_WIDTH = 32.dp

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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(RING_SIZE),
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Spent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatAmountCents(total),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
        ) {
            slices.forEach { slice ->
                PieChartLegendRow(slice)
            }
        }
    }
}

@Composable
private fun PieChartLegendRow(slice: CategoryTotal) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .background(color = slice.category.color, shape = CircleShape),
            )
            Text(
                text = slice.category.displayName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = formatAmountCents(slice.totalCents),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
