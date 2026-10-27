package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.testutil.testExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MergeRecentTest {
    private val base = Instant.parse("2026-10-20T12:00:00Z")

    private fun expenseAt(
        id: String,
        minutesAgo: Long,
    ): Expense = testExpense(id, 100L, base.minusSeconds(minutesAgo * 60))

    private fun incomeAt(
        id: String,
        minutesAgo: Long,
    ) = Income(id = id, source = id, amountCents = 100L, date = base.minusSeconds(minutesAgo * 60), note = null)

    @Test
    fun empty_returnsEmpty() {
        assertTrue(mergeRecent(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun limitConstant_isFive() {
        assertEquals(5, RECENT_ITEMS_LIMIT)
    }

    @Test
    fun onlyExpenses_sortedNewestFirst() {
        val result = mergeRecent(listOf(expenseAt("old", 30), expenseAt("new", 1), expenseAt("mid", 10)), emptyList())

        assertEquals(listOf("new", "mid", "old"), result.map { it.id })
    }

    @Test
    fun onlyIncome_sortedNewestFirst() {
        val result = mergeRecent(emptyList(), listOf(incomeAt("a", 5), incomeAt("b", 2)))

        assertEquals(listOf("b", "a"), result.map { it.id })
    }

    @Test
    fun mixedTypes_interleavedByDate_keepingTheirTypes() {
        val expenses = listOf(expenseAt("e1", 1), expenseAt("e2", 20))
        val income = listOf(incomeAt("i1", 10), incomeAt("i2", 30))

        val result = mergeRecent(expenses, income)

        assertEquals(listOf("e1", "i1", "e2", "i2"), result.map { it.id })
        assertEquals(listOf(true, false, true, false), result.map { it is Expense })
    }

    @Test
    fun overLimit_keepsNewestFiveAcrossBothTypes() {
        val expenses = (1..4).map { expenseAt("e$it", it * 10L) }
        val income = (1..4).map { incomeAt("i$it", it * 10L + 5) }

        val result = mergeRecent(expenses, income)

        assertEquals(listOf("e1", "i1", "e2", "i2", "e3"), result.map { it.id })
    }

    @Test
    fun customLimit_isHonoured_andZeroYieldsEmpty() {
        val expenses = (1..3).map { expenseAt("e$it", it.toLong()) }

        assertEquals(listOf("e1", "e2"), mergeRecent(expenses, emptyList(), limit = 2).map { it.id })
        assertTrue(mergeRecent(expenses, emptyList(), limit = 0).isEmpty())
    }

    @Test
    fun limitLargerThanInput_returnsEverything() {
        val result = mergeRecent(listOf(expenseAt("e", 1)), listOf(incomeAt("i", 2)), limit = 50)

        assertEquals(2, result.size)
    }

    @Test
    fun sameInstant_keepsBothEntries() {
        val result = mergeRecent(listOf(expenseAt("e", 5)), listOf(incomeAt("i", 5)))

        assertEquals(setOf("e", "i"), result.map { it.id }.toSet())
    }

    @Test
    fun inputOrder_doesNotMatter() {
        val all = (1..7).map { expenseAt("e$it", it.toLong()) }
        val scrambled = listOf(all[3], all[0], all[6], all[1], all[5], all[2], all[4])

        assertEquals(all.take(5), mergeRecent(scrambled, emptyList()))
    }
}
