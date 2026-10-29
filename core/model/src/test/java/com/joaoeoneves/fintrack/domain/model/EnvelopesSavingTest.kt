package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** SAVING-kind categories are not part of the envelopes but still reduce Livre exactly as before. */
class EnvelopesSavingTest : MonthlyPlanTestBase() {
    private val oct = YearMonth.of(2026, 10)
    private val env = Envelope("env", "Everyday", 0)

    private fun cat(
        id: String,
        kind: CategoryKind,
        groupId: String? = null,
    ) = Category(CategoryId(id), id, "icon", "slate", kind, 0, false, false, groupId)

    private val food = cat("food", CategoryKind.SPENDING, "env")
    private val invest = cat("invest", CategoryKind.SAVING, "env")
    private val investLoose = cat("invest", CategoryKind.SAVING)

    private fun summary(
        buckets: List<CategoryBudget>,
        categories: List<Category>,
        envelopes: List<Envelope> = listOf(env),
    ) = envelopesOf(buckets, categories, envelopes, PlanMonth(oct, 20))

    @Test
    fun savingCategory_leavesTheEnvelopeCard_andCommitsPlanned() {
        val result = summary(listOf(bucket(food, 10_000, spent = 2_000), bucket(invest, 50_000)), listOf(food, invest))

        assertEquals(
            listOf(food.id),
            result.envelopes
                .single()
                .rows
                .map { it.category.id },
        )
        assertEquals(10_000L, result.envelopes.single().amountCents)
        assertEquals(50_000L, result.savingCommittedCents)
    }

    private fun savingCommitted(b: CategoryBudget) = summary(listOf(b), listOf(invest)).savingCommittedCents

    @Test
    fun savingCommitment_isTheLargerOfPlannedAndSpent() {
        assertEquals(70_000L, savingCommitted(bucket(invest, 50_000, spent = 70_000)))
        assertEquals(50_000L, savingCommitted(bucket(invest, 50_000, spent = 10_000)))
        assertEquals(30_000L, savingCommitted(bucket(invest, null, spent = 30_000)))
        assertEquals(0L, summary(listOf(bucket(invest, 0, spent = 0)), listOf(invest)).savingCommittedCents)
    }

    @Test
    fun savingKind_isTakenFromTheCategoriesList_overTheBucketsOwnCopy() {
        val staleBucketCategory = cat("invest", CategoryKind.SPENDING, "env")

        val result = summary(listOf(bucket(staleBucketCategory, 40_000)), listOf(invest))

        assertEquals(40_000L, result.savingCommittedCents)
        assertEquals(
            0,
            result.envelopes
                .single()
                .rows.size,
        )
    }

    @Test
    fun looseSaving_isNotListedAmongTheUnenveloped() {
        val loose = summary(listOf(bucket(food, 10_000), bucket(investLoose, 50_000)), listOf(food, investLoose))

        assertEquals(50_000L, loose.savingCommittedCents)
        assertTrue(loose.unenveloped.none { it.category.kind == CategoryKind.SAVING })
    }

    @Test
    fun noSaving_leavesSavingCommittedZero() {
        assertEquals(0L, summary(listOf(bucket(food, 10_000)), listOf(food)).savingCommittedCents)
    }
}
