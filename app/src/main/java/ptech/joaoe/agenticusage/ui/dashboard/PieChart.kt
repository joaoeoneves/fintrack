package ptech.joaoe.agenticusage.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ptech.joaoe.agenticusage.ui.common.color
import ptech.joaoe.agenticusage.ui.common.displayName
import ptech.joaoe.agenticusage.ui.common.formatAmountCents
import ptech.joaoe.agenticusage.ui.common.icon

/**
 * Custom Canvas-based pie chart showing the share of spending per category, paired with a legend.
 */
@Composable
fun PieChart(
    slices: List<CategoryTotal>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.totalCents }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(16.dp)
        ) {
            if (total <= 0L) return@Canvas
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.totalCents.toFloat() / total.toFloat()) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = slice.category.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                    startAngle += sweep
                }
            }
        }

        slices.forEach { slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color = slice.category.color, shape = CircleShape)
                    )
                    Icon(
                        imageVector = slice.category.icon,
                        contentDescription = null,
                        tint = slice.category.color,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp)
                    )
                    Text(
                        text = slice.category.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    text = formatAmountCents(slice.totalCents),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
