package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.indicatorSpring

/** 4dp progress bar. [progress] is clamped to 0..1 and animated; the fill turns red when [isOver]. */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    isOver: Boolean = false,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (LocalReducedMotion.current) snapSpecFloat() else indicatorSpring(),
        label = "thinProgress",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimens.ThinBarHeight)
                .clip(CircleShape)
                .background(FinTrackTheme.colors.hairline),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(if (isOver) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface),
        )
    }
}

private fun snapSpecFloat() = snap<Float>()
