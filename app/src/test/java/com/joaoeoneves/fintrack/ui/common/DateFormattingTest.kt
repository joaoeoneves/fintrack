package com.joaoeoneves.fintrack.ui.common

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DateFormattingTest {
    private val date = LocalDate.of(2026, 10, 7)
    private val ptPT = Locale.forLanguageTag("pt-PT")

    @Test
    fun shortDate_portuguese_isDayMonthYearNumeric() {
        assertEquals("07/10/2026", formatShortDate(date, ptPT).replace(Regex("\\b0?7\\b"), "07"))
    }

    @Test
    fun shortDate_english_hasMonthNameDayAndYear() {
        val text = formatShortDate(date, Locale.ENGLISH)

        assertTrue(text, text.contains("Oct") && text.contains("7") && text.endsWith("2026"))
    }

    @Test
    fun shortDate_followsTheLocaleOrder() {
        assertTrue(formatShortDate(date, ptPT).indexOf("7") < formatShortDate(date, ptPT).indexOf("2026"))
        val english = formatShortDate(date, Locale.ENGLISH)
        assertTrue(english.indexOf("Oct") < english.indexOf("2026"))
    }

    @Test
    fun dayMonth_english_hasNoYear() {
        val text = formatDayMonth(LocalDate.of(2026, 10, 30), Locale.ENGLISH)

        assertTrue(text, text.contains("Oct") && text.contains("30"))
        assertTrue(text, !text.contains("2026"))
    }

    @Test
    fun dayMonth_portuguese_putsTheDayFirst() {
        val text = formatDayMonth(LocalDate.of(2026, 10, 30), ptPT)

        assertTrue(text, text.startsWith("30") && text.contains(Regex("out|\\/10")))
        assertTrue(text, !text.contains("2026"))
    }

    @Test
    fun dayMonth_differsBetweenLanguages() {
        val may = LocalDate.of(2026, 5, 1)

        assertTrue(formatDayMonth(may, Locale.ENGLISH) != formatDayMonth(may, ptPT))
    }
}
