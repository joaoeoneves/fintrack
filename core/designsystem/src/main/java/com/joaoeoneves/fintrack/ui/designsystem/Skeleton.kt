package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.Motion
import com.joaoeoneves.fintrack.ui.theme.reducedOr

private const val SHIMMER_MIN_ALPHA = 0.5f
private const val SHIMMER_MS = 1000
private val LineHeight = 14.dp
private val AvatarSize = 40.dp
private val CardHeight = 120.dp

/** Shimmer alpha shared by every skeleton block under one [Skeleton] host (a constant 1 outside one). */
private val LocalSkeletonAlpha = compositionLocalOf<State<Float>?> { null }

/**
 * Hosts skeleton blocks: one shared shimmer (alpha 0.5 to 1, 1s, infinite; static under reduced motion), hidden
 * from accessibility and tagged [TestTags.SKELETON]. Remove it from composition once content or an error shows,
 * since an infinite animation never lets UI tests go idle.
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha = shimmerAlpha()
    CompositionLocalProvider(LocalSkeletonAlpha provides alpha) {
        Box(modifier.testTag(TestTags.SKELETON).clearAndSetSemantics {}) { content() }
    }
}

@Composable
private fun shimmerAlpha(): State<Float> {
    if (LocalReducedMotion.current) return remember { mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = SHIMMER_MIN_ALPHA,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                tween(SHIMMER_MS, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
        label = "skeletonAlpha",
    )
}

/** Fills the element with the surface2 colour at the shared shimmer alpha. */
fun Modifier.skeleton(shape: Shape = RoundedCornerShape(Dimens.Space8)): Modifier =
    composed {
        val alpha = LocalSkeletonAlpha.current
        val color = FinTrackTheme.colors.surface2
        drawBehind {
            drawOutline(shape.createOutline(size, layoutDirection, this), color, alpha = alpha?.value ?: 1f)
        }
    }

/** A text-line placeholder. */
@Composable
fun SkeletonLine(
    width: Dp,
    modifier: Modifier = Modifier,
    height: Dp = LineHeight,
) {
    Box(modifier.width(width).height(height).skeleton())
}

/** A rounded card placeholder of [height]. */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = CardHeight,
) {
    Box(modifier.fillMaxWidth().height(height).skeleton(RoundedCornerShape(Dimens.Space16)))
}

/** One list-row placeholder at [Dimens.RowHeight]: leading avatar, two lines and an amount. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(Dimens.RowHeight).padding(horizontal = Dimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
    ) {
        Box(Modifier.size(AvatarSize).skeleton(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
            SkeletonLine(width = 140.dp)
            SkeletonLine(width = 90.dp, height = 10.dp)
        }
        SkeletonLine(width = 56.dp)
    }
}

/** [rows] stacked [SkeletonRow]s, matching the real list's row height. */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 8,
) {
    Skeleton(modifier.fillMaxWidth()) {
        Column {
            repeat(rows) { SkeletonRow() }
        }
    }
}

/** Skeleton for a screen led by a hero amount and a chart, then [rows] list rows (the Dashboard). */
@Composable
fun HeroListSkeleton(
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
    rows: Int = 5,
) {
    Skeleton(modifier.fillMaxWidth()) {
        Column {
            Column(
                Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Dimens.Space16),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
            ) {
                SkeletonLine(width = 100.dp)
                SkeletonLine(width = 200.dp, height = 44.dp)
            }
            Box(
                Modifier
                    .padding(top = Dimens.Space8)
                    .fillMaxWidth()
                    .height(chartHeight)
                    .skeleton(),
            )
            repeat(rows) { SkeletonRow(Modifier.padding(top = if (it == 0) Dimens.Space24 else 0.dp)) }
        }
    }
}

/** Skeleton for a screen made of stacked cards (Plan, Income, Annual, Savings, Salary). */
@Composable
fun CardsSkeleton(
    modifier: Modifier = Modifier,
    cards: Int = 3,
) {
    Skeleton(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Dimens.Space16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
        ) {
            repeat(cards) { SkeletonCard() }
        }
    }
}

/**
 * Crossfades between Loading, Content and Error: fades only when the state's class changes, so content updates
 * don't re-fade. The loading skeleton leaves composition once the fade ends (instantly under reduced motion).
 */
@Composable
fun <T : Any> StateCrossfade(
    state: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            val spec = reducedOr(reduced, tween<Float>(Motion.Short))
            fadeIn(spec) togetherWith fadeOut(spec) using null
        },
        label = "stateCrossfade",
        contentKey = { it::class },
        content = { content(it) },
    )
}
