package com.joaoeoneves.fintrack.ui.income.list

import org.junit.Assert.assertEquals
import org.junit.Test

class ShouldGroupIncomeByDayTest {
    @Test
    fun onlyDateSorts_groupByDay() {
        val expected =
            mapOf(
                IncomeSortOption.DATE_DESC to true,
                IncomeSortOption.DATE_ASC to true,
                IncomeSortOption.AMOUNT_DESC to false,
                IncomeSortOption.AMOUNT_ASC to false,
            )
        assertEquals(IncomeSortOption.entries.toSet(), expected.keys)
        IncomeSortOption.entries.forEach { assertEquals("$it", expected.getValue(it), shouldGroupByDay(it)) }
    }
}
