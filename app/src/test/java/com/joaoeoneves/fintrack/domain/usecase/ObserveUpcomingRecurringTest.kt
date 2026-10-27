package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ObserveUpcomingRecurring]: rows, annualized totals and the hasItems flag. */
class ObserveUpcomingRecurringTest {
    private val today = day(2026, 3, 15)

    private fun overview(vararg items: RecurringItem) =
        runBlocking { ObserveUpcomingRecurring(FakeRecurringRepository(items.toList()))(today).first() }

    @Test
    fun windowIsThirtyDays() {
        assertEquals(30L, UPCOMING_WINDOW_DAYS)
    }

    @Test
    fun noItems_emptyOverview() {
        assertEquals(RecurringOverview(emptyList(), 0L, 0L, hasItems = false), overview())
    }

    @Test
    fun onlyInactiveItem_hasItemsButNoRowsOrTotals() {
        val result = overview(recurringItem(active = false))

        assertTrue(result.hasItems)
        assertTrue(result.upcoming.isEmpty())
        assertEquals(0L, result.annualExpenseCents)
    }

    @Test
    fun totalsAreSplitByType_andRowsListDueItems() {
        val rent = recurringItem(id = "rent", amountCents = 1_000, handledThrough = day(2026, 2, 15))
        val insurance =
            recurringItem(id = "ins", name = "Insurance", amountCents = 6_000, frequency = RecurringFrequency.YEARLY)
        val salary = recurringItem(id = "sal", type = RecurringType.INCOME, amountCents = 5_000)

        val result = overview(rent, insurance, salary)

        assertEquals(18_000L, result.annualExpenseCents)
        assertEquals(60_000L, result.annualIncomeCents)
        assertTrue(result.hasItems)
        assertEquals(listOf("sal", "ins", "rent"), result.upcoming.map { it.item.id })
        assertEquals(listOf(true, true, true), result.upcoming.map { it.isDue })
    }
}
