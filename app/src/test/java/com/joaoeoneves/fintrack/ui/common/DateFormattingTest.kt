package com.joaoeoneves.fintrack.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DateFormattingTest {
    private val ptPt: Locale = Locale.forLanguageTag("pt-PT")
    private val date = LocalDate.of(2026, 10, 27)

    @Test
    fun english_usesMonthNameBeforeDay() {
        val text = mediumDateFormatter(Locale.US).format(date)
        assertTrue(text, text.startsWith("Oct"))
        assertTrue(text, text.contains("27"))
        assertTrue(text, text.endsWith("2026"))
    }

    @Test
    fun portuguese_putsDayBeforeMonth() {
        val text = mediumDateFormatter(ptPt).format(date)
        assertTrue(text, text.startsWith("27"))
        assertTrue(text, text.endsWith("2026"))
    }

    @Test
    fun pattern_differsBetweenLocales() {
        assertTrue(mediumDatePattern(Locale.US) != mediumDatePattern(ptPt))
    }

    @Test
    fun formatter_usesGivenPattern() {
        assertEquals(
            mediumDateFormatter(Locale.US).format(date),
            java.time.format.DateTimeFormatter
                .ofPattern(mediumDatePattern(Locale.US), Locale.US)
                .format(date),
        )
    }
}
