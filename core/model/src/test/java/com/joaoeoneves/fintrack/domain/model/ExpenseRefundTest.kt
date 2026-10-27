package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

/** Unit tests for [Expense.signedAmountCents] and [netCents]. */
class ExpenseRefundTest {
    private val base =
        Expense(
            id = "e1",
            name = "Shoes",
            amountCents = 5_000L,
            category = BuiltInCategories.SHOPPING,
            date = Instant.parse("2026-10-01T12:00:00Z"),
            note = null,
        )

    @Test
    fun isRefund_defaultsToFalse() {
        assertFalse(base.isRefund)
    }

    @Test
    fun signedAmountCents_regularExpense_isPositiveAmount() {
        assertEquals(5_000L, base.signedAmountCents)
    }

    @Test
    fun signedAmountCents_refund_isNegatedAmount() {
        assertEquals(-5_000L, base.copy(isRefund = true).signedAmountCents)
    }

    @Test
    fun netCents_empty_isZero() {
        assertEquals(0L, emptyList<Expense>().netCents())
    }

    @Test
    fun netCents_subtractsRefundsFromRegularExpenses() {
        val expenses =
            listOf(
                base,
                base.copy(id = "e2", amountCents = 1_200L),
                base.copy(id = "r1", amountCents = 2_000L, isRefund = true),
            )

        assertEquals(4_200L, expenses.netCents())
    }

    @Test
    fun netCents_refundsExceedingExpenses_isNegative() {
        val expenses =
            listOf(
                base.copy(amountCents = 1_000L),
                base.copy(id = "r1", amountCents = 3_000L, isRefund = true),
            )

        assertEquals(-2_000L, expenses.netCents())
    }

    @Test
    fun netCents_equalExpenseAndRefund_isExactlyZero() {
        val expenses = listOf(base, base.copy(id = "r1", isRefund = true))

        assertEquals(0L, expenses.netCents())
    }
}
