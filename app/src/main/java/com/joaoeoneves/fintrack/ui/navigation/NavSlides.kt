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
import com.joaoeoneves.fintrack.ui.theme.Motion

// Fraction of the screen the shared-axis motion travels: a small slide, not a full push.
private const val HORIZONTAL_DIVISOR = 10
private const val VERTICAL_DIVISOR = 8
private const val TAB_DIVISOR = 25

internal fun tabIn(direction: Int): EnterTransition =
    slideInHorizontally(tween(Motion.Medium, easing = Motion.Standard)) { direction * it / TAB_DIVISOR } +
        fadeIn(tween(Motion.Medium, easing = Motion.Standard))

internal fun tabOut(direction: Int): ExitTransition =
    slideOutHorizontally(tween(Motion.Medium, easing = Motion.Standard)) { direction * it / TAB_DIVISOR } +
        fadeOut(tween(Motion.Medium, easing = Motion.Standard))

internal fun pushIn(direction: Int): EnterTransition =
    slideInHorizontally(tween(Motion.Long, easing = Motion.EmphasizedDecelerate)) {
        direction * it / HORIZONTAL_DIVISOR
    } + fadeIn(tween(Motion.Long, easing = Motion.EmphasizedDecelerate))

internal fun pushOut(direction: Int): ExitTransition =
    slideOutHorizontally(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)) {
        direction * it / HORIZONTAL_DIVISOR
    } + fadeOut(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate))

internal fun formIn(): EnterTransition =
    slideInVertically(tween(Motion.Long, easing = Motion.EmphasizedDecelerate)) { it / VERTICAL_DIVISOR } +
        fadeIn(tween(Motion.Long, easing = Motion.EmphasizedDecelerate))

internal fun formOut(): ExitTransition =
    slideOutVertically(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)) { it / VERTICAL_DIVISOR } +
        fadeOut(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate))
