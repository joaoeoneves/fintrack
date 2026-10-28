package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [upcomingOf]: which items show a row, its date/overdue/due fields, window edges and ordering. */
class RecurringScheduleUpcomingTest {
    private val today = day(2026, 3, 15)
    private val item = recurringItem(dayOfMonth = 15, startDate = day(2026, 1, 1), handledThrough = day(2026, 2, 15))

    private fun upcoming(vararg items: RecurringItem) = upcomingOf(items.toList(), today, 30)

    @Test
    fun empty_noRows() {
        assertTrue(upcoming().isEmpty())
    }

    @Test
    fun inactiveItems_areExcluded() {
        assertTrue(upcoming(item.copy(active = false)).isEmpty())
    }

    @Test
    fun dueToday_isDue_tomorrowIsNot() {
        val dueToday = upcoming(item).single()
        val tomorrow = upcoming(item.copy(dayOfMonth = 16, handledThrough = day(2026, 2, 16))).single()

        assertEquals(day(2026, 3, 15), dueToday.date)
        assertTrue(dueToday.isDue)
        assertEquals(day(2026, 3, 16), tomorrow.date)
        assertFalse(tomorrow.isDue)
    }

    @Test
    fun window_exactlyThirtyDaysIncluded_thirtyOneExcluded() {
        val atEdge = item.copy(handledThrough = day(2026, 3, 15), dayOfMonth = 14)
        // Next occurrence after the cursor is 2026-04-14 (30 days out); 04-15 would be 31.
        assertEquals(day(2026, 4, 14), upcoming(atEdge).single().date)
        assertTrue(upcoming(atEdge.copy(dayOfMonth = 15)).isEmpty())
    }

    @Test
    fun oneRowPerItem_withTheOverdueCountBeyondTheOldest() {
        val behind = item.copy(handledThrough = day(2025, 12, 31))

        val row = upcoming(behind).single()

        assertEquals(day(2026, 1, 15), row.date)
        assertEquals(2, row.overdueCount)
        assertTrue(row.isDue)
    }

    @Test
    fun endedItem_stillShowsWhileAnOccurrenceUpToTheEndDateIsUnhandled() {
        val ended = item.copy(endDate = day(2026, 3, 15), handledThrough = day(2026, 1, 15))

        val row = upcoming(ended).single()

        assertEquals(day(2026, 2, 15), row.date)
        assertEquals(1, row.overdueCount)
    }

    @Test
    fun endedItem_fullyHandled_showsNothing() {
        assertTrue(upcoming(item.copy(endDate = day(2026, 2, 20))).isEmpty())
        assertTrue(upcoming(item.copy(endDate = day(2026, 3, 15), handledThrough = day(2026, 3, 15))).isEmpty())
    }

    @Test
    fun rows_sortedByDateThenName() {
        val b = item.copy(id = "b", name = "B", dayOfMonth = 20)
        val a = item.copy(id = "a", name = "A", dayOfMonth = 20)
        val early = item.copy(id = "e", name = "Z", dayOfMonth = 16)

        assertEquals(listOf("e", "a", "b"), upcoming(b, a, early).map { it.item.id })
    }

    @Test
    fun yearlyItem_onlyShowsWhenWithinTheWindow() {
        val yearly =
            recurringItem(
                frequency = RecurringFrequency.YEARLY,
                dayOfMonth = 1,
                monthOfYear = 4,
                startDate = day(2026, 1, 1),
            )

        assertEquals(day(2026, 4, 1), upcoming(yearly).single().date)
        assertTrue(upcoming(yearly.copy(monthOfYear = 5)).isEmpty())
    }
}
