package com.joaoeoneves.fintrack.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Shared motion tokens: every animation in the app takes its duration, easing and spring from here. */
@Suppress("ktlint:standard:property-naming") // PascalCase token names are the intended API (Motion.Short)
object Motion {
    const val Short = 150
    const val Medium = 250
    const val Long = 300
    const val Emphasis = 500
    const val DrawIn = 700

    val Standard = FastOutSlowInEasing
    val EmphasizedDecelerate = CubicBezierEasing(a = 0.05f, b = 0.7f, c = 0.1f, d = 1f)
    val EmphasizedAccelerate = CubicBezierEasing(a = 0.3f, b = 0f, c = 0.8f, d = 0.15f)
}

/** Duration of the shared-axis screen transitions. */
const val SCREEN_TRANSITION_MS = Motion.Long

/** Whether decorative motion should be skipped (animations disabled system-wide). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** True when the system animator duration scale is zero, i.e. the user turned animations off. */
fun isReducedMotion(scale: Float): Boolean = scale == 0f

/** Tracks the system animator duration scale, reacting to changes while the app is running. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(readReduced(resolver)) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = readReduced(resolver)
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        reduced = readReduced(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun readReduced(resolver: android.content.ContentResolver): Boolean =
    isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
