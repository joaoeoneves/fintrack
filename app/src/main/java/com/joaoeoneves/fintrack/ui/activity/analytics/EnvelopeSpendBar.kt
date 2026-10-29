package com.joaoeoneves.fintrack.ui.activity.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val BarHeight = 10.dp
private val BarGap = 2.dp
private const val DIMMED_ALPHA = 0.35f

/** One slice of an [EnvelopeSpendBar]: its width is proportional to [cents]; empty slices are not drawn. */
data class BarSegmentData(
    val cents: Long,
    val color: Color,
)

/**
 * A segmented horizontal bar on a hairline track: one slice per [segments] entry (widths proportional to their
 * amounts). The bar is a single accessibility node reading [description]. With [onSegmentClick] a slice is
 * tappable (its index in [segments]); [selectedIndex] dims the other slices.
 */
@Composable
fun EnvelopeSpendBar(
    segments: List<BarSegmentData>,
    description: String,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSegmentClick: ((Int) -> Unit)? = null,
) {
    val total = segments.sumOf { it.cents.coerceAtLeast(0) }.toFloat()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(CircleShape)
                .background(FinTrackTheme.colors.hairline)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Image
                },
        horizontalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        if (total > 0f) {
            segments.forEachIndexed { index, segment ->
                if (segment.cents <= 0) return@forEachIndexed
                val dimmed = selectedIndex != null && selectedIndex != index
                Box(
                    Modifier
                        .weight(segment.cents / total)
                        .height(BarHeight)
                        .background(if (dimmed) segment.color.copy(alpha = DIMMED_ALPHA) else segment.color)
                        .then(
                            if (onSegmentClick == null) Modifier else Modifier.clickable { onSegmentClick(index) },
                        ),
                )
            }
        }
    }
}
