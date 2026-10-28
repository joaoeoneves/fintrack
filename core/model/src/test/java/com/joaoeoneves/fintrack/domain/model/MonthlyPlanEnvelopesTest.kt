package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthlyPlanEnvelopesTest : MonthlyPlanTestBase() {
    private val oct = YearMonth.of(2026, 10)
    private val envelope = Envelope("env", "Food", 0)

    private fun cat(
        id: String,
        kind: CategoryKind = CategoryKind.SPENDING,
        groupId: String? = "env",
    ) = Category(CategoryId(id), id, "icon", "slate", kind, 0, archived = false, builtIn = false, groupId = groupId)

    private val dinners = cat("dinners")
    private val snacks = cat("snacks")
    private val rent = cat("rent", groupId = null)

    private fun planWith(
        buckets: List<CategoryBudget>,
        envelopes: List<Envelope>,
    ) = monthlyPlanOf(buckets, listOf(line(200_000)), received(), oct, 22, envelopes)

    @Test
    fun committed_isPooledPerEnvelope() {
        val buckets =
            listOf(
                bucket(dinners, 10_000, spent = 15_000),
                bucket(snacks, 8_000, spent = 1_000),
                bucket(rent, 5_000),
            )

        val summary = planWith(buckets, listOf(envelope))

        assertEquals(18_000L + 5_000L, summary.committedCents)
    }

    @Test
    fun withoutEnvelopes_committedIsPerBucket() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 15_000), bucket(snacks, 8_000, spent = 1_000))

        assertEquals(23_000L, planWith(buckets, emptyList()).committedCents)
    }

    @Test
    fun fixedAmount_countsAsPlannedSpending_andRemainingIsAmountMinusSpend() {
        val buckets = listOf(bucket(dinners, 99_000, spent = 12_000), bucket(rent, 5_000))

        val summary = planWith(buckets, listOf(envelope.copy(amountCents = 40_000)))

        assertEquals(45_000L, summary.plannedSpendingCents)
        assertEquals(28_000L + 5_000L, summary.remainingInBucketsCents)
        assertEquals(40_000L + 5_000L, summary.committedCents)
    }

    @Test
    fun fixedAmount_overspent_remainingFloorsAtZero_committedIsSpend() {
        val summary =
            planWith(
                listOf(bucket(dinners, null, spent = 60_000)),
                listOf(envelope.copy(amountCents = 40_000)),
            )

        assertEquals(0L, summary.remainingInBucketsCents)
        assertEquals(60_000L, summary.committedCents)
    }

    @Test
    fun perCategoryEnvelope_savingKindStaysSaving() {
        val saving = cat("invest", CategoryKind.SAVING)

        val summary = planWith(listOf(bucket(saving, 7_000), bucket(dinners, 3_000)), listOf(envelope))

        assertEquals(7_000L, summary.plannedSavingCents)
        assertEquals(3_000L, summary.plannedSpendingCents)
    }
}
