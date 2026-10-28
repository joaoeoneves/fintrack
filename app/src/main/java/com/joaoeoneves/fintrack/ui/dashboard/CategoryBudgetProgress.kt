package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import kotlin.math.roundToInt

/** How much of the limit is used, as a 0..1 bar fill: 0 without a (positive) limit, capped at 1 when over. */
fun CategoryBudget.progressFraction(): Float {
    val limit = planned?.takeIf { it > 0L } ?: return 0f
    return (spentCents.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** Whole-number percent of the limit spent, not capped (130 when 30% over); 0 without a (positive) limit. */
fun CategoryBudget.percentUsed(): Int {
    val limit = planned?.takeIf { it > 0L } ?: return 0
    return (spentCents.toDouble() / limit.toDouble() * PERCENT_SCALE).roundToInt()
}

private const val PERCENT_SCALE = 100
