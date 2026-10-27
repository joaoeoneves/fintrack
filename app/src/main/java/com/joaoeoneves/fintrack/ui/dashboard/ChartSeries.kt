package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.Transaction
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Cumulative net balance (income minus outflow, refunds counting as money back) at the end of each
 * day of [range], from its first day up to the earlier of its last day and [today]; days after
 * today are not plotted. Always has at least two points so a line can be drawn: a single day is
 * returned as a flat segment.
 */
fun dailyNetSeries(
    expenses: List<Expense>,
    income: List<Income>,
    range: InstantRange,
    zone: ZoneId,
    today: LocalDate,
): List<Long> {
    val first = range.startInclusive.atZone(zone).toLocalDate()
    val lastOfRange =
        range.endExclusive
            .atZone(zone)
            .toLocalDate()
            .minusDays(1)
    val last = minOf(lastOfRange, today).let { if (it < first) first else it }
    val days = ChronoUnit.DAYS.between(first, last).toInt() + 1

    val flowPerDay = LongArray(days)

    fun add(transaction: Transaction) {
        val offset = ChronoUnit.DAYS.between(first, transaction.date.atZone(zone).toLocalDate())
        if (offset in 0 until days) flowPerDay[offset.toInt()] += transaction.cashFlowCents
    }
    expenses.forEach(::add)
    income.forEach(::add)

    var running = 0L
    val series = flowPerDay.map { flow -> (running + flow).also { running = it } }
    return if (series.size >= 2) series else listOf(series.first(), series.first())
}

/** Scales [values] to 0..1 (min to 0, max to 1); a flat series maps to the vertical middle. */
fun normalize(values: List<Long>): List<Float> {
    val min = values.minOrNull() ?: return emptyList()
    val max = values.max()
    val span = (max - min).toDouble()
    return values.map { if (span == 0.0) FLAT_LEVEL else ((it - min).toDouble() / span).toFloat() }
}

/** The index of the point nearest to horizontal position [x] in a chart [width] px wide with [count] points. */
fun scrubIndex(
    x: Float,
    width: Float,
    count: Int,
): Int {
    if (count <= 1 || width <= 0f) return 0
    return ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/**
 * Resamples [values] to [count] evenly spaced points by linear interpolation, so two series of
 * different lengths can be blended point by point.
 */
fun resample(
    values: List<Float>,
    count: Int,
): FloatArray {
    if (values.size < 2) return FloatArray(count) { values.firstOrNull() ?: FLAT_LEVEL }
    return FloatArray(count) { i ->
        val position = i.toFloat() / (count - 1) * (values.size - 1)
        val lower = position.toInt().coerceAtMost(values.size - 2)
        val fraction = position - lower
        values[lower] + (values[lower + 1] - values[lower]) * fraction
    }
}

private const val FLAT_LEVEL = 0.5f
