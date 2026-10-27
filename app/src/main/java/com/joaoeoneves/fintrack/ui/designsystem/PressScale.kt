package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.indicatorSpring

private const val PRESSED_SCALE = 0.97f

/**
 * Shrinks the element slightly while [interactionSource] reports a press, with a spring back on
 * release. A no-op under reduced motion.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val reducedMotion = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) PRESSED_SCALE else 1f,
        animationSpec = indicatorSpring(),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
