package com.joaoeoneves.fintrack.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [percentageOf], the plain (non-composable) helper that computes a category's
 * rounded whole-percent share of the total for the pie chart's TalkBack content descriptions.
 * Pure JVM function, no Compose/Android dependency required.
 */
class PieChartPercentageTest {
    @Test
    fun `normal case rounds to expected whole percent`() {
        assertEquals(45, percentageOf(amountCents = 4500, totalCents = 10000))
    }

    @Test
    fun `one third rounds down per roundToInt semantics`() {
        // 1 / 3 * 100 = 33.333...; Kotlin's roundToInt() rounds to the nearest int, so this
        // rounds down to 33.
        assertEquals(33, percentageOf(amountCents = 1, totalCents = 3))
    }

    @Test
    fun `two thirds rounds up per roundToInt semantics`() {
        // 2 / 3 * 100 = 66.666...; rounds up to 67.
        assertEquals(67, percentageOf(amountCents = 2, totalCents = 3))
    }

    @Test
    fun `exact half boundary rounds up per roundToInt half-up semantics`() {
        // 1 / 8 * 100 = 12.5 exactly. Double#roundToInt() delegates to Math.round(), which rounds
        // half-way values up (toward positive infinity), so this must be 13, not 12.
        assertEquals(13, percentageOf(amountCents = 1, totalCents = 8))
    }

    @Test
    fun `total zero returns zero without dividing by zero`() {
        assertEquals(0, percentageOf(amountCents = 500, totalCents = 0))
    }

    @Test
    fun `total zero with zero amount returns zero`() {
        assertEquals(0, percentageOf(amountCents = 0, totalCents = 0))
    }

    @Test
    fun `negative total returns zero via defensive guard`() {
        // Shouldn't occur in practice (totals are sums of non-negative amounts), but the `<= 0L`
        // guard must still hold and not attempt the division.
        assertEquals(0, percentageOf(amountCents = 500, totalCents = -1000))
    }

    @Test
    fun `amount equal to total returns one hundred percent`() {
        assertEquals(100, percentageOf(amountCents = 2500, totalCents = 2500))
    }

    @Test
    fun `zero amount with positive total returns zero percent`() {
        assertEquals(0, percentageOf(amountCents = 0, totalCents = 10000))
    }

    @Test
    fun `amount greater than total does not crash and returns rounded value over one hundred`() {
        // Shouldn't happen given how callers build totals (sum of the very slices being measured),
        // but confirm the function stays well-behaved (no crash, no clamping) rather than silently
        // producing garbage.
        assertEquals(150, percentageOf(amountCents = 1500, totalCents = 1000))
    }

    @Test
    fun `negative amount with positive total returns negative percent without crashing`() {
        // Also shouldn't happen in practice (amounts are always non-negative sums), but confirms
        // there's no unexpected crash/NaN for a stray negative input.
        assertEquals(-20, percentageOf(amountCents = -200, totalCents = 1000))
    }

    @Test
    fun `large cents values do not overflow before division`() {
        // Both operands converted to Double before dividing, so this should behave like the
        // normal case even for large (but realistic) monetary totals.
        assertEquals(25, percentageOf(amountCents = 250_000_00L, totalCents = 1_000_000_00L))
    }
}
