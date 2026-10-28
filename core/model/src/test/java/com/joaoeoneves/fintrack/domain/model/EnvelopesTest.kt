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
    private fun income(
        id: String,
        amount: Long,
        order: Int = 0,
        basis: IncomeBasis = IncomeBasis.FIXED,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
        skip: Set<Int> = emptySet(),
    ) = PlannedIncome(
        id = id,
        label = id,
        amountCents = amount,
        order = order,
        basis = basis,
        frequency = frequency,
        monthOfYear = if (frequency == IncomeFrequency.YEARLY) 10 else null,
        skipMonths = skip,
    )

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun envelopes(
        buckets: List<CategoryBudget>,
        lines: List<PlannedIncome>,
        envelopes: List<Envelope> = listOf(foodEnv, funEnv),
        actual: List<Long> = emptyList(),
        days: Int = 20,
        categories: List<Category> = all,
    ) = envelopesOf(buckets, categories, envelopes, lines, received(*actual.toLongArray()), PlanMonth(oct, days))

    private val fundedFood = foodEnv.copy(fundedByLineId = "meal")

    @Test
    fun singleAmount_isTheMonthAmount_andIgnoresRowsPlanned() {
        val progress =
            envelopes(
                listOf(bucket(dinners, 10_000, spent = 3_000), bucket(snacks, 8_000)),
                listOf(income("salary", 100_000)),
                envelopes = listOf(foodEnv.copy(amountCents = 50_000)),
            ).envelopes.single()

        assertEquals(50_000L, progress.amountCents)
        assertEquals(3_000L, progress.spentCents)
        assertEquals(50_000L, progress.committedCents)
        assertFalse(progress.isOver)
    }

    @Test
    fun noAmount_isTheSumOfItsCategoriesLimits() {
        val progress = envelopes(listOf(bucket(dinners, 10_000), bucket(snacks, 8_000)), emptyList()).envelopes.first()

        assertEquals(18_000L, progress.amountCents)
        assertEquals(listOf(dinners, snacks), progress.rows.map { it.category })
    }

    @Test
    fun skipMonth_fallsBackToRecurringPlanned() {
        val recurringRow =
            CategoryBudget(
                dinners,
                limitCents = 10_000,
                spentCents = 0,
                planned = 3_000,
                plannedSource = PlannedSource.RECURRING,
                skipMonths = setOf(10),
            )

        val progress = envelopes(listOf(recurringRow), emptyList()).envelopes.first()

        assertEquals(3_000L, progress.amountCents)
        assertTrue(progress.hasRecurringPlanned)
    }

    @Test
    fun nothingPlanned_amountIsNull_andOnlySpendIsCommitted() {
        val progress = envelopes(listOf(bucket(dinners, null, spent = 2_500)), emptyList()).envelopes.first()

        assertNull(progress.amountCents)
        assertFalse(progress.isOver)
        assertEquals(2_500L, progress.committedCents)
    }

    @Test
    fun emptyEnvelope_listedWithNoRows() {
        val summary = envelopes(emptyList(), emptyList())

        assertEquals(listOf("env-food", "env-fun"), summary.envelopes.map { it.envelope.id })
        assertTrue(summary.envelopes.all { it.rows.isEmpty() && it.amountCents == null && it.spentCents == 0L })
    }

    @Test
    fun committed_isPooledOverTheEnvelope() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 15_000), bucket(snacks, 8_000, spent = 1_000))

        val progress = envelopes(buckets, emptyList()).envelopes.first()

        assertEquals(18_000L, progress.amountCents)
        assertEquals(16_000L, progress.spentCents)
        assertEquals(18_000L, progress.committedCents)
        assertFalse(progress.isOver)
    }

    @Test
    fun isOver_whenSpendExceedsTheAmount_committedIsTheSpend() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 12_500), bucket(snacks, 8_000, spent = 6_000))

        val progress = envelopes(buckets, emptyList()).envelopes.first()

        assertTrue(progress.isOver)
        assertEquals(18_500L, progress.committedCents)
    }

    @Test
    fun fundedSurplus_staysOutOfLivre() {
        val buckets = listOf(bucket(dinners, 16_000, spent = 5_000), bucket(rent, 30_000))
        val lines = listOf(income("salary", 200_000), income("meal", 20_000, order = 1))

        val summary = envelopes(buckets, lines, envelopes = listOf(fundedFood))

        val food = summary.envelopes.single()
        assertEquals(20_000L, food.fundingCents)
        assertEquals(15_000L, food.leftoverCents)
        assertEquals(0L, food.overflowCents)
        assertEquals(200_000L, summary.baseCents)
        assertEquals(30_000L, summary.committedCents)
        assertEquals(170_000L, summary.freeCents)
    }

    @Test
    fun fundedOverflow_spillsIntoLivre() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 25_000), bucket(rent, 30_000))
        val lines = listOf(income("salary", 200_000), income("meal", 20_000, order = 1))

        val summary = envelopes(buckets, lines, envelopes = listOf(fundedFood))

        val food = summary.envelopes.single()
        assertEquals(-5_000L, food.leftoverCents)
        assertEquals(5_000L, food.overflowCents)
        assertEquals(listOf(food), summary.overflowing)
        assertEquals(165_000L, summary.freeCents)
    }

    @Test
    fun fundedByAnExtraOrMissingLine_isUnfunded() {
        val buckets = listOf(bucket(dinners, 10_000))
        val extra = income("meal", 20_000, order = 1, frequency = IncomeFrequency.YEARLY)
        val salary = income("salary", 100_000)

        val viaExtra = envelopes(buckets, listOf(salary, extra), envelopes = listOf(fundedFood))
        val viaMissing = envelopes(buckets, listOf(salary), envelopes = listOf(fundedFood))

        listOf(viaExtra, viaMissing).forEach {
            assertNull(it.envelopes.single().fundingCents)
            assertNull(it.envelopes.single().fundingLine)
            assertEquals(0L, it.envelopes.single().overflowCents)
            assertEquals(90_000L, it.freeCents)
        }
    }

    @Test
    fun twoEnvelopesOnOneLine_onlyTheFirstByOrderIsFunded() {
        val buckets = listOf(bucket(dinners, 10_000), bucket(movies, 4_000))
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, order = 1))
        val second = funEnv.copy(fundedByLineId = "meal")

        val summary = envelopes(buckets, lines, envelopes = listOf(second, fundedFood))

        assertEquals(20_000L, summary.envelopes.first { it.envelope.id == "env-food" }.fundingCents)
        assertNull(summary.envelopes.first { it.envelope.id == "env-fun" }.fundingCents)
        assertEquals(100_000L - 4_000L, summary.freeCents)
    }

    @Test
    fun perWorkingDayFunding_multipliesByDays_andSkippedMonthFundsNothing() {
        val buckets = listOf(bucket(dinners, 5_000))
        val perDay = income("meal", 1_000, basis = IncomeBasis.PER_WORKING_DAY)

        assertEquals(
            20_000L,
            envelopes(buckets, listOf(perDay), listOf(fundedFood), days = 20).envelopes.single().fundingCents,
        )

        val skipped = envelopes(buckets, listOf(income("meal", 20_000, skip = setOf(10))), listOf(fundedFood))
        assertEquals(0L, skipped.envelopes.single().fundingCents)
        assertEquals(-5_000L, skipped.freeCents)
    }

    @Test
    fun actualIncomeAboveExpected_theExtraGoesToLivre_net_of_funding() {
        val buckets = listOf(bucket(dinners, 10_000), bucket(rent, 30_000))
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, order = 1))

        val summary = envelopes(buckets, lines, listOf(fundedFood), actual = listOf(150_000, 20_000))

        assertEquals(150_000L, summary.baseCents)
        assertEquals(120_000L, summary.freeCents)
    }

    @Test
    fun noFunding_livreEqualsSafeToSpend() {
        val buckets =
            listOf(
                bucket(rent, 30_000),
                bucket(dinners, 10_000, spent = 12_000),
                bucket(snacks, 8_000),
                bucket(movies, null, spent = 700),
            )
        val lines =
            listOf(
                income("salary", 200_000),
                income("meal", 5_000, order = 1, basis = IncomeBasis.PER_WORKING_DAY),
            )
        val list = listOf(foodEnv, funEnv, foodEnv.copy(id = "empty", name = "Empty", order = 2, amountCents = 9_000))

        listOf(emptyList(), listOf(100_000L), listOf(400_000L)).forEach { actual ->
            val summary = envelopes(buckets, lines, list, actual = actual)
            val plan = monthlyPlanOf(buckets, lines, received(*actual.toLongArray()), oct, 20, list)
            assertEquals(plan.safeToSpendCents, summary.freeCents)
        }
    }

    @Test
    fun fixedAmountEnvelope_livreCountsAmountEvenWithNoRows() {
        val list = listOf(foodEnv.copy(amountCents = 30_000))

        val summary = envelopes(emptyList(), listOf(income("salary", 100_000)), list)

        assertEquals(70_000L, summary.freeCents)
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

        val summary = envelopes(buckets, listOf(income("salary", 100_000)), categories = all + orphan + idle)

        assertEquals(listOf(rent, orphan), summary.unenveloped.map { it.category })
        assertEquals(30_000L, summary.unenvelopedPlannedCents)
        assertEquals(4_000L, summary.unenvelopedSpentCents)
        assertEquals(100_000L - 30_000L - 4_000L - 1_000L, summary.freeCents)
    }

    @Test
    fun archivedCategoryWithSpend_stillCountsInItsEnvelope() {
        val old = cat("old", "env-food", archived = true)
        val buckets = listOf(bucket(dinners, 10_000, spent = 1_000), bucket(old, null, spent = 2_000))

        val progress = envelopes(buckets, emptyList(), categories = all + old).envelopes.first()

        assertEquals(3_000L, progress.spentCents)
        assertEquals(10_000L, progress.amountCents)
        assertEquals(10_000L, progress.committedCents)
    }

    @Test
    fun envelopesAreSortedByOrderThenId() {
        val list = listOf(Envelope("b", "B", 1), Envelope("a", "A", 1), Envelope("c", "C", 0))

        assertEquals(listOf("c", "a", "b"), envelopes(emptyList(), emptyList(), list).envelopes.map { it.envelope.id })
    }

    @Test
    fun emptyInputs_zeroLivre() {
        val summary = envelopesOf(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), PlanMonth(oct, 0))

        assertTrue(summary.envelopes.isEmpty())
        assertTrue(summary.unenveloped.isEmpty())
        assertEquals(0L, summary.freeCents)
    }
}
