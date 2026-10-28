package com.joaoeoneves.fintrack.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/** Duration of the shared-axis screen transitions. */
const val SCREEN_TRANSITION_MS = 300

/** Shorter cross-fade used instead of the spatial transitions under reduced motion. */
const val REDUCED_MOTION_FADE_MS = 150

/** Spring used by sliding indicators and pressed-state feedback. */
fun <T> indicatorSpring() = spring<T>(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)

/** Spring for [Dp] values (sliding tab indicator). */
fun indicatorDpSpring() = indicatorSpring<Dp>()

/** Whether decorative motion should be skipped (animations disabled system-wide). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** True when the system animator duration scale is zero, i.e. the user turned animations off. */
fun isReducedMotion(scale: Float): Boolean = scale == 0f

@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}
