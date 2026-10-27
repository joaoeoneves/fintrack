package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBreakdownRankingTest {
    private val a = BuiltInCategories.SHOPPING
    private val b = BuiltInCategories.TRANSFER
    private val c = BuiltInCategories.INVESTMENTS

    @Test
    fun emptyInput_givesEmptyList() {
        assertTrue(rankedBreakdown(emptyList()).isEmpty())
    }

    @Test
    fun allZeroOrNegative_givesEmptyList() {
        assertTrue(rankedBreakdown(listOf(CategoryTotal(a, 0L), CategoryTotal(b, -500L))).isEmpty())
    }

    @Test
    fun sortedDescending_withPercentAndFraction() {
        val entries = rankedBreakdown(listOf(CategoryTotal(a, 250L), CategoryTotal(b, 750L)))
        assertEquals(listOf(b, a), entries.map { it.category })
        assertEquals(listOf(750L, 250L), entries.map { it.cents })
        assertEquals(listOf(75, 25), entries.map { it.percent })
        assertEquals(0.75f, entries[0].fraction, 1e-6f)
    }

    @Test
    fun negativesClamped_andDropped_zerosDropped_totalExcludesNegatives() {
        val entries =
            rankedBreakdown(
                listOf(CategoryTotal(a, -2_000L), CategoryTotal(b, 100L), CategoryTotal(c, 0L)),
            )
        assertEquals(1, entries.size)
        assertEquals(b, entries[0].category)
        assertEquals(100, entries[0].percent)
        assertEquals(1f, entries[0].fraction, 0f)
    }

    @Test
    fun ties_keepInputOrder() {
        val entries = rankedBreakdown(listOf(CategoryTotal(c, 100L), CategoryTotal(a, 100L), CategoryTotal(b, 100L)))
        assertEquals(listOf(c, a, b), entries.map { it.category })
    }

    @Test
    fun fractionsSumToOne() {
        val entries = rankedBreakdown(listOf(CategoryTotal(a, 1L), CategoryTotal(b, 1L), CategoryTotal(c, 1L)))
        assertEquals(1f, entries.sumOf { it.fraction.toDouble() }.toFloat(), 1e-5f)
        assertEquals(listOf(33, 33, 33), entries.map { it.percent })
    }
}
