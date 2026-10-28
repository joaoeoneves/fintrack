package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [lastOccurrenceBefore] (used when resuming a paused item) and [isEnded]. */
class RecurringScheduleBoundsTest {
    private val item = recurringItem(dayOfMonth = 15, startDate = day(2026, 1, 1))

    @Test
    fun lastOccurrenceBefore_isStrictlyBeforeToday() {
        assertNull(item.lastOccurrenceBefore(day(2026, 1, 15)))
        assertEquals(day(2026, 1, 15), item.lastOccurrenceBefore(day(2026, 1, 16)))
        assertEquals(day(2026, 2, 15), item.lastOccurrenceBefore(day(2026, 3, 15)))
        assertEquals(day(2026, 3, 15), item.lastOccurrenceBefore(day(2026, 3, 16)))
    }

    @Test
    fun lastOccurrenceBefore_isCappedAtTheEndDate() {
        assertEquals(day(2026, 2, 15), item.copy(endDate = day(2026, 2, 20)).lastOccurrenceBefore(day(2026, 6, 1)))
        assertEquals(day(2026, 1, 15), item.copy(endDate = day(2026, 2, 10)).lastOccurrenceBefore(day(2026, 6, 1)))
    }

    @Test
    fun isEnded_onlyAfterTheEndDate() {
        assertFalse(item.isEnded(day(2030, 1, 1)))
        assertFalse(item.copy(endDate = day(2026, 5, 1)).isEnded(day(2026, 5, 1)))
        assertTrue(item.copy(endDate = day(2026, 5, 1)).isEnded(day(2026, 5, 2)))
    }

    @Test
    fun lastOccurrenceBefore_yearly() {
        val yearly = recurringItem(frequency = RecurringFrequency.YEARLY, dayOfMonth = 10, monthOfYear = 3)

        assertNull(yearly.lastOccurrenceBefore(day(2026, 3, 10)))
        assertEquals(day(2026, 3, 10), yearly.lastOccurrenceBefore(day(2027, 3, 10)))
        assertEquals(day(2027, 3, 10), yearly.lastOccurrenceBefore(day(2027, 3, 11)))
    }
}
