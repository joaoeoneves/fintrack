package com.joaoeoneves.fintrack.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun zeroScale_isReducedMotion() {
        assertTrue(isReducedMotion(0f))
    }

    @Test
    fun nonZeroScales_areNotReducedMotion() {
        listOf(0.5f, 1f, 1.5f, 10f, 0.0001f).forEach { assertFalse("$it", isReducedMotion(it)) }
    }
}
