package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** [duplicated]: what a copy keeps and what it resets. */
class ExpenseDuplicateTest {
    private val today = Instant.parse("2026-10-30T09:00:00Z")
    private val original =
        Expense(
            id = "e1",
            name = "Gym",
            amountCents = 2_990L,
            category = BuiltInCategories.SHOPPING,
            date = Instant.parse("2026-03-01T10:00:00Z"),
            note = "monthly",
            isRefund = true,
            paidWithMealCard = true,
            needsReview = true,
            refundOf = "orig",
            splitId = "s1",
        )

    @Test
    fun keepsContent_resetsIdDateLinksAndReview() {
        val copy = original.duplicated(today)

        assertEquals("", copy.id)
        assertEquals(today, copy.date)
        assertEquals("Gym", copy.name)
        assertEquals(2_990L, copy.amountCents)
        assertEquals(BuiltInCategories.SHOPPING, copy.category)
        assertEquals("monthly", copy.note)
        assertEquals(true, copy.isRefund)
        assertEquals(true, copy.paidWithMealCard)
        assertNull(copy.refundOf)
        assertNull(copy.splitId)
        assertFalse(copy.needsReview)
    }
}
