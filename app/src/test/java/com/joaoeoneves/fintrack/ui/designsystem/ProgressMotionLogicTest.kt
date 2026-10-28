package com.joaoeoneves.fintrack.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMotionLogicTest {
    @Test
    fun shouldPulse_fullTruthTable() {
        for (wasOver in listOf(false, true)) {
            for (isOver in listOf(false, true)) {
                for (reduced in listOf(false, true)) {
                    val expected = !reduced && !wasOver && isOver
                    assertEquals(
                        "was=$wasOver is=$isOver reduced=$reduced",
                        expected,
                        shouldPulse(wasOver, isOver, reduced),
                    )
                }
            }
        }
    }

    @Test
    fun shouldPulse_reducedIsAlwaysFalse() {
        assertFalse(shouldPulse(wasOver = false, isOver = true, reduced = true))
    }

    @Test
    fun crossedGoal_nullPrevious_isFalse() = assertFalse(crossedGoal(null, 1f))

    @Test
    fun crossedGoal_reachingOne_isTrue() = assertTrue(crossedGoal(0.9f, 1.0f))

    @Test
    fun crossedGoal_alreadyComplete_isFalse() = assertFalse(crossedGoal(1.0f, 1.0f))

    @Test
    fun crossedGoal_dropping_isFalse() = assertFalse(crossedGoal(1.0f, 0.8f))

    @Test
    fun crossedGoal_stillBelow_isFalse() = assertFalse(crossedGoal(0.5f, 0.7f))

    @Test
    fun crossedGoal_overshoot_isTrue() = assertTrue(crossedGoal(0.99f, 1.2f))

    @Test
    fun interpolateCents_zeroSpanIsConstantAtAnyFraction() {
        assertEquals(0L, interpolateCents(0L, 0L, 0.5f))
        assertEquals(-7L, interpolateCents(-7L, -7L, 1f))
    }
}
