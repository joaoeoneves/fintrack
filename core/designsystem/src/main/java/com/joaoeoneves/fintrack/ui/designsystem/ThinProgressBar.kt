package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.Motion
import com.joaoeoneves.fintrack.ui.theme.motionSpec

/**
 * 4dp progress bar. [progress] is clamped to 0..1; the first value draws in from zero when [drawIn] (pass false for
 * bars that report real work in progress). The fill turns red when [isOver], with a one-shot pulse on the flip.
 */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    isOver: Boolean = false,
    drawIn: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by rememberDrawnProgress(target, drawIn)
    val pulse by rememberOverspendPulse(isOver)
    val negative = FinTrackTheme.colors.negative
    val neutral = MaterialTheme.colorScheme.onSurface
    val fill by animateColorAsState(
        targetValue = if (isOver) negative else neutral,
        animationSpec = motionSpec(tween(Motion.Medium)),
        label = "thinProgressColor",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimens.ThinBarHeight)
                .graphicsLayer { scaleY = pulse }
                .progressSemantics(target)
                .clip(CircleShape)
                .background(FinTrackTheme.colors.hairline),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(fill),
        )
    }
}
