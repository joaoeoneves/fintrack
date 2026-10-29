package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Derived plan figures, [plannedSlices] and [dailyAllowanceOf]. */
class MonthlyPlanDerivedTest : MonthlyPlanTestBase() {
    private val food = BuiltInCategories.FOOD
    private val shopping = BuiltInCategories.SHOPPING
    private val investments = BuiltInCategories.INVESTMENTS

    @Test
    fun remainingInBuckets_excludesSavingAndUnbudgeted() {
        val result =
            plan(
                listOf(
                    bucket(food, 30_000L, 10_000L),
                    bucket(shopping, null, 5_000L),
                    bucket(investments, 50_000L, 0L),
                ),
            )

        assertEquals(20_000L, result.remainingInBucketsCents)
    }

    @Test
    fun plannedAllocationsAndLeftover() {
        val result = plan(listOf(bucket(food, 30_000L), bucket(investments, 50_000L)))

        assertEquals(30_000L, result.plannedSpendingCents)
        assertEquals(50_000L, result.plannedSavingCents)
        assertEquals(80_000L, result.plannedAllocationsCents)
        assertEquals(120_000L, result.plannedLeftoverCents)
    }

    @Test
    fun plannedLeftover_canBeNegative_andIgnoresActualIncome() {
        val result = plan(listOf(bucket(food, 250_000L)), income = listOf(900_000L))

        assertEquals(-50_000L, result.plannedLeftoverCents)
    }

    @Test
    fun plannedSlices_onlyPositiveLimits_keepingOrder() {
        val buckets =
            listOf(
                bucket(BuiltInCategories.TRANSFER, null, 1L),
                bucket(investments, 50_000L),
                bucket(shopping, 0L),
                bucket(food, 30_000L, 99L),
            )

        assertEquals(
            listOf(CategoryTotal(investments, 50_000L), CategoryTotal(food, 30_000L)),
            buckets.plannedSlices(),
        )
    }

    @Test
    fun plannedSlices_emptyWhenNothingPlanned() {
        assertEquals(emptyList<CategoryTotal>(), emptyList<CategoryBudget>().plannedSlices())
    }
}
