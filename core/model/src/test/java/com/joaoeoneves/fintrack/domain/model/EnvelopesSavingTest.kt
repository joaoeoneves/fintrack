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
    ) = envelopesOf(buckets, categories, envelopes, listOf(line(200_000L)), emptyList(), PlanMonth(oct, 20))

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
        assertEquals(60_000L, result.committedCents)
        assertEquals(140_000L, result.freeCents)
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
    fun livre_isUnchanged_whetherSavingSitsInAnUnfundedEnvelopeOrLoose() {
        val buckets = listOf(bucket(food, 10_000), bucket(invest, 50_000))
        val looseBuckets = listOf(bucket(food, 10_000), bucket(investLoose, 50_000))

        val inEnvelope = summary(buckets, listOf(food, invest))
        val loose = summary(looseBuckets, listOf(food, investLoose))
        // What the old math gave: one unfunded envelope committing 10 000 + 50 000.
        val old = 200_000L - 60_000L

        assertEquals(old, inEnvelope.freeCents)
        assertEquals(old, loose.freeCents)
        assertTrue(loose.unenveloped.none { it.category.kind == CategoryKind.SAVING })
    }

    @Test
    fun noSaving_leavesSavingCommittedZero() {
        assertEquals(0L, summary(listOf(bucket(food, 10_000)), listOf(food)).savingCommittedCents)
    }

    @Test
    fun envelopeMigration_skipsSavingCategories() {
        val result =
            envelopeMigrationOf(
                listOf(Budget(invest.id, 100), Budget(CategoryId("gym"), 100)),
                listOf(cat("invest", CategoryKind.SAVING), cat("gym", CategoryKind.SPENDING)),
                emptyList(),
                emptyList(),
            ) { "n-${it.id.value}" }

        assertEquals(listOf("env_gym"), result.envelopes.map { it.id })
    }

    @Test
    fun envelopeMigration_skipsSavingEarmarks() {
        val earmarkedLine = PlannedIncome("meal", "Meal", 100, 0, earmark = Earmark.Category("invest"))

        val result =
            envelopeMigrationOf(
                emptyList(),
                listOf(cat("invest", CategoryKind.SAVING)),
                emptyList(),
                listOf(earmarkedLine),
            ) { "n" }

        assertTrue(result.envelopes.none { it.id == "env_invest" })
    }
}
