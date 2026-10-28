package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.Motion
import com.joaoeoneves.fintrack.ui.theme.progressSpring

private const val PULSE_PEAK = 1.25f

/** The overspend pulse plays only when a bar/ring flips from within budget to over, and never under reduced motion. */
fun shouldPulse(
    wasOver: Boolean,
    isOver: Boolean,
    reduced: Boolean,
): Boolean = !reduced && !wasOver && isOver

/**
 * Animated 0..1 fill for a bar or ring. With [drawIn] the first value draws in from zero; later changes spring from
 * the previous value. Under reduced motion (or without [drawIn]) the first value is shown immediately, and reduced
 * motion also snaps every later change.
 */
@Composable
fun rememberDrawnProgress(
    target: Float,
    drawIn: Boolean,
): State<Float> {
    val reduced = LocalReducedMotion.current
    val animatable = remember { Animatable(if (drawIn && !reduced) 0f else target) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target, reduced) {
        val isFirst = first
        first = false
        when {
            reduced -> animatable.snapTo(target)
            isFirst && drawIn ->
                animatable.animateTo(target, tween(Motion.DrawIn, easing = Motion.EmphasizedDecelerate))
            isFirst -> animatable.snapTo(target)
            else -> animatable.animateTo(target, progressSpring())
        }
    }
    return animatable.asState()
}

/** A scale factor that bumps once, briefly, when [isOver] flips to true. Stays at 1 under reduced motion. */
@Composable
fun rememberOverspendPulse(isOver: Boolean): State<Float> {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    var wasOver by remember { mutableStateOf(isOver) }
    LaunchedEffect(isOver) {
        val previous = wasOver
        wasOver = isOver
        if (shouldPulse(previous, isOver, reduced)) {
            scale.animateTo(PULSE_PEAK, tween(Motion.Short))
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    return scale.asState()
}

/**
 * True when a goal just crossed from incomplete to complete. [previous] is null on first show, so a goal that is
 * already complete when its card appears never celebrates.
 */
fun crossedGoal(
    previous: Float?,
    now: Float,
): Boolean = previous != null && previous < 1f && now >= 1f

/**
 * Celebrates a goal being reached: a success haptic (always) and a ring scale pulse (not under reduced motion) the
 * moment [fraction] crosses 1, never on first show. Returns the pulse scale (1 at rest).
 */
@Composable
fun rememberGoalCelebration(fraction: Float): State<Float> {
    val reduced = LocalReducedMotion.current
    val haptics = rememberFinTrackHaptics()
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(fraction) {
        val before = previous
        previous = fraction
        if (crossedGoal(before, fraction)) {
            haptics.success()
            if (!reduced) {
                scale.animateTo(PULSE_PEAK, tween(Motion.Short))
                scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
        }
    }
    return scale.asState()
}
