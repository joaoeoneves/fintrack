package com.joaoeoneves.fintrack.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Pure JVM tests for [formatSavingsRate]; non-breaking spaces are normalized before comparing. */
class FormatSavingsRateTest {
    private val ptPt: Locale = Locale.forLanguageTag("pt-PT")

    private fun format(
        rate: Double,
        locale: Locale,
    ) = formatSavingsRate(rate, locale).replace(' ', ' ').replace(' ', ' ')

    @Test
    fun english_quarter_is25Percent() {
        assertEquals("25%", format(0.25, Locale.US))
    }

    @Test
    fun portuguese_quarter_is25Percent() {
        assertEquals("25%", format(0.25, ptPt))
    }

    @Test
    fun zeroFractionDigits_roundsToWholePercent() {
        assertEquals("25%", format(0.2549, Locale.US))
        assertEquals("26%", format(0.2551, Locale.US))
    }

    @Test
    fun zero_andAboveOneHundredPercent() {
        assertEquals("0%", format(0.0, Locale.US))
        assertEquals("150%", format(1.5, Locale.US))
    }

    @Test
    fun negativeRate_isFormatted() {
        assertEquals("-10%", format(-0.1, Locale.US))
    }
}
