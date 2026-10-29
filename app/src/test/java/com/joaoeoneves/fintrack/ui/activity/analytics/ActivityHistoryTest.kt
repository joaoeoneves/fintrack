package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.MonthTotals
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.model.YearToDate
import com.joaoeoneves.fintrack.domain.model.spendingComparisonOf
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ActivityHistoryTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val food = CategoryId("FOOD")
    private val leisure = CategoryId("LEISURE")
    private val oct = YearMonth.of(2026, 10)
    private val sep = YearMonth.of(2026, 9)

    /** 12 months Nov 2025 - Oct 2026. */
    private val window = (11 downTo 0).map { oct.minusMonths(it.toLong()) }

    private fun defaultSpending(index: Int): Long = (index + 1) * 100L

    private fun history(
        periodMonths: List<YearMonth>,
        calendar: Boolean = periodMonths.size == 1,
        spending: (Int) -> Long = ::defaultSpending,
        categories: Map<CategoryId, List<Long>> = emptyMap(),
    ) = ActivityHistory(
        periodMonths,
        calendar,
        MonthlyHistory(
            window.mapIndexed { i, m -> MonthTotals(m, 0L, spending(i), 0L) },
            categories,
            emptyList(),
            YearToDate(2026, 0L, 0L, 0L),
        ),
    )

    private val allSpending = window.indices.map { defaultSpending(it) }

    @Test
    fun periodMonthsOf_calendarMonths_areTheMonth() {
        assertEquals(listOf(oct), periodMonthsOf(Period.CurrentMonth, now, zone))
        assertEquals(listOf(sep), periodMonthsOf(Period.Month(sep), now, zone))
    }

    @Test
    fun periodMonthsOf_customRange_listsEveryOverlappedMonth_endBoundaryInclusive() {
        val custom = Period.Custom(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 1))
        assertEquals(listOf(YearMonth.of(2026, 8), sep, oct), periodMonthsOf(custom, now, zone))
        val oneDay = Period.Custom(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30))
        assertEquals(listOf(sep), periodMonthsOf(oneDay, now, zone))
    }

    @Test
    fun periodMonthsOf_rolling_endsAtTheCurrentMonth_oldestFirst() {
        val months = periodMonthsOf(Period.Rolling(TimeRange.THREE_MONTHS), now, zone)
        assertEquals(oct, months.last())
        assertEquals(months.sorted(), months)
        assertTrue(months.size in 3..4)
    }

    @Test
    fun comparison_onlyForACalendarMonth() {
        val calendar = history(listOf(oct))
        assertEquals(spendingComparisonOf(calendar.monthly.months, oct), calendar.comparison())
        assertNotNull(calendar.comparison())
        assertNull(history(listOf(oct), calendar = false).comparison())
        assertNull(history(listOf(sep, oct), calendar = true).comparison())
    }

    @Test
    fun monthlyAverage_overPeriodMonthsWithData_nullForOneMonthOrNoData() {
        assertEquals(1_150L, history(listOf(sep, oct)).monthlyAverageSpendingCents())
        assertNull(history(listOf(oct)).monthlyAverageSpendingCents())
        assertNull(history(listOf(sep, oct), spending = { 0L }).monthlyAverageSpendingCents())
        // A month without data does not drag the average down.
        val onlyOct = history(listOf(sep, oct), spending = { if (it == 11) 1_200L else 0L })
        assertEquals(1_200L, onlyOct.monthlyAverageSpendingCents())
    }

    @Test
    fun spendingSeries_totalWithoutSelection_categoryBeatsEnvelope() {
        val h = history(listOf(oct), categories = mapOf(food to List(12) { 10L }, leisure to List(12) { 1L }))
        assertEquals(allSpending, h.spendingSeries(null, null))
        assertEquals(List(12) { 10L }, h.spendingSeries(food, setOf(food, leisure)))
        assertEquals(List(12) { 11L }, h.spendingSeries(null, setOf(food, leisure)))
        assertEquals(List(12) { 0L }, h.spendingSeries(CategoryId("NONE"), null))
    }

    @Test
    fun evolution_windowIsAtLeastSix_highlightsThePeriod() {
        val short = history(listOf(sep, oct)).evolutionOf(allSpending)!!
        assertEquals(6, short.months.size)
        assertEquals(oct, short.months.last())
        assertEquals(setOf(4, 5), short.highlighted)
    }

    @Test
    fun evolution_longPeriod_windowIsThePeriodLength() {
        val period = (2..10).map { YearMonth.of(2026, it) }
        val evolution = history(period, calendar = false).evolutionOf(allSpending)!!
        assertEquals(9, evolution.months.size)
        assertEquals(9, evolution.valuesCents.size)
        assertEquals((0..8).toSet(), evolution.highlighted)
    }

    @Test
    fun evolution_nullWhenEveryValueIsZero() {
        assertNull(history(listOf(oct)).evolutionOf(List(12) { 0L }))
    }
}
