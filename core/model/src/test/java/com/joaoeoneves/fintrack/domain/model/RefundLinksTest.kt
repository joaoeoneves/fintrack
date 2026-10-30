package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** [refundStatusOf], [refundIndexOf], [refundCandidates] and the rule that linking never changes a total. */
class RefundLinksTest {
    private val at = Instant.parse("2026-10-10T12:00:00Z")
    private val original = testExpense("o", 10_000L, at)

    private fun refund(
        id: String,
        cents: Long,
        of: String? = "o",
    ) = testExpense(id, cents, at, isRefund = true).copy(refundOf = of)

    @Test
    fun status_noLinkedRefund_isNull() {
        assertNull(refundStatusOf(original, emptyList()))
        assertNull(refundStatusOf(original, listOf(refund("r", 500, of = "other"), refund("r2", 500, of = null))))
    }

    @Test
    fun status_originalThatIsARefund_isNull() {
        val asRefund = original.copy(isRefund = true)

        assertNull(refundStatusOf(asRefund, listOf(refund("r", 500))))
    }

    @Test
    fun status_sumsSeveralRefunds_andFloorsThePercent() {
        val status = refundStatusOf(original, listOf(refund("r1", 3_333L), refund("r2", 1L)))!!

        assertEquals(3_334L, status.refundedCents)
        assertEquals(33, status.percent)
        assertEquals(6_666L, status.netCents)
    }

    @Test
    fun status_overRefund_capsPercentAtHundred_andNetAtZero() {
        val status = refundStatusOf(original, listOf(refund("r", 15_000L)))!!

        assertEquals(100, status.percent)
        assertEquals(0L, status.netCents)
    }

    @Test
    fun status_fullRefund_isHundredPercent_andTinyRefundFloorsToZero() {
        assertEquals(100, refundStatusOf(original, listOf(refund("r", 10_000L)))!!.percent)
        assertEquals(0, refundStatusOf(original, listOf(refund("r", 99L)))!!.percent)
    }

    @Test
    fun status_zeroOriginal_doesNotDivideByZero() {
        val status = RefundStatus(refundedCents = 5L, originalCents = 0L)

        assertEquals(0, status.percent)
        assertEquals(0L, status.netCents)
    }

    @Test
    fun status_ignoresLinkedEntriesThatAreNotRefunds() {
        val notRefund = testExpense("x", 700L, at).copy(refundOf = "o")

        assertNull(refundStatusOf(original, listOf(notRefund)))
    }

    @Test
    fun index_totalsPerOriginal_skipsUnlinkedAndNonRefunds() {
        val index =
            refundIndexOf(
                listOf(
                    refund("r1", 100, of = "a"),
                    refund("r2", 250, of = "a"),
                    refund("r3", 40, of = "b"),
                    refund("r4", 999, of = null),
                    testExpense("x", 5, at).copy(refundOf = "a"),
                ),
            )

        assertEquals(mapOf("a" to 350L, "b" to 40L), index)
    }

    @Test
    fun candidates_nonRefundsOnly_excludeId_newestFirst() {
        val old = testExpense("old", 1, Instant.parse("2026-01-01T00:00:00Z"))
        val new = testExpense("new", 1, Instant.parse("2026-09-01T00:00:00Z"))
        val mid = testExpense("mid", 1, Instant.parse("2026-05-01T00:00:00Z"))

        val result = refundCandidates(listOf(old, refund("r", 5), new, mid), excludeId = "mid")

        assertEquals(listOf("new", "old"), result.map { it.id })
        val all = refundCandidates(listOf(old, new, mid), excludeId = null)
        assertEquals(listOf("new", "mid", "old"), all.map { it.id })
    }

    @Test
    fun totals_areTheSame_linkedOrUnlinked() {
        val unlinked = listOf(original, refund("r1", 2_500L, of = null), refund("r2", 500L, of = null))
        val linked = listOf(original, refund("r1", 2_500L, of = "o"), refund("r2", 500L, of = "gone"))

        assertEquals(7_000L, unlinked.netCents())
        assertEquals(unlinked.netCents(), linked.netCents())
        assertEquals(
            unlinked.groupBy { it.category }.mapValues { it.value.netCents() },
            linked.groupBy { it.category }.mapValues { it.value.netCents() },
        )
    }
}
