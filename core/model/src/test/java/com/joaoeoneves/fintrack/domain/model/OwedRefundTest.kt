package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OwedRefundTest {
    private fun awaiting(
        id: String,
        amount: Long = 10_000L,
        date: String = "2026-03-01T10:00:00Z",
    ) = Expense(id, "E$id", amount, BuiltInCategories.FOOD, Instant.parse(date), null, awaitingRefund = true)

    private fun refund(
        of: String?,
        amount: Long,
        id: String = "r-$of-$amount",
    ) = Expense(id, "R", amount, BuiltInCategories.FOOD, Instant.parse("2026-03-05T10:00:00Z"), null)
        .copy(isRefund = true, refundOf = of)

    @Test
    fun noAwaitingExpenses_owesNothing() {
        assertTrue(owedRefunds(emptyList(), listOf(refund("a", 100))).isEmpty())
        assertEquals(0L, emptyList<OwedRefund>().totalRemainingCents())
    }

    @Test
    fun noRefunds_wholeAmountIsOwed() {
        val owed = owedRefunds(listOf(awaiting("a")), emptyList()).single()

        assertEquals(0L, owed.refundedCents)
        assertEquals(10_000L, owed.remainingCents)
        assertEquals(0, owed.percent)
    }

    @Test
    fun partialRefund_leavesTheRest() {
        val owed = owedRefunds(listOf(awaiting("a")), listOf(refund("a", 2_500))).single()

        assertEquals(2_500L, owed.refundedCents)
        assertEquals(7_500L, owed.remainingCents)
        assertEquals(25, owed.percent)
    }

    @Test
    fun fullRefund_leavesTheList() {
        assertTrue(owedRefunds(listOf(awaiting("a")), listOf(refund("a", 10_000))).isEmpty())
    }

    @Test
    fun overRefund_countsAsNothingOwed() {
        assertTrue(owedRefunds(listOf(awaiting("a")), listOf(refund("a", 12_000))).isEmpty())
    }

    @Test
    fun severalRefunds_areSummed() {
        val refunds = listOf(refund("a", 1_000), refund("a", 3_000), refund("a", 500))

        val owed = owedRefunds(listOf(awaiting("a")), refunds).single()

        assertEquals(4_500L, owed.refundedCents)
        assertEquals(5_500L, owed.remainingCents)
    }

    @Test
    fun severalRefunds_canAddUpToFullyRefunded() {
        val refunds = listOf(refund("a", 6_000), refund("a", 4_000))

        assertTrue(owedRefunds(listOf(awaiting("a")), refunds).isEmpty())
    }

    @Test
    fun refundToANonAwaitingExpense_isIgnored() {
        val owed = owedRefunds(listOf(awaiting("a")), listOf(refund("other", 9_000), refund(null, 9_000)))

        assertEquals(10_000L, owed.single().remainingCents)
    }

    @Test
    fun expenseNotInTheAwaitingList_isNeverListed() {
        assertTrue(owedRefunds(emptyList(), listOf(refund("a", 100))).isEmpty())
    }

    @Test
    fun splitParts_areListedIndividually() {
        val parts =
            listOf(
                awaiting("p1", 6_000).copy(splitId = "s"),
                awaiting("p2", 4_000).copy(splitId = "s"),
            )

        val owed = owedRefunds(parts, listOf(refund("p1", 6_000)))

        assertEquals(listOf("p2"), owed.map { it.expense.id })
        assertEquals(4_000L, owed.totalRemainingCents())
    }

    @Test
    fun sortedNewestFirst_tiesById() {
        val result =
            owedRefunds(
                listOf(
                    awaiting("b", date = "2026-03-02T10:00:00Z"),
                    awaiting("z", date = "2026-03-09T10:00:00Z"),
                    awaiting("a", date = "2026-03-02T10:00:00Z"),
                ),
                emptyList(),
            )

        assertEquals(listOf("z", "a", "b"), result.map { it.expense.id })
    }

    @Test
    fun total_sumsOnlyWhatIsStillOwed() {
        val owed =
            owedRefunds(
                listOf(awaiting("a", 10_000), awaiting("b", 5_000), awaiting("c", 2_000)),
                listOf(refund("a", 4_000), refund("c", 2_000)),
            )

        assertEquals(listOf("a", "b"), owed.map { it.expense.id }.sorted())
        assertEquals(11_000L, owed.totalRemainingCents())
    }
}
