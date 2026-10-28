package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [autoPostDueOccurrences]: eligibility, catch-up order, bounds and the 12-month lookback. */
class RecurringAutoPostTest {
    private fun d(
        y: Int,
        m: Int,
        day: Int,
    ) = LocalDate.of(y, m, day)

    @Suppress("LongParameterList") // Test fixture builder with defaults.
    private fun item(
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        startDate: LocalDate = d(2026, 1, 1),
        endDate: LocalDate? = null,
        handledThrough: LocalDate? = null,
        active: Boolean = true,
        autoPost: Boolean = true,
    ) = RecurringItem(
        id = "r1",
        type = RecurringType.EXPENSE,
        name = "Rent",
        amountCents = 50_000L,
        categoryId = BuiltInCategories.FOOD.id,
        note = null,
        frequency = frequency,
        dayOfMonth = 15,
        monthOfYear = if (frequency == RecurringFrequency.YEARLY) 3 else null,
        startDate = startDate,
        endDate = endDate,
        handledThrough = handledThrough,
        active = active,
        autoPost = autoPost,
    )

    @Test
    fun lookbackConstant_isTwelveMonths() {
        assertEquals(12L, AUTO_POST_LOOKBACK_MONTHS)
        assertFalse(item().copy(autoPost = false).autoPost)
    }

    @Test
    fun inactiveOrAutoPostOff_isEmpty() {
        val today = d(2026, 3, 20)

        assertTrue(item(active = false).autoPostDueOccurrences(today).isEmpty())
        assertTrue(item(autoPost = false).autoPostDueOccurrences(today).isEmpty())
    }

    @Test
    fun dueToday_isIncluded_dueTomorrow_isNot() {
        val handled = d(2026, 2, 15)

        assertEquals(listOf(d(2026, 3, 15)), item(handledThrough = handled).autoPostDueOccurrences(d(2026, 3, 15)))
        assertTrue(item(handledThrough = handled).autoPostDueOccurrences(d(2026, 3, 14)).isEmpty())
    }

    @Test
    fun severalMonthsBehind_areReturnedOldestFirst() {
        val result = item(handledThrough = d(2026, 1, 15)).autoPostDueOccurrences(d(2026, 4, 20))

        assertEquals(listOf(d(2026, 2, 15), d(2026, 3, 15), d(2026, 4, 15)), result)
    }

    @Test
    fun neverHandled_startsFromFirstOccurrenceOnOrAfterStart() {
        val result = item(startDate = d(2026, 2, 1)).autoPostDueOccurrences(d(2026, 3, 20))

        assertEquals(listOf(d(2026, 2, 15), d(2026, 3, 15)), result)
    }

    @Test
    fun endDate_boundsTheRun_inclusive() {
        val handled = d(2026, 1, 15)

        assertEquals(
            listOf(d(2026, 2, 15), d(2026, 3, 15)),
            item(handledThrough = handled, endDate = d(2026, 3, 15)).autoPostDueOccurrences(d(2026, 8, 1)),
        )
        assertEquals(
            listOf(d(2026, 2, 15)),
            item(handledThrough = handled, endDate = d(2026, 3, 14)).autoPostDueOccurrences(d(2026, 8, 1)),
        )
    }

    @Test
    fun yearly_postsEachDueYear() {
        val yearly =
            item(frequency = RecurringFrequency.YEARLY, startDate = d(2024, 1, 1), handledThrough = d(2024, 3, 15))

        assertEquals(listOf(d(2025, 3, 15), d(2026, 3, 15)), yearly.autoPostDueOccurrences(d(2026, 3, 15)))
        assertEquals(listOf(d(2025, 3, 15)), yearly.autoPostDueOccurrences(d(2026, 3, 14)))
    }

    private fun backlog(handledThrough: LocalDate) = item(startDate = d(2025, 1, 1), handledThrough = handledThrough)

    @Test
    fun backlogOlderThanTwelveMonths_isLeftManual() {
        val today = d(2026, 6, 20)

        // Oldest pending 2025-05-15 is before 2025-06-20: too old.
        assertTrue(backlog(d(2025, 4, 15)).autoPostDueOccurrences(today).isEmpty())
        // Oldest pending exactly on the lookback boundary is still posted (whole backlog, 13 occurrences).
        val boundary = backlog(d(2025, 5, 15)).autoPostDueOccurrences(d(2026, 6, 15))
        assertEquals(d(2025, 6, 15), boundary.first())
        assertEquals(13, boundary.size)
    }

    @Test
    fun alreadyHandled_isEmpty() {
        assertTrue(backlog(d(2026, 3, 15)).autoPostDueOccurrences(d(2026, 3, 20)).isEmpty())
        assertTrue(item(handledThrough = d(2026, 6, 15)).autoPostDueOccurrences(d(2026, 3, 20)).isEmpty())
    }

    @Test
    fun startInTheFuture_isEmpty() {
        assertTrue(item(startDate = d(2026, 5, 1)).autoPostDueOccurrences(d(2026, 3, 20)).isEmpty())
    }
}
