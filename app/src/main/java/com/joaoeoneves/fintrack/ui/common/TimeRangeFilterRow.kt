package com.joaoeoneves.fintrack.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.domain.model.TimeRange

// Duration (in ms) of the selected-segment background/content color cross-fade below.
private const val SEGMENT_TRANSITION_MILLIS = 200

/**
 * A single pill-shaped segmented control, one equal-weight segment per [TimeRange] value, shared
 * between the dashboard and full expense/income list screens.
 */
@Composable
fun TimeRangeFilterRow(
    selected: TimeRange,
    onSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            TimeRange.entries.forEach { range ->
                TimeRangeSegment(
                    label = range.label,
                    selected = range == selected,
                    onClick = { onSelected(range) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TimeRangeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by
        animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            animationSpec = tween(SEGMENT_TRANSITION_MILLIS),
            label = "segmentBackground",
        )
    val contentColor by
        animateColorAsState(
            targetValue =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            animationSpec = tween(SEGMENT_TRANSITION_MILLIS),
            label = "segmentContent",
        )

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(color = backgroundColor, shape = RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
