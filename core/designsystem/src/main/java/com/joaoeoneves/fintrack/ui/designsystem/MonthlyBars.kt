@file:Suppress("MatchingDeclarationName") // MonthlyBars is the composable; MonthlyBar is its data holder.

package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion

/** One bar: its axis [label], [valueCents] (negatives draw as zero) and the [accessibilityText] spoken for it. */
data class MonthlyBar(
    val label: String,
    val valueCents: Long,
    val accessibilityText: String,
)

private val ChartHeight = 120.dp
private val BarMinHeight = 2.dp
private val BarCorner = 4.dp
private val MinTouchTarget = 48.dp

/**
 * A stateless bar chart, one bar per month, scaled to the largest value. [highlighted] indexes are drawn in the
 * emphasis colour, the rest muted. Each bar is a single accessibility node with its [MonthlyBar.accessibilityText];
 * tapping a bar shows its value above the chart (local state only) and calls [onBarClick]; it never filters.
 * Bars snap under reduced motion and otherwise draw in with the shared progress spring.
 */
@Composable
fun MonthlyBars(
    bars: List<MonthlyBar>,
    modifier: Modifier = Modifier,
    highlighted: Set<Int> = emptySet(),
    onBarClick: ((Int) -> Unit)? = null,
) {
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val maxValue = bars.maxOfOrNull { it.valueCents }?.coerceAtLeast(1L) ?: 1L
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        Text(
            text = selected?.let { bars.getOrNull(it) }?.let { formatAmountCents(it.valueCents) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.heightIn(min = Dimens.Space16).clearAndSetSemantics { },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space4), modifier = Modifier.fillMaxWidth()) {
            bars.forEachIndexed { index, bar ->
                BarColumn(
                    bar = bar,
                    fraction = (bar.valueCents.coerceAtLeast(0L).toDouble() / maxValue).toFloat(),
                    emphasised = index in highlighted || index == selected,
                    onClick = {
                        selected = if (selected == index) null else index
                        onBarClick?.invoke(index)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarColumn(
    bar: MonthlyBar,
    fraction: Float,
    emphasised: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val animated by rememberDrawnProgress(fraction, drawIn = !reduced)
    val colors = FinTrackTheme.colors
    val fill = if (emphasised) MaterialTheme.colorScheme.onSurface else colors.hairline
    val barHeight: Dp = maxOf(BarMinHeight, ChartHeight * animated.coerceIn(0f, 1f))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .heightIn(min = MinTouchTarget)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = bar.accessibilityText
                },
    ) {
        Box(modifier = Modifier.height(ChartHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier =
                    Modifier
                        .height(barHeight)
                        .fillMaxWidth(BAR_WIDTH_FRACTION)
                        .clip(RoundedCornerShape(topStart = BarCorner, topEnd = BarCorner))
                        .background(fill),
            )
        }
        Text(
            text = bar.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasised) MaterialTheme.colorScheme.onSurface else FinTrackTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = Dimens.Space4),
        )
    }
}

private const val BAR_WIDTH_FRACTION = 0.7f
