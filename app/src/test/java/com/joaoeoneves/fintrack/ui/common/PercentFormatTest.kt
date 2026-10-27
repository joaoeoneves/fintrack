package com.joaoeoneves.fintrack.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PercentFormatTest {
    private val ptPt: Locale = Locale.forLanguageTag("pt-PT")

    @Test
    fun english_hasNoSpaceBeforeSign() {
        assertEquals("25%", formatPercent(25, Locale.US))
    }

    @Test
    fun portuguese_hasDigitsAndSign() {
        val text = formatPercent(25, ptPt)
        assertTrue(text, text.startsWith("25"))
        assertTrue(text, text.endsWith("%"))
    }

    @Test
    fun zeroAndHundred() {
        assertEquals("0%", formatPercent(0, Locale.US))
        assertEquals("100%", formatPercent(100, Locale.US))
    }
}
