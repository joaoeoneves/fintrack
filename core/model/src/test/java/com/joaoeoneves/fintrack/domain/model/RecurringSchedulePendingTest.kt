package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [firstPendingOccurrence] and [overdueCount]. */
class RecurringSchedulePendingTest {
    private val item = recurringItem(dayOfMonth = 15, startDate = day(2026, 1, 1))

    @Test
    fun pending_noCursor_isTheFirstOccurrence() {
        assertEquals(day(2026, 1, 15), item.firstPendingOccurrence())
    }

    @Test
    fun pending_cursorOnAnOccurrence_isTheNextOne() {
        assertEquals(day(2026, 4, 15), item.copy(handledThrough = day(2026, 3, 15)).firstPendingOccurrence())
    }

    @Test
    fun pending_cursorBetweenOccurrences_isTheNextOne() {
        assertEquals(day(2026, 4, 15), item.copy(handledThrough = day(2026, 3, 20)).firstPendingOccurrence())
        assertEquals(day(2026, 3, 15), item.copy(handledThrough = day(2026, 3, 10)).firstPendingOccurrence())
    }

    @Test
    fun pending_cursorBeforeTheFirstOccurrence_isTheFirst() {
        assertEquals(day(2026, 1, 15), item.copy(handledThrough = day(2025, 12, 1)).firstPendingOccurrence())
    }

    @Test
    fun pending_endDateIsInclusive() {
        val last = item.copy(endDate = day(2026, 3, 15), handledThrough = day(2026, 2, 15))

        assertEquals(day(2026, 3, 15), last.firstPendingOccurrence())
        assertNull(last.copy(endDate = day(2026, 3, 14)).firstPendingOccurrence())
        assertNull(last.copy(handledThrough = day(2026, 3, 15)).firstPendingOccurrence())
    }

    @Test
    fun overdueCount_startTenYearsAgo_countsByArithmetic() {
        val old = recurringItem(dayOfMonth = 15, startDate = day(2016, 10, 27))

        assertEquals(day(2016, 11, 15), old.firstPendingOccurrence())
        // Nov 2016 .. Oct 2026 is 120 occurrences; the oldest is the row, the rest are "overdue".
        assertEquals(119, old.overdueCount(day(2026, 10, 27)))
        assertEquals(119, old.overdueCount(day(2026, 10, 15)))
        assertEquals(118, old.overdueCount(day(2026, 10, 14)))
    }

    @Test
    fun overdueCount_zeroWhenOldestNotDueOrNone() {
        assertEquals(0, item.overdueCount(day(2026, 1, 14)))
        assertEquals(0, item.overdueCount(day(2026, 1, 15)))
        assertEquals(1, item.overdueCount(day(2026, 2, 15)))
        assertEquals(0, item.copy(endDate = day(2026, 1, 1)).overdueCount(day(2026, 6, 1)))
    }

    @Test
    fun overdueCount_isCappedAtTheEndDateAndHonoursTheCursor() {
        assertEquals(2, item.copy(endDate = day(2026, 3, 15)).overdueCount(day(2030, 1, 1)))
        assertEquals(1, item.copy(endDate = day(2026, 3, 14)).overdueCount(day(2030, 1, 1)))
        assertEquals(1, item.copy(handledThrough = day(2026, 8, 15)).overdueCount(day(2026, 10, 15)))
    }

    @Test
    fun overdueCount_day31AcrossAShortMonth() {
        val day31 = recurringItem(dayOfMonth = 31, startDate = day(2026, 1, 1))

        assertEquals(1, day31.overdueCount(day(2026, 3, 30)))
        assertEquals(2, day31.overdueCount(day(2026, 3, 31)))
    }
}
