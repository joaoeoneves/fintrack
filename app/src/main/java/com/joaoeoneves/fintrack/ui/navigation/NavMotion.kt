package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.joaoeoneves.fintrack.ui.theme.Motion

/** The kind of motion a screen change uses; picked by [navMotionFor]. */
internal enum class NavMotion { Fade, TabForward, TabBack, Push, PushForm }

/**
 * Picks the motion for a change between two destinations. [fromTab]/[toTab] are the tab ordinals when the
 * destination is a tab root (else `null`); [toIsForm] is whether the pushed/popped screen is an add/edit form.
 * Reduced motion always yields a short fade.
 */
internal fun navMotionFor(
    fromTab: Int?,
    toTab: Int?,
    toIsForm: Boolean,
    reduced: Boolean,
): NavMotion =
    when {
        reduced -> NavMotion.Fade
        fromTab != null && toTab != null ->
            when {
                toTab > fromTab -> NavMotion.TabForward
                toTab < fromTab -> NavMotion.TabBack
                else -> NavMotion.Fade
            }
        toIsForm -> NavMotion.PushForm
        else -> NavMotion.Push
    }

internal fun enterFor(motion: NavMotion): EnterTransition =
    when (motion) {
        NavMotion.Fade -> fadeIn(tween(Motion.Short))
        NavMotion.TabForward -> tabIn(1)
        NavMotion.TabBack -> tabIn(-1)
        NavMotion.Push -> pushIn(1)
        NavMotion.PushForm -> formIn()
    }

internal fun exitFor(motion: NavMotion): ExitTransition =
    when (motion) {
        NavMotion.Fade -> fadeOut(tween(Motion.Short))
        NavMotion.TabForward -> tabOut(-1)
        NavMotion.TabBack -> tabOut(1)
        NavMotion.Push -> pushOut(-1)
        // The screen beneath a form holds still and fades while the form slides up over it.
        NavMotion.PushForm -> fadeOut(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate))
    }

internal fun popEnterFor(motion: NavMotion): EnterTransition =
    when (motion) {
        NavMotion.Push -> pushIn(-1)
        NavMotion.PushForm -> fadeIn(tween(Motion.Medium, easing = Motion.EmphasizedDecelerate))
        else -> enterFor(motion)
    }

internal fun popExitFor(motion: NavMotion): ExitTransition =
    when (motion) {
        NavMotion.Push -> pushOut(1)
        NavMotion.PushForm -> formOut()
        else -> exitFor(motion)
    }
