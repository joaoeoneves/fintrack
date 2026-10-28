package com.joaoeoneves.fintrack.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [sliceColorFor]: chart slices cycle through the palette and never use the trailing slate. */
class SliceColorForTest {
    private val slateKey = categoryColorKeys.last()
    private val cycle = categoryColorKeys.size - 1

    @Test
    fun firstSlice_isTheFirstPaletteColor() {
        assertEquals(categoryColorFor(categoryColorKeys.first(), false), sliceColorFor(0, isDarkTheme = false))
        assertEquals(categoryColorFor(categoryColorKeys.first(), true), sliceColorFor(0, isDarkTheme = true))
    }

    @Test
    fun slices_cycleAfterThePaletteWithoutSlate() {
        assertEquals(sliceColorFor(0, false), sliceColorFor(cycle, false))
        assertEquals(sliceColorFor(3, true), sliceColorFor(3 + 2 * cycle, true))
    }

    @Test
    fun slate_isNeverUsed() {
        val slate = categoryColorFor(slateKey, false)

        assertTrue((0 until cycle * 2).none { sliceColorFor(it, false) == slate })
    }

    @Test
    fun consecutiveSlices_differ() {
        assertTrue((0 until cycle).all { sliceColorFor(it, false) != sliceColorFor(it + 1, false) })
    }

    @Test
    fun negativeIndex_wrapsAroundInsteadOfThrowing() {
        assertEquals(sliceColorFor(cycle - 1, false), sliceColorFor(-1, false))
    }

    @Test
    fun darkAndLight_differ() {
        assertNotEquals(sliceColorFor(0, true), sliceColorFor(0, false))
    }
}
