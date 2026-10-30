package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [monthlyPlanOf] and [plannedSlices] over buckets whose planned amount comes from recurring items only. */
class MonthlyPlanRecurringTest : MonthlyPlanTestBase() {
    private fun recurringBucket(
        category: Category,
        planned: Long,
        spent: Long = 0L,
    ) = CategoryBudget(category, spent, planned, PlannedSource.RECURRING)

    @Test
    fun recurringOnlyBucket_countsAsPlannedSpending() {
        val summary = plan(listOf(recurringBucket(BuiltInCategories.FOOD, 8_650L, spent = 2_000L)))

        assertEquals(8_650L, summary.plannedSpendingCents)
        assertEquals(8_650L, summary.committedCents)
        assertEquals(6_650L, summary.remainingInBucketsCents)
    }

    @Test
    fun recurringOnlySavingBucket_countsAsPlannedSaving() {
        val summary = plan(listOf(recurringBucket(BuiltInCategories.INVESTMENTS, 30_000L)))

        assertEquals(30_000L, summary.plannedSavingCents)
        assertEquals(0L, summary.plannedSpendingCents)
    }

    @Test
    fun multipleBuckets_addUp() {
        val summary =
            plan(
                listOf(
                    bucket(BuiltInCategories.SHOPPING, 10_000L),
                    recurringBucket(BuiltInCategories.FOOD, 5_000L, spent = 7_000L),
                ),
            )

        assertEquals(15_000L, summary.plannedSpendingCents)
        assertEquals(17_000L, summary.committedCents)
        assertEquals(10_000L, summary.remainingInBucketsCents)
    }

    @Test
    fun bucketWithoutPlanned_isNotPlanned() {
        val summary = plan(listOf(CategoryBudget(BuiltInCategories.FOOD, 500L)))

        assertEquals(0L, summary.plannedSpendingCents)
        assertEquals(500L, summary.committedCents)
    }

    @Test
    fun plannedSlices_includeRecurringOnlyBuckets_inOrder() {
        val slices =
            listOf(
                recurringBucket(BuiltInCategories.SHOPPING, 700L),
                CategoryBudget(BuiltInCategories.FOOD, 0L),
                recurringBucket(BuiltInCategories.TRANSFER, 0L),
                bucket(BuiltInCategories.INVESTMENTS, 900L),
            ).plannedSlices()

        assertEquals(listOf(BuiltInCategories.SHOPPING, BuiltInCategories.INVESTMENTS), slices.map { it.category })
        assertEquals(listOf(700L, 900L), slices.map { it.totalCents })
    }

    @Test
    fun isOverBudget_usesPlanned() {
        assertTrue(recurringBucket(BuiltInCategories.FOOD, 100L, spent = 101L).isOverBudget)
        assertEquals(false, recurringBucket(BuiltInCategories.FOOD, 100L, spent = 100L).isOverBudget)
        assertEquals(false, CategoryBudget(BuiltInCategories.FOOD, 500L).isOverBudget)
    }
}
