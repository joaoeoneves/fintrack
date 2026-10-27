package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Unit tests for [spendingSplitOf] and [SpendingSplit.spendingCents]. */
class SpendingSplitTest {
    private val date = Instant.parse("2026-10-05T12:00:00Z")

    private fun expense(
        id: String,
        cents: Long,
        category: Category = BuiltInCategories.SHOPPING,
        isRefund: Boolean = false,
    ) = Expense(id, id, cents, category, date, note = null, isRefund = isRefund)

    @Test
    fun empty_isAllZero() {
        val split = spendingSplitOf(emptyList())

        assertEquals(SpendingSplit(0L, 0L, 0L), split)
        assertEquals(0L, split.spendingCents)
    }

    @Test
    fun recurringIds_areFixed_andManualIds_areVariable() {
        val split =
            spendingSplitOf(
                listOf(
                    expense("rec_a_2026-10", 1_200L),
                    expense("rec_b_2026-10", 300L, BuiltInCategories.FOOD),
                    expense("manual1", 450L),
                    expense("manual2", 50L, BuiltInCategories.FOOD),
                ),
            )

        assertEquals(1_500L, split.fixedCents)
        assertEquals(500L, split.variableCents)
        assertEquals(0L, split.savingCents)
        assertEquals(2_000L, split.spendingCents)
    }

    @Test
    fun savingKind_onlyCountsAsSaving_evenWhenRecurring() {
        val split =
            spendingSplitOf(
                listOf(
                    expense("rec_s_2026-10", 2_500L, BuiltInCategories.INVESTMENTS),
                    expense("manual", 700L, BuiltInCategories.INVESTMENTS),
                    expense("spend", 100L),
                ),
            )

        assertEquals(0L, split.fixedCents)
        assertEquals(100L, split.variableCents)
        assertEquals(3_200L, split.savingCents)
        assertEquals(100L, split.spendingCents)
    }

    @Test
    fun refunds_netWithinTheirOwnBucket() {
        val split =
            spendingSplitOf(
                listOf(
                    expense("rec_a", 1_000L),
                    expense("rec_r", 400L, isRefund = true),
                    expense("m1", 900L),
                    expense("m2", 150L, isRefund = true),
                    expense("inv", 800L, BuiltInCategories.INVESTMENTS),
                    expense("invr", 300L, BuiltInCategories.INVESTMENTS, isRefund = true),
                ),
            )

        assertEquals(600L, split.fixedCents)
        assertEquals(750L, split.variableCents)
        assertEquals(500L, split.savingCents)
    }

    @Test
    fun refundsAboveSpend_floorEachBucketAtZero() {
        val split =
            spendingSplitOf(
                listOf(
                    expense("rec_a", 100L),
                    expense("rec_r", 900L, isRefund = true),
                    expense("m", 200L),
                    expense("mr", 201L, isRefund = true),
                    expense("inv", 50L, BuiltInCategories.INVESTMENTS),
                    expense("invr", 60L, BuiltInCategories.INVESTMENTS, isRefund = true),
                ),
            )

        assertEquals(SpendingSplit(0L, 0L, 0L), split)
    }

    @Test
    fun floorInOneBucket_doesNotReduceTheOther() {
        val split =
            spendingSplitOf(
                listOf(
                    expense("rec_r", 500L, isRefund = true),
                    expense("m", 700L),
                ),
            )

        assertEquals(0L, split.fixedCents)
        assertEquals(700L, split.variableCents)
        assertEquals(700L, split.spendingCents)
    }

    @Test
    fun exactRefund_nets_toZero() {
        val split = spendingSplitOf(listOf(expense("m", 500L), expense("mr", 500L, isRefund = true)))

        assertEquals(0L, split.variableCents)
    }
}
