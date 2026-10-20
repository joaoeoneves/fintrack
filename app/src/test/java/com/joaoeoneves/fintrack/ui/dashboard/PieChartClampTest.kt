package com.joaoeoneves.fintrack.ui.dashboard

import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [clampedForChart], which keeps net-negative (refund-heavy) categories off the chart. */
class PieChartClampTest {
    @Test
    fun negativeTotals_areClampedToZero_positivesAndZerosUnchanged_orderPreserved() {
        val slices =
            listOf(
                CategoryTotal(ExpenseCategory.TRANSFER, 1_000L),
                CategoryTotal(ExpenseCategory.INVESTMENTS, 0L),
                CategoryTotal(ExpenseCategory.SHOPPING, -2_500L),
                CategoryTotal(ExpenseCategory.RECURRING, 300L),
            )

        val clamped = slices.clampedForChart()

        assertEquals(
            listOf(
                CategoryTotal(ExpenseCategory.TRANSFER, 1_000L),
                CategoryTotal(ExpenseCategory.INVESTMENTS, 0L),
                CategoryTotal(ExpenseCategory.SHOPPING, 0L),
                CategoryTotal(ExpenseCategory.RECURRING, 300L),
            ),
            clamped,
        )
    }

    @Test
    fun emptyList_staysEmpty() {
        assertEquals(emptyList<CategoryTotal>(), emptyList<CategoryTotal>().clampedForChart())
    }
}
