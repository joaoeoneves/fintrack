package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [monthlyPlanOf]: income basis, committed money and the safe-to-spend figure. */
class MonthlyPlanTest : MonthlyPlanTestBase() {
    private val food = BuiltInCategories.FOOD
    private val shopping = BuiltInCategories.SHOPPING
    private val investments = BuiltInCategories.INVESTMENTS

    @Test
    fun noLines_notConfigured_andEmptyEverything() {
        val result = plan(lines = emptyList())

        assertFalse(result.isConfigured)
        assertEquals(0L, result.expectedIncomeCents)
        assertEquals(0L, result.safeToSpendCents)
    }

    @Test
    fun incomeSums_expectedAndActual() {
        val result = plan(lines = listOf(line(200_000L), line(20_000L)), income = listOf(5_000L, 7_000L))

        assertTrue(result.isConfigured)
        assertEquals(220_000L, result.expectedIncomeCents)
        assertEquals(12_000L, result.actualIncomeCents)
    }

    @Test
    fun basis_isExpected_whenLargerThanActual() {
        assertEquals(200_000L, plan(income = listOf(50_000L)).incomeBasisCents)
    }

    @Test
    fun basis_isActual_whenLargerThanExpected() {
        assertEquals(250_000L, plan(income = listOf(250_000L)).incomeBasisCents)
    }

    @Test
    fun spendWithinBudget_leavesSafeToSpendUnchanged() {
        val untouched = plan(listOf(bucket(food, 30_000L, 0L)))
        val partlySpent = plan(listOf(bucket(food, 30_000L, 12_000L)))

        assertEquals(170_000L, untouched.safeToSpendCents)
        assertEquals(170_000L, partlySpent.safeToSpendCents)
    }

    @Test
    fun overspend_reducesSafeByTheExcess() {
        val result = plan(listOf(bucket(food, 30_000L, 35_000L)))

        assertEquals(165_000L, result.safeToSpendCents)
        assertEquals(35_000L, result.committedCents)
        assertEquals(0L, result.remainingInBucketsCents)
    }

    @Test
    fun unbudgetedSpend_countsInFull() {
        val result = plan(listOf(bucket(shopping, null, 4_000L)))

        assertEquals(4_000L, result.committedCents)
        assertEquals(196_000L, result.safeToSpendCents)
    }

    @Test
    fun savingBucket_plannedCounts_andOverSavingCountsMore() {
        val planned = plan(listOf(bucket(investments, 50_000L, 10_000L)))
        val over = plan(listOf(bucket(investments, 50_000L, 60_000L)))

        assertEquals(50_000L, planned.plannedSavingCents)
        assertEquals(150_000L, planned.safeToSpendCents)
        assertEquals(140_000L, over.safeToSpendCents)
    }

    @Test
    fun refund_flooredBucketStillCountsItsLimit() {
        // spentCents is already floored at 0 by budgetProgressOf; the limit is still reserved.
        val result = plan(listOf(bucket(food, 30_000L, 0L)))

        assertEquals(30_000L, result.committedCents)
    }

    @Test
    fun refundOnlyUnbudgetedBucket_commitsNothing() {
        assertEquals(200_000L, plan(listOf(bucket(shopping, null, 0L))).safeToSpendCents)
    }

    @Test
    fun archivedAndUnknownCategories_areCounted() {
        val archived = userCategory("old", kind = CategoryKind.SPENDING, archived = true)
        val unknown = Category.unknown(CategoryId("gone"))

        val result = plan(listOf(bucket(archived, 1_000L, 500L), bucket(unknown, null, 2_000L)))

        assertEquals(3_000L, result.committedCents)
        assertEquals(1_000L, result.plannedSpendingCents)
    }

    @Test
    fun safeToSpend_canBeNegative() {
        val result = plan(listOf(bucket(food, 150_000L, 0L), bucket(shopping, 80_000L, 90_000L)))

        assertEquals(-40_000L, result.safeToSpendCents)
    }
}
