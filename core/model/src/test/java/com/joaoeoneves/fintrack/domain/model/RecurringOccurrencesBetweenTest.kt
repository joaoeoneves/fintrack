package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [occurrencesBetween]. Rent: monthly, day 15, from 2026-01-01. */
class RecurringOccurrencesBetweenTest {
    @Test
    fun bounds_areInclusive() {
        val dates = recurringItem().occurrencesBetween(day(2026, 2, 15), day(2026, 4, 15))

        assertEquals(listOf(day(2026, 2, 15), day(2026, 3, 15), day(2026, 4, 15)), dates)
    }

    @Test
    fun aRangeBetweenTwoOccurrences_isEmpty() {
        assertTrue(recurringItem().occurrencesBetween(day(2026, 2, 16), day(2026, 3, 14)).isEmpty())
    }

    @Test
    fun fromAfterThrough_isEmpty() {
        assertTrue(recurringItem().occurrencesBetween(day(2026, 3, 1), day(2026, 2, 1)).isEmpty())
    }

    @Test
    fun neverReturnsDatesBeforeTheFirstOccurrence() {
        val dates = recurringItem().occurrencesBetween(day(2025, 6, 1), day(2026, 2, 1))

        assertEquals(listOf(day(2026, 1, 15)), dates)
    }

    @Test
    fun stopsAtTheEndDate() {
        val item = recurringItem(endDate = day(2026, 3, 14))

        assertEquals(
            listOf(day(2026, 1, 15), day(2026, 2, 15)),
            item.occurrencesBetween(day(2026, 1, 1), day(2026, 6, 1)),
        )
    }

    @Test
    fun anOccurrenceOnTheEndDate_isIncluded() {
        val item = recurringItem(endDate = day(2026, 2, 15))

        assertEquals(
            listOf(day(2026, 1, 15), day(2026, 2, 15)),
            item.occurrencesBetween(day(2026, 1, 1), day(2026, 6, 1)),
        )
    }

    @Test
    fun yearly_givesOneDatePerYear() {
        val item = recurringItem(frequency = RecurringFrequency.YEARLY, startDate = day(2024, 1, 1))

        assertEquals(
            listOf(day(2025, 3, 15), day(2026, 3, 15)),
            item.occurrencesBetween(day(2025, 1, 1), day(2026, 12, 31)),
        )
    }

    @Test
    fun day31_isClampedToTheMonthLength() {
        val item = recurringItem(dayOfMonth = 31)

        assertEquals(
            listOf(day(2026, 1, 31), day(2026, 2, 28), day(2026, 3, 31), day(2026, 4, 30)),
            item.occurrencesBetween(day(2026, 1, 1), day(2026, 4, 30)),
        )
    }

    @Test
    fun day31_inALeapFebruary_isThe29th() {
        val item = recurringItem(dayOfMonth = 31, startDate = day(2028, 1, 1))

        assertEquals(listOf(day(2028, 2, 29)), item.occurrencesBetween(day(2028, 2, 1), day(2028, 2, 29)))
    }
}
