package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [Transaction.cashFlowCents] signs, [TransactionsInRange.all] ordering and [recentActivity]. */
class TransactionTest {
    private val day1 = Instant.parse("2026-10-01T12:00:00Z")
    private val day2 = Instant.parse("2026-10-02T12:00:00Z")
    private val day3 = Instant.parse("2026-10-03T12:00:00Z")
    private val day4 = Instant.parse("2026-10-04T12:00:00Z")
    private val range = InstantRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))

    @Test
    fun cashFlowCents_regularExpense_isNegative() {
        assertEquals(-1_250L, testExpense(id = "e", amountCents = 1_250L, date = day1).cashFlowCents)
    }

    @Test
    fun cashFlowCents_refund_isPositive() {
        assertEquals(1_250L, testExpense(id = "r", amountCents = 1_250L, date = day1, isRefund = true).cashFlowCents)
    }

    @Test
    fun cashFlowCents_income_isPositive() {
        assertEquals(300_000L, testIncome(id = "i", amountCents = 300_000L, date = day1).cashFlowCents)
    }

    @Test
    fun all_mergesExpensesAndIncome_newestFirst() {
        val tx =
            TransactionsInRange(
                range = range,
                expenses = listOf(testExpense("e1", 100L, day1), testExpense("e3", 300L, day3)),
                income = listOf(testIncome("i4", 400L, day4), testIncome("i2", 200L, day2)),
            )

        assertEquals(listOf("i4", "e3", "i2", "e1"), tx.all.map { it.id })
    }

    @Test
    fun all_empty_isEmpty() {
        assertTrue(TransactionsInRange(range, emptyList(), emptyList()).all.isEmpty())
    }

    @Test
    fun recentActivity_takesLimitPerKind_newestFirst() {
        val tx =
            TransactionsInRange(
                range = range,
                expenses = listOf(day1, day3, day2, day4).mapIndexed { i, d -> testExpense("e$i", 100L, d) },
                income = listOf(testIncome("i1", 100L, day1)),
            )

        val recent = tx.recentActivity(limit = 2)

        assertEquals(listOf(day4, day3), recent.expenses.map { it.date })
        assertEquals(listOf("i1"), recent.income.map { it.id })
    }
}
