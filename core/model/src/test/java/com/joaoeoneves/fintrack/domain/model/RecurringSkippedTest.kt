package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skipped occurrences are never pending, overdue or auto-posted. Rent: monthly, day 15, from 2026-01-01. */
class RecurringSkippedTest {
    private val jan15 = day(2026, 1, 15)
    private val feb15 = day(2026, 2, 15)
    private val mar15 = day(2026, 3, 15)
    private val apr15 = day(2026, 4, 15)

    @Test
    fun firstPending_skipsASkippedDate() {
        val item = recurringItem(handledThrough = jan15, skipped = setOf(feb15))

        assertEquals(mar15, item.firstPendingOccurrence())
    }

    @Test
    fun firstPending_skipsARunOfConsecutiveSkippedDates() {
        val item = recurringItem(handledThrough = jan15, skipped = setOf(feb15, mar15, apr15))

        assertEquals(day(2026, 5, 15), item.firstPendingOccurrence())
    }

    @Test
    fun firstPending_withoutCursor_skipsTheFirstDate() {
        assertEquals(feb15, recurringItem(skipped = setOf(jan15)).firstPendingOccurrence())
    }

    @Test
    fun firstPending_isNullWhenEverythingUpToTheEndDateIsSkipped() {
        val item = recurringItem(handledThrough = jan15, endDate = day(2026, 3, 31), skipped = setOf(feb15, mar15))

        assertNull(item.firstPendingOccurrence())
    }

    @Test
    fun aSkippedDatePastTheEndDate_changesNothing() {
        val item = recurringItem(handledThrough = jan15, endDate = day(2026, 2, 28), skipped = setOf(mar15))

        assertEquals(feb15, item.firstPendingOccurrence())
    }

    @Test
    fun skippedDatesThatAreNotOccurrences_areIgnored() {
        val item = recurringItem(handledThrough = jan15, skipped = setOf(day(2026, 2, 14)))

        assertEquals(feb15, item.firstPendingOccurrence())
    }

    @Test
    fun overdueCount_doesNotCountSkippedDates() {
        val today = day(2026, 4, 20)

        assertEquals(2, recurringItem(handledThrough = jan15).overdueCount(today))
        assertEquals(1, recurringItem(handledThrough = jan15, skipped = setOf(mar15)).overdueCount(today))
        assertEquals(0, recurringItem(handledThrough = jan15, skipped = setOf(feb15, mar15, apr15)).overdueCount(today))
    }

    @Test
    fun overdueCount_whenTheOldestPendingIsAfterASkippedRun() {
        val item = recurringItem(handledThrough = jan15, skipped = setOf(feb15, mar15))

        assertEquals(0, item.overdueCount(day(2026, 4, 20)))
        assertEquals(1, item.copy(skipped = setOf(feb15)).overdueCount(day(2026, 4, 20)))
    }

    @Test
    fun upcomingOf_listsTheFirstNonSkippedOccurrence() {
        val item = recurringItem(handledThrough = jan15, skipped = setOf(feb15))

        val upcoming = upcomingOf(listOf(item), day(2026, 3, 20), windowDays = 7).single()

        assertEquals(mar15, upcoming.date)
        assertEquals(0, upcoming.overdueCount)
        assertTrue(upcoming.isDue)
    }

    @Test
    fun upcomingOf_hidesAnItemWhoseOnlyDueDateIsSkipped() {
        val item = recurringItem(handledThrough = feb15, skipped = setOf(mar15))

        assertTrue(upcomingOf(listOf(item), day(2026, 3, 20), windowDays = 7).isEmpty())
    }

    @Test
    fun autoPostDue_excludesSkippedDates() {
        val item = recurringItem(handledThrough = jan15, autoPost = true, skipped = setOf(feb15))

        assertEquals(listOf(mar15), item.autoPostDueOccurrences(day(2026, 3, 20)))
    }

    @Test
    fun autoPostDue_isEmptyWhenAllDueDatesAreSkipped() {
        val item = recurringItem(handledThrough = jan15, autoPost = true, skipped = setOf(feb15, mar15))

        assertTrue(item.autoPostDueOccurrences(day(2026, 3, 20)).isEmpty())
    }
}
