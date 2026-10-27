package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.Income
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DayGroupingTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val kiritimati = ZoneId.of("Pacific/Kiritimati") // UTC+14

    private fun income(
        id: String,
        at: String,
    ) = Income(id = id, source = id, amountCents = 100L, date = Instant.parse(at), note = null)

    @Test
    fun emptyInput_givesNoSections() {
        assertTrue(groupByDay(emptyList<Income>(), lisbon).isEmpty())
    }

    @Test
    fun sameDayItems_shareOneSection() {
        val items = listOf(income("a", "2026-10-20T08:00:00Z"), income("b", "2026-10-20T20:00:00Z"))
        val sections = groupByDay(items, ZoneId.of("UTC"))
        assertEquals(1, sections.size)
        assertEquals(LocalDate.of(2026, 10, 20), sections[0].date)
        assertEquals(items, sections[0].items)
    }

    @Test
    fun newestFirst_orderPreserved() {
        val items =
            listOf(
                income("c", "2026-10-22T10:00:00Z"),
                income("b", "2026-10-21T10:00:00Z"),
                income("a", "2026-10-21T09:00:00Z"),
            )
        val sections = groupByDay(items, ZoneId.of("UTC"))
        assertEquals(listOf(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 21)), sections.map { it.date })
        assertEquals(listOf("b", "a"), sections[1].items.map { it.id })
    }

    @Test
    fun oldestFirst_orderPreserved() {
        val items =
            listOf(
                income("a", "2026-10-21T09:00:00Z"),
                income("b", "2026-10-21T10:00:00Z"),
                income("c", "2026-10-22T10:00:00Z"),
            )
        val sections = groupByDay(items, ZoneId.of("UTC"))
        assertEquals(listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22)), sections.map { it.date })
        assertEquals(listOf("a", "b"), sections[0].items.map { it.id })
    }

    @Test
    fun midnightBoundary_inLisbonSummerTime() {
        // Lisbon is UTC+1 in October-before-last-Sunday: 23:30Z is already 00:30 next day locally.
        val items = listOf(income("late", "2026-07-01T23:30:00Z"), income("early", "2026-07-01T22:30:00Z"))
        val sections = groupByDay(items, lisbon)
        assertEquals(listOf(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1)), sections.map { it.date })
    }

    @Test
    fun midnightBoundary_exactlyAtLocalMidnightStartsNewDay() {
        val items = listOf(income("b", "2026-07-01T23:00:00Z"), income("a", "2026-07-01T22:59:59Z"))
        val sections = groupByDay(items, lisbon)
        assertEquals(2, sections.size)
        assertEquals(LocalDate.of(2026, 7, 2), sections[0].date)
    }

    @Test
    fun largeOffsetZone_shiftsDateAhead() {
        val items = listOf(income("a", "2026-10-20T10:00:00Z"), income("b", "2026-10-20T09:00:00Z"))
        val sections = groupByDay(items, kiritimati)
        // 10:00Z = 00:00 on the 21st; 09:00Z = 23:00 on the 20th.
        assertEquals(listOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 20)), sections.map { it.date })
        assertEquals(1, groupByDay(items, ZoneId.of("UTC")).size)
    }

    @Test
    fun dayReappearingAfterOtherDay_startsNewSection() {
        val items =
            listOf(
                income("a", "2026-10-20T10:00:00Z"),
                income("b", "2026-10-21T10:00:00Z"),
                income("c", "2026-10-20T11:00:00Z"),
            )
        assertEquals(3, groupByDay(items, ZoneId.of("UTC")).size)
    }

    @Test
    fun dayHeader_todayYesterdayAndDate() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals(DayHeader.Today, dayHeaderOf(today, today))
        assertEquals(DayHeader.Yesterday, dayHeaderOf(today.minusDays(1), today))
        assertEquals(DayHeader.Date(today.minusDays(2), false), dayHeaderOf(today.minusDays(2), today))
    }

    @Test
    fun dayHeader_showYearOnlyWhenYearDiffers_acrossYearBoundary() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals(DayHeader.Yesterday, dayHeaderOf(LocalDate.of(2025, 12, 31), today))
        assertEquals(DayHeader.Date(LocalDate.of(2025, 12, 30), true), dayHeaderOf(LocalDate.of(2025, 12, 30), today))
        assertEquals(DayHeader.Date(LocalDate.of(2026, 3, 5), false), dayHeaderOf(LocalDate.of(2026, 3, 5), today))
    }

    @Test
    fun formatDayHeaderDate_english() {
        val date = LocalDate.of(2026, 10, 19)
        assertEquals("Monday, 19 October", formatDayHeaderDate(date, false, Locale.ENGLISH))
        assertEquals("19 October 2026", formatDayHeaderDate(date, true, Locale.ENGLISH))
    }

    @Test
    fun formatDayHeaderDate_portuguese() {
        val pt = Locale.forLanguageTag("pt-PT")
        val date = LocalDate.of(2026, 10, 19)
        assertEquals("segunda-feira, 19 de outubro", formatDayHeaderDate(date, false, pt))
        assertEquals("19 de outubro de 2026", formatDayHeaderDate(date, true, pt))
    }

    @Test
    fun formatDayHeaderDate_neverMatchesMonthPeriodLabelShape() {
        val periodLabel = Regex("[A-Z][a-z]+ [0-9]{4}")
        val locales = listOf(Locale.ENGLISH, Locale.US, Locale.forLanguageTag("pt-PT"), Locale.forLanguageTag("pt-BR"))
        var date = LocalDate.of(2024, 1, 1)
        while (date.year < 2028) {
            for (locale in locales) {
                for (showYear in listOf(true, false)) {
                    val text = formatDayHeaderDate(date, showYear, locale)
                    assertFalse("$text ($locale)", periodLabel.matches(text))
                }
            }
            date = date.plusDays(9)
        }
    }
}
