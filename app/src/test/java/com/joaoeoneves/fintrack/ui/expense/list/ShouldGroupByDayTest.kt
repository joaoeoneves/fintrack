package com.joaoeoneves.fintrack.ui.expense.list

import org.junit.Assert.assertEquals
import org.junit.Test

class ShouldGroupByDayTest {
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
