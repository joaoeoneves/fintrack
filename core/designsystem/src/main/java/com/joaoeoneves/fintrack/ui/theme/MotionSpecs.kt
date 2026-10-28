package com.joaoeoneves.fintrack.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/** Spring used by sliding indicators and pressed-state feedback. */
fun <T> indicatorSpring() = spring<T>(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)

/** Spring for [Dp] values (sliding tab indicator). */
fun indicatorDpSpring() = indicatorSpring<Dp>()

/** Spring for progress fills: settles without overshoot. */
fun progressSpring() = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)

/** Spring for list item placement changes. */
fun placementSpring() =
    spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

/** Spring for a gesture snapping back (swipe released below its threshold). */
fun <T> snapBackSpring() = spring<T>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)

/** [spec], or an instant [snap] when [reduced] motion is on. */
fun <T> reducedOr(
    reduced: Boolean,
    spec: FiniteAnimationSpec<T>,
): FiniteAnimationSpec<T> = if (reduced) snap() else spec

/** [spec] resolved against [LocalReducedMotion]. */
@Composable
fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = reducedOr(LocalReducedMotion.current, spec)

/** `animateItem` with every spec removed under reduced motion, so list changes apply instantly. */
@Composable
fun LazyItemScope.motionAnimateItem(modifier: Modifier = Modifier): Modifier =
    if (LocalReducedMotion.current) {
        modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    } else {
        modifier.animateItem(placementSpec = placementSpring())
    }
