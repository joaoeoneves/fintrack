package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.Motion
import kotlin.math.roundToLong

/** The value [fraction] (0..1) of the way from [from] to [to], in whole cents. */
fun interpolateCents(
    from: Long,
    to: Long,
    fraction: Float,
): Long = from + ((to - from).toDouble() * fraction.toDouble()).roundToLong()

/**
 * Counts from the previous value to [cents] whenever [cents] changes (the first value is shown as-is, never
 * counted up from zero). Under reduced motion the state is always exactly [cents].
 */
@Composable
fun rememberAnimatedCents(cents: Long): State<Long> {
    val reducedMotion = LocalReducedMotion.current
    val latest by rememberUpdatedState(cents)
    val progress = remember { Animatable(0f) }
    var from by remember { mutableLongStateOf(cents) }
    var to by remember { mutableLongStateOf(cents) }

    LaunchedEffect(cents, reducedMotion) {
        if (cents == to) return@LaunchedEffect
        if (reducedMotion) {
            from = cents
            to = cents
            return@LaunchedEffect
        }
        from = interpolateCents(from, to, progress.value)
        to = cents
        progress.snapTo(0f)
        progress.animateTo(1f, tween(Motion.Emphasis, easing = Motion.Standard))
    }

    return remember(reducedMotion) {
        derivedStateOf { if (reducedMotion) latest else interpolateCents(from, to, progress.value) }
    }
}

/**
 * Money text that counts from its previous value to [cents] whenever [cents] changes. The text is
 * re-formatted every frame (so grouping and currency stay correct); semantics expose only the final
 * value ([format] is applied to it too). [autoSize] shrinks the text to fit one line for very large values or
 * font scales. Snaps
 * instead of animating under reduced motion.
 */
@Suppress("LongParameterList") // generic display component; optional styling knobs keep call sites terse
@Composable
fun AnimatedAmountText(
    cents: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    autoSize: TextAutoSize? = null,
    format: @Composable (Long) -> String = { formatAmountCents(it) },
) {
    val displayed by rememberAnimatedCents(cents)
    val finalText = format(cents)
    Text(
        text = format(displayed),
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        autoSize = autoSize,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(finalText) },
    )
}
