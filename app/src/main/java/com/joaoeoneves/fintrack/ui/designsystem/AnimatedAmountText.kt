package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import kotlin.math.roundToLong

private const val COUNT_UP_MS = 500

/** The value [fraction] (0..1) of the way from [from] to [to], in whole cents. */
fun interpolateCents(
    from: Long,
    to: Long,
    fraction: Float,
): Long = from + ((to - from).toDouble() * fraction.toDouble()).roundToLong()

/**
 * Money text that counts from its previous value to [cents] whenever [cents] changes. The text is
 * re-formatted every frame (so grouping and currency stay correct); semantics expose only the final
 * value. Snaps instead of animating under reduced motion.
 */
@Composable
fun AnimatedAmountText(
    cents: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(0f) }
    var from by remember { mutableLongStateOf(cents) }
    var to by remember { mutableLongStateOf(cents) }

    LaunchedEffect(cents) {
        if (cents == to) return@LaunchedEffect
        if (reducedMotion) {
            from = cents
            to = cents
            return@LaunchedEffect
        }
        from = interpolateCents(from, to, progress.value)
        to = cents
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COUNT_UP_MS, easing = FastOutSlowInEasing))
    }

    val displayed = interpolateCents(from, to, progress.value)
    val finalText = formatAmountCents(cents)
    Text(
        text = formatAmountCents(displayed),
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(finalText) },
    )
}
