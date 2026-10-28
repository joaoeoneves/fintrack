package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.indicatorSpring

private val DefaultRingSize = 56.dp
private val DefaultRingStroke = 5.dp
private const val FULL_CIRCLE_DEGREES = 360f
private const val START_ANGLE = -90f

/**
 * A circular progress ring on a hairline track. [progress] is clamped to 0..1 and animated; the arc turns red when
 * [isOver]. [content] is centred inside the ring.
 */
@Suppress("LongParameterList") // size, stroke and content slot are independently optional
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    isOver: Boolean = false,
    size: Dp = DefaultRingSize,
    strokeWidth: Dp = DefaultRingStroke,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (LocalReducedMotion.current) snap() else indicatorSpring(),
        label = "progressRing",
    )
    val track = FinTrackTheme.colors.hairline
    val fill = if (isOver) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = START_ANGLE,
                sweepAngle = FULL_CIRCLE_DEGREES,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (animated > 0f) {
                drawArc(
                    color = fill,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_CIRCLE_DEGREES * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}
