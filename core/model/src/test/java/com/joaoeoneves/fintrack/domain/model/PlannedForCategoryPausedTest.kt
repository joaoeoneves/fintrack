package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** [plannedForCategory] with skipped occurrences and items paused part-way through. */
class PlannedForCategoryPausedTest {
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    private fun planned(
        month: YearMonth,
        vararg items: RecurringItem,
    ) = plannedForCategory(month, items.toList())

    @Test
    fun skippedOccurrence_isExcluded_otherMonthsStillCount() {
        val item = recurringItem(amountCents = 700L, skipped = setOf(day(2026, 10, 15)))

        assertEquals(PlannedSource.NONE, planned(october, item).source)
        assertEquals(700L, planned(november, item).cents)
    }

    @Test
    fun skippedOccurrence_dropsOnlyThatItemFromTheSum() {
        val skipped = recurringItem(id = "a", amountCents = 700L, skipped = setOf(day(2026, 10, 15)))
        val other = recurringItem(id = "b", amountCents = 300L)

        assertEquals(300L, planned(october, skipped, other).cents)
    }

    @Test
    fun pausedAfterTheOccurrence_stillCountsThatMonth_butNotTheNext() {
        val item = recurringItem(amountCents = 500L, active = false, pausedAt = day(2026, 10, 20))

        assertEquals(500L, planned(october, item).cents)
        assertEquals(PlannedSource.NONE, planned(november, item).source)
    }

    @Test
    fun pausedBeforeTheOccurrence_doesNotCountThatMonth() {
        val item = recurringItem(amountCents = 500L, active = false, pausedAt = day(2026, 10, 10))

        assertEquals(PlannedSource.NONE, planned(october, item).source)
    }

    @Test
    fun pausedOnTheOccurrenceDay_doesNotCountIt() {
        val item = recurringItem(amountCents = 500L, active = false, pausedAt = day(2026, 10, 15))

        assertEquals(PlannedSource.NONE, planned(october, item).source)
    }

    @Test
    fun pausedWithoutADate_neverCounts() {
        val item = recurringItem(amountCents = 500L, active = false, pausedAt = null)

        assertEquals(PlannedSource.NONE, planned(october, item).source)
    }
}
