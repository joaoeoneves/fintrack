package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** [firstScheduled] and [occurrence]: day clamping without drift and the "counts from" start date. */
class RecurringScheduleOccurrenceTest {
    private val monthly31 = recurringItem(dayOfMonth = 31, startDate = day(2026, 1, 1))
    private val yearlyFeb29 =
        recurringItem(frequency = RecurringFrequency.YEARLY, dayOfMonth = 29, monthOfYear = 2)

    @Test
    fun monthlyDay31_clampsToMonthLengthWithoutDrift() {
        assertEquals(day(2026, 1, 31), monthly31.occurrence(0))
        assertEquals(day(2026, 2, 28), monthly31.occurrence(1))
        assertEquals(day(2026, 3, 31), monthly31.occurrence(2))
        assertEquals(day(2026, 4, 30), monthly31.occurrence(3))
    }

    @Test
    fun monthlyDay31_leapFebruaryIs29() {
        assertEquals(day(2028, 2, 29), monthly31.copy(startDate = day(2028, 1, 1)).occurrence(1))
    }

    @Test
    fun yearlyFeb29_clampsInNonLeapYears() {
        assertEquals(day(2026, 2, 28), yearlyFeb29.occurrence(0))
        assertEquals(day(2027, 2, 28), yearlyFeb29.occurrence(1))
        assertEquals(day(2028, 2, 29), yearlyFeb29.occurrence(2))
        assertEquals(day(2029, 2, 28), yearlyFeb29.occurrence(3))
    }

    @Test
    fun yearlyFeb29_startingInALeapYear_firstIsFeb29() {
        assertEquals(day(2028, 2, 29), yearlyFeb29.copy(startDate = day(2028, 1, 1)).firstScheduled())
    }

    @Test
    fun monthly_startAfterTheDayInItsMonth_firstIsNextMonth() {
        val item = recurringItem(dayOfMonth = 5, startDate = day(2026, 3, 10))

        assertEquals(day(2026, 4, 5), item.firstScheduled())
    }

    @Test
    fun monthly_startOnOrBeforeTheDay_firstIsThatMonth() {
        assertEquals(day(2026, 3, 5), recurringItem(dayOfMonth = 5, startDate = day(2026, 3, 5)).firstScheduled())
        assertEquals(day(2026, 3, 5), recurringItem(dayOfMonth = 5, startDate = day(2026, 3, 1)).firstScheduled())
    }

    @Test
    fun monthly_startDateInDecember_rollsIntoNextYear() {
        val item = recurringItem(dayOfMonth = 5, startDate = day(2026, 12, 20))

        assertEquals(day(2027, 1, 5), item.firstScheduled())
        assertEquals(day(2027, 2, 5), item.occurrence(1))
    }

    @Test
    fun monthlyDay31_startLateInShortMonth_firstIsClampedMonthEnd() {
        assertEquals(day(2026, 2, 28), monthly31.copy(startDate = day(2026, 2, 28)).firstScheduled())
        assertEquals(day(2026, 3, 31), monthly31.copy(startDate = day(2026, 3, 1)).firstScheduled())
    }

    @Test
    fun yearly_startAfterTheBillingDate_firstIsNextYear() {
        val item = recurringItem(frequency = RecurringFrequency.YEARLY, dayOfMonth = 10, monthOfYear = 3)

        assertEquals(day(2026, 3, 10), item.copy(startDate = day(2026, 1, 15)).firstScheduled())
        assertEquals(day(2026, 3, 10), item.copy(startDate = day(2026, 3, 10)).firstScheduled())
        assertEquals(day(2027, 3, 10), item.copy(startDate = day(2026, 3, 11)).firstScheduled())
        assertEquals(day(2027, 3, 10), item.copy(startDate = day(2026, 12, 1)).firstScheduled())
    }

    @Test
    fun yearly_startInAnEarlierMonthOfTheSameYear_isNeverBeforeStart() {
        val item =
            recurringItem(
                frequency = RecurringFrequency.YEARLY,
                dayOfMonth = 1,
                monthOfYear = 1,
                startDate = day(2026, 1, 2),
            )

        assertEquals(day(2027, 1, 1), item.firstScheduled())
        assertEquals(day(2028, 1, 1), item.occurrence(1))
    }
}
