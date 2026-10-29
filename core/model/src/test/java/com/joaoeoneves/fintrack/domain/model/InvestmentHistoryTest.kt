package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class InvestmentHistoryTest {
    private val zone = ZoneOffset.UTC
    private val etf = userCategory("etf", kind = CategoryKind.SAVING)
    private val food = userCategory("food")
    private val categories = listOf(etf, food)
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)

    private fun at(
        m: Int,
        d: Int,
    ): Instant = LocalDate.of(2026, m, d).atTime(12, 0).toInstant(zone)

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun inv(
        id: String,
        cents: Long,
        m: Int,
        d: Int,
        name: String = id,
        refund: Boolean = false,
    ) = testExpense(id, cents, at(m, d), etf, refund).copy(name = name)

    private fun history(vararg expenses: Expense): InvestmentHistory {
        val months = listOf(sep, oct)
        return investmentHistoryOf(months, expenses.toList(), categories, zone)
    }

    @Test
    fun onlySavingCategoriesCount() {
        val result = history(inv("a", 5_000, 10, 2), testExpense("f", 900, at(10, 3), food))

        assertEquals(5_000L, result.totalCents)
        assertEquals(1, result.movements.size)
    }

    @Test
    fun perMonth_andAllTime() {
        val result = history(inv("a", 5_000, 9, 29, "VWCE"), inv("b", 7_000, 10, 29, "VWCE"))

        assertEquals(5_000L, result.monthOf(sep)!!.totalCents)
        assertEquals(7_000L, result.monthOf(oct)!!.totalCents)
        assertEquals(12_000L, result.totalCents)
        assertEquals(12_000L, result.instruments.single().cents)
    }

    @Test
    fun instrumentNames_mergeIgnoringCaseAccentsAndSpaces_latestSpellingWins() {
        val result = history(inv("a", 100, 9, 5, "Poupança  Reforma"), inv("b", 200, 10, 5, "poupanca reforma"))

        assertEquals(1, result.instruments.size)
        assertEquals(300L, result.instruments.single().cents)
        assertEquals("poupanca reforma", result.instruments.single().label)
    }

    @Test
    fun blankName_fallsBackToTheCategory() {
        val result = history(inv("a", 100, 10, 5, name = " "), inv("b", 50, 10, 6, name = ""))

        val only = result.instruments.single()
        assertEquals(InstrumentKey("category:etf"), only.key)
        assertNull(only.label)
        assertEquals(etf.id, only.categoryId)
        assertEquals(150L, only.cents)
    }

    @Test
    fun refundsNet_andAMonthIsFlooredAtZero() {
        val result =
            history(
                inv("a", 1_000, 9, 5, "X"),
                inv("r", 400, 9, 6, "X", refund = true),
                inv("rr", 900, 10, 5, "X", refund = true),
            )

        assertEquals(600L, result.monthOf(sep)!!.totalCents)
        assertEquals(0L, result.monthOf(oct)!!.totalCents)
        assertTrue(result.monthOf(oct)!!.instruments.isEmpty())
    }

    @Test
    fun outsideTheWindow_isIgnored_andEmptyIsZero() {
        assertEquals(0L, history(inv("old", 100, 8, 5)).totalCents)
        assertEquals(0L, history().totalCents)
        assertNull(history().monthOf(YearMonth.of(2025, 1)))
    }

    @Test
    fun instrumentsAreSortedBiggestFirst_movementsNewestFirst() {
        val result = history(inv("a", 100, 9, 5, "A"), inv("b", 900, 10, 5, "B"))

        assertEquals(listOf("B", "A"), result.instruments.map { it.label })
        assertEquals(listOf("b", "a"), result.movements.map { it.id })
    }
}
