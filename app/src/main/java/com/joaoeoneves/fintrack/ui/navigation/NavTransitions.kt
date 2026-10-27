package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import com.joaoeoneves.fintrack.ui.theme.REDUCED_MOTION_FADE_MS
import com.joaoeoneves.fintrack.ui.theme.SCREEN_TRANSITION_MS

// Fraction of the screen the shared-axis motion travels: a small slide, not a full push.
private const val HORIZONTAL_DIVISOR = 10
private const val VERTICAL_DIVISOR = 8

/**
 * Shared-axis screen transitions. Pushed screens slide a little along X (add/edit forms along Y)
 * while cross-fading; under reduced motion everything is a short fade.
 */
internal class NavTransitions(
    private val reducedMotion: Boolean,
) {
    private val duration get() = if (reducedMotion) REDUCED_MOTION_FADE_MS else SCREEN_TRANSITION_MS

    fun enter(): EnterTransition =
        if (reducedMotion) {
            fadeIn(tween(duration))
        } else {
            slideInHorizontally(tween(duration)) { it / HORIZONTAL_DIVISOR } + fadeIn(tween(duration))
        }

    fun exit(): ExitTransition =
        if (reducedMotion) {
            fadeOut(tween(duration))
        } else {
            slideOutHorizontally(tween(duration)) { -it / HORIZONTAL_DIVISOR } + fadeOut(tween(duration))
        }

    fun popEnter(): EnterTransition =
        if (reducedMotion) {
            fadeIn(tween(duration))
        } else {
            slideInHorizontally(tween(duration)) { -it / HORIZONTAL_DIVISOR } + fadeIn(tween(duration))
        }

    fun popExit(): ExitTransition =
        if (reducedMotion) {
            fadeOut(tween(duration))
        } else {
            slideOutHorizontally(tween(duration)) { it / HORIZONTAL_DIVISOR } + fadeOut(tween(duration))
        }

    fun enterFromBottom(): EnterTransition =
        if (reducedMotion) {
            fadeIn(tween(duration))
        } else {
            slideInVertically(tween(duration)) { it / VERTICAL_DIVISOR } + fadeIn(tween(duration))
        }

    fun popExitToBottom(): ExitTransition =
        if (reducedMotion) {
            fadeOut(tween(duration))
        } else {
            slideOutVertically(tween(duration)) { it / VERTICAL_DIVISOR } + fadeOut(tween(duration))
        }
}
