package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.indicatorDpSpring

private val TabHeight = 40.dp

/**
 * Equal-width text tabs with a pill indicator that slides to the selected tab. [selected] may be
 * null (no tab selected, indicator hidden). [tabContentDescription], when given, replaces the
 * spoken label of each tab.
 */
@Suppress("LongParameterList") // generic component API; the optional description keeps call sites terse
@Composable
fun <T> SegmentedTextTabs(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tabContentDescription: (@Composable (T) -> String)? = null,
) {
    val selectedIndex = options.indexOf(selected)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(TabHeight)) {
        val tabWidth = maxWidth / options.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex.coerceAtLeast(0),
            animationSpec = indicatorDpSpring(),
            label = "tabIndicatorOffset",
        )
        val indicatorAlpha by animateFloatAsState(
            targetValue = if (selectedIndex >= 0) 1f else 0f,
            animationSpec = tween(durationMillis = 150),
            label = "tabIndicatorAlpha",
        )
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(x = indicatorOffset.roundToPx(), y = 0) }
                    .width(tabWidth)
                    .height(TabHeight)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .clip(CircleShape)
                    .background(FinTrackTheme.colors.surface2),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                SegmentedTab(
                    label = label(option),
                    selected = option == selected,
                    description = tabContentDescription?.invoke(option),
                    onClick = { onSelect(option) },
                    modifier = Modifier.width(tabWidth),
                )
            }
        }
    }
}

@Composable
private fun SegmentedTab(
    label: String,
    selected: Boolean,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(TabHeight)
                .clip(CircleShape)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .then(
                    if (description != null) {
                        Modifier.semantics { contentDescription = description }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) FinTrackTheme.colors.accentText else FinTrackTheme.colors.textSecondary,
            maxLines = 1,
        )
    }
}
