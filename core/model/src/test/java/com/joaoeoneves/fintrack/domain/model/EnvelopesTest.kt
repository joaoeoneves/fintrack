package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class EnvelopesTest : MonthlyPlanTestBase() {
    private val oct = YearMonth.of(2026, 10)
    private val foodEnv = Envelope("env-food", "Food", 0)
    private val funEnv = Envelope("env-fun", "Fun", 1)

    private fun cat(
        id: String,
        groupId: String? = null,
        archived: Boolean = false,
    ) = Category(
        id = CategoryId(id),
        customName = id,
        iconKey = "icon",
        colorKey = "slate",
        kind = CategoryKind.SPENDING,
        order = 0,
        archived = archived,
        builtIn = false,
        groupId = groupId,
    )

    private val dinners = cat("dinners", "env-food")
    private val snacks = cat("snacks", "env-food")
    private val movies = cat("movies", "env-fun")
    private val rent = cat("rent")
    private val all = listOf(dinners, snacks, movies, rent)

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun envelopes(
        buckets: List<CategoryBudget>,
        envelopes: List<Envelope> = listOf(foodEnv, funEnv),
        days: Int = 20,
        categories: List<Category> = all,
    ) = envelopesOf(buckets, categories, envelopes, PlanMonth(oct, days))

    @Test
    fun amount_isTheSumOfItsRowsPlanned() {
        val progress = envelopes(listOf(bucket(dinners, 10_000), bucket(snacks, 8_000))).envelopes.first()

        assertEquals(18_000L, progress.amountCents)
        assertEquals(listOf(dinners, snacks), progress.rows.map { it.category })
    }

    @Test
    fun recurringRows_makeUpTheAmount_andFlagRecurringPlanned() {
        val recurringRow = CategoryBudget(dinners, 0, planned = 3_000, plannedSource = PlannedSource.RECURRING)

        val progress = envelopes(listOf(recurringRow)).envelopes.first()

        assertEquals(3_000L, progress.amountCents)
        assertTrue(progress.hasRecurringPlanned)
    }

    @Test
    fun nothingPlanned_amountIsNull_andOnlySpendIsCommitted() {
        val progress = envelopes(listOf(bucket(dinners, null, spent = 2_500))).envelopes.first()

        assertNull(progress.amountCents)
        assertFalse(progress.isOver)
        assertEquals(2_500L, progress.committedCents)
    }

    @Test
    fun emptyEnvelope_listedWithNoRows() {
        val summary = envelopes(emptyList())

        assertEquals(listOf("env-food", "env-fun"), summary.envelopes.map { it.envelope.id })
        assertTrue(summary.envelopes.all { it.rows.isEmpty() && it.amountCents == null && it.spentCents == 0L })
    }

    @Test
    fun committed_isPooledOverTheEnvelope() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 15_000), bucket(snacks, 8_000, spent = 1_000))

        val progress = envelopes(buckets).envelopes.first()

        assertEquals(18_000L, progress.amountCents)
        assertEquals(16_000L, progress.spentCents)
        assertEquals(18_000L, progress.committedCents)
        assertFalse(progress.isOver)
    }

    @Test
    fun isOver_whenSpendExceedsTheAmount_committedIsTheSpend() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 12_500), bucket(snacks, 8_000, spent = 6_000))

        val progress = envelopes(buckets).envelopes.first()

        assertTrue(progress.isOver)
        assertEquals(18_500L, progress.committedCents)
    }

    @Test
    fun unenveloped_isTheCategoriesInNoOrAnUnknownEnvelope_withSomethingPlannedOrSpent() {
        val orphan = cat("orphan", "gone")
        val idle = cat("idle")
        val buckets =
            listOf(
                bucket(rent, 30_000),
                bucket(orphan, null, spent = 4_000),
                bucket(idle, null),
                bucket(dinners, 1_000),
            )

        val summary = envelopes(buckets, categories = all + orphan + idle)

        assertEquals(listOf(rent, orphan), summary.unenveloped.map { it.category })
        assertEquals(30_000L, summary.unenvelopedPlannedCents)
        assertEquals(4_000L, summary.unenvelopedSpentCents)
    }

    @Test
    fun archivedCategoryWithSpend_stillCountsInItsEnvelope() {
        val old = cat("old", "env-food", archived = true)
        val buckets = listOf(bucket(dinners, 10_000, spent = 1_000), bucket(old, null, spent = 2_000))

        val progress = envelopes(buckets, categories = all + old).envelopes.first()

        assertEquals(3_000L, progress.spentCents)
        assertEquals(10_000L, progress.amountCents)
        assertEquals(10_000L, progress.committedCents)
    }

    @Test
    fun envelopesAreSortedByOrderThenId() {
        val list = listOf(Envelope("b", "B", 1), Envelope("a", "A", 1), Envelope("c", "C", 0))

        assertEquals(listOf("c", "a", "b"), envelopes(emptyList(), list).envelopes.map { it.envelope.id })
    }

    @Test
    fun emptyInputs_zeroLivre() {
        val summary = envelopesOf(emptyList(), emptyList(), emptyList(), PlanMonth(oct, 0))

        assertTrue(summary.envelopes.isEmpty())
        assertTrue(summary.unenveloped.isEmpty())
        assertEquals(0L, summary.freeCents)
    }
}
