package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

/** [splitExpenses]: sibling records that sum to the original and share the split id. */
class ExpenseSplitTest {
    private val base =
        Expense(
            id = "orig",
            name = "Supermarket",
            amountCents = 10_000L,
            category = BuiltInCategories.FOOD,
            date = Instant.parse("2026-10-05T18:30:00Z"),
            note = "weekly",
            paidWithMealCard = true,
            refundOf = "zzz",
        )

    private fun parts(vararg cents: Long) = cents.map { SplitPart(BuiltInCategories.SHOPPING, it) }

    @Test
    fun split_keepsSharedFields_assignsCategoryAmountAndSplitId() {
        val result =
            splitExpenses(
                base,
                listOf(SplitPart(BuiltInCategories.FOOD, 6_000L), SplitPart(BuiltInCategories.SHOPPING, 4_000L)),
                "s1",
            )

        assertEquals(listOf(6_000L, 4_000L), result.map { it.amountCents })
        assertEquals(listOf(BuiltInCategories.FOOD, BuiltInCategories.SHOPPING), result.map { it.category })
        result.forEach {
            assertEquals("", it.id)
            assertEquals("s1", it.splitId)
            assertEquals(base.name, it.name)
            assertEquals(base.date, it.date)
            assertEquals(base.note, it.note)
            assertEquals(true, it.paidWithMealCard)
            assertNull(it.refundOf)
        }
        assertEquals(base.amountCents, result.sumOf { it.amountCents })
    }

    @Test
    fun split_partCountBounds_twoToTen() {
        assertThrows(IllegalArgumentException::class.java) { splitExpenses(base, parts(10_000L), "s") }
        assertThrows(IllegalArgumentException::class.java) { splitExpenses(base, emptyList(), "s") }
        assertEquals(2, splitExpenses(base, parts(1L, 9_999L), "s").size)
        assertEquals(MAX_SPLIT_PARTS, splitExpenses(base, parts(*LongArray(10) { 1_000L }), "s").size)
        assertThrows(IllegalArgumentException::class.java) {
            splitExpenses(base.copy(amountCents = 11L), parts(*LongArray(11) { 1L }), "s")
        }
    }

    @Test
    fun split_nonPositivePart_isRejected_evenWhenTheSumMatches() {
        assertThrows(IllegalArgumentException::class.java) { splitExpenses(base, parts(0L, 10_000L), "s") }
        assertThrows(IllegalArgumentException::class.java) { splitExpenses(base, parts(-1L, 10_001L), "s") }
    }

    @Test
    fun split_sumMismatch_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { splitExpenses(base, parts(5_000L, 4_999L), "s") }
        assertThrows(IllegalArgumentException::class.java) { splitExpenses(base, parts(5_000L, 5_001L), "s") }
    }

    @Test
    fun split_refundBase_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            splitExpenses(base.copy(isRefund = true), parts(5_000L, 5_000L), "s")
        }
    }
}
