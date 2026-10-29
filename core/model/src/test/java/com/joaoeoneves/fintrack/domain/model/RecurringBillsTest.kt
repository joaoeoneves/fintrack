package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurringBillsTest {
    @Test
    fun billsOf_keepsOnlyExpenseItems() {
        val items =
            listOf(
                recurringItem("a"),
                recurringItem("inc", type = RecurringType.INCOME, amountCents = 1L),
            )

        assertEquals(listOf("a"), billsOf(items).map { it.id })
    }

    @Test
    fun billsOf_includesPlanOwnedItems() {
        val items = listOf(recurringItem("bill"), recurringItem("own"))

        assertEquals(listOf("bill", "own"), billsOf(items).map { it.id })
    }

    @Test
    fun billsOf_empty_isEmpty() {
        assertEquals(emptyList<RecurringItem>(), billsOf(emptyList()))
    }

    @Test
    fun monthlyEquivalent_roundsToNearest() {
        assertEquals(0L, monthlyEquivalentCents(0L))
        assertEquals(1_000L, monthlyEquivalentCents(12_000L))
        assertEquals(8L, monthlyEquivalentCents(100L))
        assertEquals(9L, monthlyEquivalentCents(103L))
        // Exactly half rounds up.
        assertEquals(1L, monthlyEquivalentCents(6L))
        assertEquals(0L, monthlyEquivalentCents(5L))
    }
}
