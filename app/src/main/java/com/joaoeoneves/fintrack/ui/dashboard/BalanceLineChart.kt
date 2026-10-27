package com.joaoeoneves.fintrack.ui.dashboard

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ChartHeight = 140.dp
private val LabelBand = 24.dp
private val VerticalInset = 8.dp
private val StrokeWidth = 1.5.dp
private val DotRadius = 5.dp
private val DotHaloRadius = 9.dp
private const val SAMPLE_COUNT = 128
private const val DRAW_IN_MS = 600
private const val MORPH_MS = 450
private const val FADE_MS = 160
private const val FILL_ALPHA = 0.12f
private const val GUIDE_ALPHA = 0.35f
private const val HALO_ALPHA = 0.18f

/**
 * Frameless line chart of the cumulative net balance. [series] is one value per day starting at
 * [startDate]. The line draws in on first appearance and morphs when the series changes; a long
 * press followed by a drag scrubs a guide line across the days and reports the index through
 * [onScrub] (null when released), with a haptic tick for every day crossed.
 */
@Composable
fun BalanceLineChart(
    series: List<Long>,
    startDate: LocalDate,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val target = remember(series) { resample(normalize(series), SAMPLE_COUNT) }
    val morph = rememberMorph(target, reducedMotion)
    val drawIn = rememberDrawIn(reducedMotion)
    val scrub = rememberScrubState(count = series.size, onScrub = onScrub)
    val guideAlpha by animateFloatAsState(
        targetValue = if (scrub.active) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else tween(FADE_MS),
        label = "scrubGuide",
    )
    val description =
        stringResource(
            R.string.cd_balance_chart,
            formatAmountCents(series.first(), typographic = false),
            formatAmountCents(series.last(), typographic = false),
        )
    val labels = rememberScrubLabelFormatter()
    val amountText = if (scrub.index in series.indices) formatAmountCents(series[scrub.index]) else ""
    val measurer = rememberTextMeasurer()
    val colors = ChartColors(secondary = FinTrackTheme.colors.textSecondary, line = FinTrackTheme.colors.accentText)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics {
                    contentDescription = description
                    role = Role.Image
                }.scrubGestures(scrub),
    ) {
        val points = plotPoints(morph.values(), size.width, size.height, VerticalInset.toPx(), LabelBand.toPx())
        drawBalanceLine(points, colors.line, drawIn.value)
        if (guideAlpha > 0f && series.size > 1) {
            val label = labels(startDate.plusDays(scrub.index.toLong())) + "  " + amountText
            drawScrub(ScrubMark(scrub.index, series.size, guideAlpha, label, measurer), points, colors)
        }
    }
}

private class ChartColors(
    val secondary: Color,
    val line: Color,
)

/** Everything needed to draw the scrub guide for one frame. */
private class ScrubMark(
    val index: Int,
    val count: Int,
    val alpha: Float,
    val label: String,
    val measurer: TextMeasurer,
)

/** Observable scrub state shared between the gesture handler and the drawing. */
private class ScrubState(
    val count: Int,
    val onScrub: (Int?) -> Unit,
) {
    private var activeState by mutableStateOf(false)
    private val indexState = mutableIntStateOf(0)
    var width = 0f
    val active get() = activeState
    val index get() = indexState.intValue

    fun start(x: Float) {
        indexState.intValue = scrubIndex(x, width, count)
        activeState = true
        onScrub(index)
    }

    /** Returns whether the scrubbed day changed. */
    fun move(x: Float): Boolean {
        val next = scrubIndex(x, width, count)
        if (next == index) return false
        indexState.intValue = next
        onScrub(next)
        return true
    }

    fun end() {
        activeState = false
        onScrub(null)
    }
}

@Composable
private fun rememberScrubState(
    count: Int,
    onScrub: (Int?) -> Unit,
): ScrubState {
    val latestOnScrub by rememberUpdatedState(onScrub)
    return remember(count) { ScrubState(count) { latestOnScrub(it) } }
}

@Composable
private fun Modifier.scrubGestures(scrub: ScrubState): Modifier {
    val haptics = LocalHapticFeedback.current
    return pointerInput(scrub) {
        scrub.width = size.width.toFloat()
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                scrub.width = size.width.toFloat()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scrub.start(offset.x)
            },
            onDrag = { change, _ ->
                change.consume()
                if (scrub.move(change.position.x)) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            },
            onDragEnd = scrub::end,
            onDragCancel = scrub::end,
        )
    }
}

@Composable
private fun rememberScrubLabelFormatter(): (LocalDate) -> String {
    val locale = currentLocale()
    val formatter =
        remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale) }
    return { date -> formatter.format(date) }
}

/**
 * Blends from the previously shown samples to the current target. The retarget runs in an effect
 * (never during composition): the old picture is frozen as [from] and the new samples become [to]
 * only once the animation has been reset to 0, in the same effect step, so there is no jump.
 */
private class MorphState(
    initial: FloatArray,
) {
    val progress = Animatable(1f)
    var from by mutableStateOf(initial)
    var to by mutableStateOf(initial)

    fun values(): FloatArray {
        val fraction = progress.value
        val start = from
        val end = to
        return FloatArray(SAMPLE_COUNT) { i -> start[i] + (end[i] - start[i]) * fraction }
    }

    suspend fun retarget(
        target: FloatArray,
        animate: Boolean,
    ) {
        if (to === target) return
        val shown = values()
        // Freeze the picture currently on screen as the new origin, then restart from 0.
        from = shown
        to = target
        progress.snapTo(0f)
        if (animate) {
            progress.animateTo(1f, tween(MORPH_MS, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(1f)
        }
    }
}

@Composable
private fun rememberMorph(
    target: FloatArray,
    reducedMotion: Boolean,
): MorphState {
    val state = remember { MorphState(target) }
    LaunchedEffect(target) { state.retarget(target, animate = !reducedMotion) }
    return state
}

@Composable
private fun rememberDrawIn(reducedMotion: Boolean): Animatable<Float, *> {
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) progress.animateTo(1f, tween(DRAW_IN_MS, easing = FastOutSlowInEasing))
    }
    return progress
}

private fun plotPoints(
    samples: FloatArray,
    width: Float,
    height: Float,
    inset: Float,
    top: Float,
): List<Offset> {
    val plotHeight = height - top - inset * 2
    return samples.mapIndexed { i, v ->
        Offset(x = width * i / (samples.size - 1), y = top + inset + plotHeight * (1f - v))
    }
}

private fun DrawScope.drawBalanceLine(
    points: List<Offset>,
    color: Color,
    drawInProgress: Float,
) {
    val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    val fill =
        Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    clipRect(right = size.width * drawInProgress) {
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = FILL_ALPHA), Color.Transparent)),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = StrokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.drawScrub(
    mark: ScrubMark,
    points: List<Offset>,
    colors: ChartColors,
) {
    val alpha = mark.alpha
    val x = size.width * mark.index / (mark.count - 1)
    val y = interpolateY(points, x)
    drawLine(
        color = colors.line.copy(alpha = GUIDE_ALPHA * alpha),
        start = Offset(x, LabelBand.toPx()),
        end = Offset(x, size.height),
        strokeWidth = 1.dp.toPx(),
    )
    drawCircle(colors.line.copy(alpha = HALO_ALPHA * alpha), DotHaloRadius.toPx(), Offset(x, y))
    drawCircle(colors.line.copy(alpha = alpha), DotRadius.toPx(), Offset(x, y))
    val layout: TextLayoutResult = mark.measurer.measure(mark.label, SectionLabel.copy(color = colors.secondary))
    val left = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
    drawText(layout, topLeft = Offset(left, (LabelBand.toPx() - layout.size.height) / 2f), alpha = alpha)
}

private fun interpolateY(
    points: List<Offset>,
    x: Float,
): Float {
    val width = points.last().x
    val position = if (width <= 0f) 0f else x / width * (points.size - 1)
    val lower = position.toInt().coerceIn(0, points.size - 2)
    val fraction = position - lower
    return points[lower].y + (points[lower + 1].y - points[lower].y) * fraction
}
