package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** [sortTransactions] and [shouldGroupByDay]. */
class ActivitySortingTest {
    private val t0 = Instant.parse("2026-10-10T12:00:00Z")
    private val old = testExpense("old", 300L, t0)
    private val mid = testIncome("mid", 900L, t0.plusSeconds(60))
    private val new = testExpense("new", 100L, t0.plusSeconds(120))
    private val list = listOf(mid, new, old)

    @Test
    fun dateDesc_newestFirst() {
        assertEquals(listOf(new, mid, old), sortTransactions(list, SortOption.DATE_DESC))
    }

    @Test
    fun dateAsc_oldestFirst() {
        assertEquals(listOf(old, mid, new), sortTransactions(list, SortOption.DATE_ASC))
    }

    @Test
    fun amountDesc_highestFirst_acrossTypes() {
        assertEquals(listOf(mid, old, new), sortTransactions(list, SortOption.AMOUNT_DESC))
    }

    @Test
    fun amountAsc_lowestFirst_acrossTypes() {
        assertEquals(listOf(new, old, mid), sortTransactions(list, SortOption.AMOUNT_ASC))
    }

    @Test
    fun refunds_sortByNegativeAmount() {
        val refund = testExpense("refund", 100_000L, t0, isRefund = true)
        val items = listOf(old, refund, mid)

        assertEquals(listOf(mid, old, refund), sortTransactions(items, SortOption.AMOUNT_DESC))
        assertEquals(listOf(refund, old, mid), sortTransactions(items, SortOption.AMOUNT_ASC))
    }

    @Test
    fun emptyList_staysEmpty() {
        SortOption.entries.forEach { assertEquals(emptyList<Any>(), sortTransactions(emptyList(), it)) }
    }

    @Test
    fun onlyDateSorts_groupByDay() {
        val expected =
            mapOf(
                SortOption.DATE_DESC to true,
                SortOption.DATE_ASC to true,
                SortOption.AMOUNT_DESC to false,
                SortOption.AMOUNT_ASC to false,
            )
        assertEquals(SortOption.entries.toSet(), expected.keys)
        SortOption.entries.forEach { assertEquals("$it", expected.getValue(it), shouldGroupByDay(it)) }
    }
}
