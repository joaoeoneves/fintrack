package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PeriodIncomeSourcesTest {
    private val rent = PlannedIncome("rent", "Rent", 20_000L, 1)
    private val rentKey = IncomeSourceKey.Row(null, null, "rent").encode()

    private fun at(day: Int): Instant = Instant.parse("2026-10-%02dT12:00:00Z".format(day))

    private fun named(
        id: String,
        source: String,
        cents: Long,
        day: Int,
    ) = testIncome(id, cents, at(day)).copy(source = source)

    @Test
    fun empty_noChips() {
        assertTrue(periodIncomeSourcesOf(emptyList(), listOf(rent)).isEmpty())
    }

    @Test
    fun rowSource_usesTheLineLabel_notTheSpelling() {
        val pay = testIncome("pay_rent_202610", 20_000L, at(5))
        val chips = periodIncomeSourcesOf(listOf(pay), listOf(rent))
        assertEquals(listOf(IncomeSourceChip(rentKey, "Rent")), chips)
    }

    @Test
    fun namedSource_groupsByNormalisedText_andShowsLatestSpelling() {
        val older = named("a", "Gift", 1_000L, 2)
        val latest = named("b", " GIFT ", 500L, 9)
        val chips = periodIncomeSourcesOf(listOf(older, latest), emptyList())
        assertEquals(1, chips.size)
        assertEquals("GIFT", chips.single().label)
        assertEquals(IncomeSourceKey.Named("gift").encode(), chips.single().key)
    }

    @Test
    fun sortedBySummedAmountDescending() {
        val small = named("a", "Alpha", 100L, 2)
        val bigA = named("b", "Beta", 400L, 3)
        val bigB = named("c", "Beta", 400L, 4)
        val chips = periodIncomeSourcesOf(listOf(small, bigA, bigB), emptyList())
        assertEquals(listOf("Beta", "Alpha"), chips.map { it.label })
    }

    @Test
    fun blankSource_hasNullLabel() {
        val chips = periodIncomeSourcesOf(listOf(named("a", "   ", 100L, 2)), emptyList())
        assertEquals(1, chips.size)
        assertNull(chips.single().label)
    }
}
