package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan-owned items (formerly savings-plan items) are ordinary bills now: listed and counted in the totals. */
class ObserveUpcomingRecurringPlanOwnedTest {
    private val today = day(2026, 3, 15)
    private val rent = recurringItem(id = "rent", amountCents = 1_000, handledThrough = day(2026, 2, 15))
    private val owned = recurringItem(id = "own", amountCents = 9_000, handledThrough = day(2026, 2, 15))

    @Test
    fun planOwnedItem_isListedAndCounted() =
        runBlocking {
            val result = ObserveUpcomingRecurring(FakeRecurringRepository(listOf(rent, owned)))(today).first()

            assertEquals(setOf("rent", "own"), result.upcoming.map { it.item.id }.toSet())
            assertEquals(120_000L, result.annualExpenseCents)
            assertTrue(result.hasItems)
        }
}
