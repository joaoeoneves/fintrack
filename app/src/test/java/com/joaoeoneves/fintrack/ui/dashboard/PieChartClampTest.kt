package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [clampedForChart], which keeps net-negative (refund-heavy) categories off the chart. */
class PieChartClampTest {
    @Test
    fun negativeTotals_areClampedToZero_positivesAndZerosUnchanged_orderPreserved() {
        val slices =
            listOf(
                CategoryTotal(BuiltInCategories.TRANSFER, 1_000L),
                CategoryTotal(BuiltInCategories.INVESTMENTS, 0L),
                CategoryTotal(BuiltInCategories.SHOPPING, -2_500L),
                CategoryTotal(BuiltInCategories.RECURRING, 300L),
            )

        val clamped = slices.clampedForChart()

        assertEquals(
            listOf(
                CategoryTotal(BuiltInCategories.TRANSFER, 1_000L),
                CategoryTotal(BuiltInCategories.INVESTMENTS, 0L),
                CategoryTotal(BuiltInCategories.SHOPPING, 0L),
                CategoryTotal(BuiltInCategories.RECURRING, 300L),
            ),
            clamped,
        )
    }

    @Test
    fun emptyList_staysEmpty() {
        assertEquals(emptyList<CategoryTotal>(), emptyList<CategoryTotal>().clampedForChart())
    }
}
