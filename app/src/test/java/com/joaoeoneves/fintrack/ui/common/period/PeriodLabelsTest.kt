package com.joaoeoneves.fintrack.ui.common.period

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Pure JVM tests for the non-Composable period label formatters. */
class PeriodLabelsTest {
    private val ptPt: Locale = Locale.forLanguageTag("pt-PT")

    @Test
    fun formatMonthLabel_english() {
        assertEquals("October 2026", formatMonthLabel(YearMonth.of(2026, 10), Locale.US))
        assertEquals("January 2027", formatMonthLabel(YearMonth.of(2027, 1), Locale.US))
    }

    @Test
    fun formatMonthLabel_portuguese_titlecasesFirstLetter() {
        assertEquals("Outubro 2026", formatMonthLabel(YearMonth.of(2026, 10), ptPt))
        assertEquals("Fevereiro 2028", formatMonthLabel(YearMonth.of(2028, 2), ptPt))
    }

    @Test
    fun formatMonthLabel_everyMonth_startsUppercase_andEndsWithYear() {
        for (month in 1..12) {
            for (locale in listOf(Locale.US, ptPt)) {
                val label = formatMonthLabel(YearMonth.of(2026, month), locale)
                assertTrue(label, label.first().isUpperCase())
                assertTrue(label, label.endsWith(" 2026"))
            }
        }
    }

    @Test
    fun formatShortMonthName_english_andPortuguese() {
        assertEquals("Oct", formatShortMonthName(YearMonth.of(2026, 10), Locale.US))
        val pt = formatShortMonthName(YearMonth.of(2026, 10), ptPt)
        assertTrue(pt, pt.startsWith("Out"))
    }

    @Test
    fun formatCustomRangeLabel_english() {
        assertEquals(
            "Oct 1, 2026 – Oct 15, 2026",
            formatCustomRangeLabel(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), Locale.US),
        )
    }

    @Test
    fun formatCustomRangeLabel_singleDay_andAcrossYears() {
        assertEquals(
            "Oct 5, 2026 – Oct 5, 2026",
            formatCustomRangeLabel(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), Locale.US),
        )
        assertEquals(
            "Dec 31, 2025 – Jan 1, 2026",
            formatCustomRangeLabel(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1), Locale.US),
        )
    }
}
