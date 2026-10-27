package com.joaoeoneves.fintrack.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class InterpolateCentsTest {
    @Test
    fun endpointsAreExact() {
        assertEquals(1_234L, interpolateCents(1_234L, 987_654_321L, 0f))
        assertEquals(987_654_321L, interpolateCents(1_234L, 987_654_321L, 1f))
    }

    @Test
    fun midpoint_isHalfway() {
        assertEquals(500L, interpolateCents(0L, 1_000L, 0.5f))
    }

    @Test
    fun roundsToNearestCent() {
        assertEquals(3L, interpolateCents(0L, 10L, 0.25f)) // 2.5 rounds up
        assertEquals(3L, interpolateCents(0L, 10L, 0.3f))
        assertEquals(2L, interpolateCents(0L, 10L, 0.2f))
    }

    @Test
    fun negativeRange_interpolates() {
        assertEquals(-500L, interpolateCents(0L, -1_000L, 0.5f))
        assertEquals(-1_500L, interpolateCents(-1_000L, -2_000L, 0.5f))
        assertEquals(0L, interpolateCents(-1_000L, 1_000L, 0.5f))
    }

    @Test
    fun fromGreaterThanTo_countsDown() {
        assertEquals(750L, interpolateCents(1_000L, 0L, 0.25f))
        assertEquals(0L, interpolateCents(1_000L, 0L, 1f))
    }

    @Test
    fun sameValue_isConstant() {
        assertEquals(42L, interpolateCents(42L, 42L, 0.7f))
    }
}
