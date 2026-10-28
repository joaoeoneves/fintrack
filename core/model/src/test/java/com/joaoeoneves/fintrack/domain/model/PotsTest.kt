package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class PotsTest : MonthlyPlanTestBase() {
    private val oct = YearMonth.of(2026, 10)
    private val foodGroup = CategoryGroup("g-food", "Food", 0)
    private val groups = listOf(foodGroup)

    private fun cat(
        id: String,
        groupId: String? = null,
    ) = Category(
        id = CategoryId(id),
        customName = id,
        iconKey = "icon",
        colorKey = "slate",
        kind = CategoryKind.SPENDING,
        order = 0,
        archived = false,
        builtIn = false,
        groupId = groupId,
    )

    private val dinners = cat("dinners", "g-food")
    private val snacks = cat("snacks", "g-food")
    private val rent = cat("rent")

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun income(
        id: String,
        amount: Long,
        earmark: Earmark? = null,
        basis: IncomeBasis = IncomeBasis.FIXED,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
        skip: Set<Int> = emptySet(),
        order: Int = 0,
    ) = PlannedIncome(
        id = id,
        label = id,
        amountCents = amount,
        order = order,
        basis = basis,
        frequency = frequency,
        monthOfYear = if (frequency == IncomeFrequency.YEARLY) 10 else null,
        yearMonth = if (frequency == IncomeFrequency.ONCE) oct else null,
        skipMonths = skip,
        earmark = earmark,
    )

    private fun pots(
        buckets: List<CategoryBudget>,
        lines: List<PlannedIncome>,
        actual: List<Long> = emptyList(),
        days: Int = 20,
    ) = potsOf(buckets, groups, lines, received(*actual.toLongArray()), PlanMonth(oct, days))

    private val food = Earmark.Group("g-food")

    @Test
    fun noEarmarks_baseFreeEqualsSafeToSpend() {
        val buckets = listOf(bucket(rent, 30_000), bucket(dinners, 10_000, spent = 12_000))
        val lines = listOf(line(200_000), line(5_000, "meal").copy(basis = IncomeBasis.PER_WORKING_DAY))

        val summary = pots(buckets, lines, actual = listOf(100_000))

        assertTrue(summary.pots.isEmpty())
        assertEquals(monthlyPlanOf(buckets, lines, received(100_000), oct, 20).safeToSpendCents, summary.base.freeCents)
    }

    @Test
    fun noEarmarks_actualAboveExpected_matchesPlan() {
        val buckets = listOf(bucket(rent, 30_000, spent = 10_000))
        val lines = listOf(line(100_000))

        val summary = pots(buckets, lines, actual = listOf(250_000))

        assertEquals(monthlyPlanOf(buckets, lines, received(250_000), oct, 20).safeToSpendCents, summary.base.freeCents)
    }

    @Test
    fun surplus_staysInPot() {
        val buckets = listOf(bucket(dinners, 16_000, spent = 5_000), bucket(rent, 30_000))
        val lines = listOf(income("salary", 200_000), income("meal", 20_000, earmark = food))

        val summary = pots(buckets, lines)

        val pot = summary.pots.single()
        assertEquals(food, pot.target)
        assertEquals(foodGroup, pot.group)
        assertEquals(20_000L, pot.expectedIncomeCents)
        assertEquals(16_000L, pot.committedCents)
        assertEquals(4_000L, pot.freeCents)
        assertEquals(200_000L, summary.base.expectedIncomeCents)
        assertEquals(170_000L, summary.base.freeCents)
    }

    @Test
    fun spill_overspentPotDeficitReducesBase() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 25_000), bucket(rent, 30_000))
        val lines = listOf(income("salary", 200_000), income("meal", 20_000, earmark = food))

        val summary = pots(buckets, lines)

        assertEquals(-5_000L, summary.pots.single().freeCents)
        assertEquals(30_000L + 5_000L, summary.base.committedCents)
        assertEquals(165_000L, summary.base.freeCents)
    }

    @Test
    fun spill_perCategoryCommittedUsesMaxOfPlannedAndSpent() {
        val buckets = listOf(bucket(dinners, 10_000, spent = 15_000), bucket(snacks, 8_000, spent = 1_000))
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, earmark = food))

        val pot = pots(buckets, lines).pots.single()

        assertEquals(23_000L, pot.committedCents)
        assertEquals(-3_000L, pot.freeCents)
    }

    @Test
    fun categoryEarmark_beatsGroupEarmark() {
        val buckets = listOf(bucket(dinners, 10_000), bucket(snacks, 4_000))
        val lines =
            listOf(
                income("meal", 20_000, earmark = food, order = 0),
                income("treat", 7_000, earmark = Earmark.Category("snacks"), order = 1),
            )

        val summary = pots(buckets, lines)

        val groupPot = summary.pots.first { it.target == food }
        val categoryPot = summary.pots.first { it.target == Earmark.Category("snacks") }
        assertEquals(10_000L, groupPot.committedCents)
        assertEquals(4_000L, categoryPot.committedCents)
        assertEquals(3_000L, categoryPot.freeCents)
        assertEquals(listOf(food, Earmark.Category("snacks")), summary.pots.map { it.target })
        assertEquals(snacks, categoryPot.category)
    }

    @Test
    fun severalLinesOnOneTarget_sum() {
        val lines =
            listOf(
                income("a", 10_000, earmark = food, order = 0),
                income("b", 5_000, earmark = food, order = 1),
            )

        val summary = pots(listOf(bucket(dinners, 6_000)), lines)

        assertEquals(1, summary.pots.size)
        assertEquals(15_000L, summary.pots.single().expectedIncomeCents)
        assertEquals(9_000L, summary.pots.single().freeCents)
    }

    @Test
    fun danglingGroup_countsAsUnearmarked() {
        val buckets = listOf(bucket(dinners, 10_000))
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, earmark = Earmark.Group("gone")))

        val summary = pots(buckets, lines)

        assertTrue(summary.pots.isEmpty())
        assertEquals(120_000L, summary.base.expectedIncomeCents)
        assertEquals(110_000L, summary.base.freeCents)
    }

    @Test
    fun danglingCategory_countsAsUnearmarked() {
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, earmark = Earmark.Category("gone")))

        val summary = pots(listOf(bucket(rent, 10_000)), lines)

        assertTrue(summary.pots.isEmpty())
        assertEquals(110_000L, summary.base.freeCents)
    }

    @Test
    fun actualIncomeAboveExpected_extraGoesToBase() {
        val buckets = listOf(bucket(dinners, 10_000), bucket(rent, 30_000))
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, earmark = food))

        val summary = pots(buckets, lines, actual = listOf(150_000, 20_000))

        assertEquals(170_000L - 20_000L, summary.base.expectedIncomeCents)
        assertEquals(10_000L, summary.pots.single().freeCents)
        assertEquals(150_000L - 30_000L, summary.base.freeCents)
    }

    @Test
    fun actualIncomeBelowExpected_baseUsesExpected() {
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, earmark = food))

        val summary = pots(listOf(bucket(rent, 30_000)), lines, actual = listOf(10_000))

        assertEquals(100_000L, summary.base.expectedIncomeCents)
    }

    @Test
    fun perWorkingDayEarmarkedLine_multipliesByDays() {
        val lines = listOf(income("meal", 1_000, earmark = food, basis = IncomeBasis.PER_WORKING_DAY))

        val pot = pots(listOf(bucket(dinners, 5_000)), lines, days = 20).pots.single()

        assertEquals(20_000L, pot.expectedIncomeCents)
        assertEquals(15_000L, pot.freeCents)
    }

    @Test
    fun perWorkingDayEarmarkedLine_zeroDays_noPot() {
        val lines = listOf(income("meal", 1_000, earmark = food, basis = IncomeBasis.PER_WORKING_DAY))

        val summary = pots(listOf(bucket(dinners, 5_000)), lines, days = 0)

        assertTrue(summary.pots.isEmpty())
        assertEquals(-5_000L, summary.base.freeCents)
    }

    @Test
    fun earmarkedLineInSkippedMonth_hasNoPot_andBucketsFallToBase() {
        val buckets = listOf(bucket(dinners, 10_000))
        val lines = listOf(income("salary", 100_000), income("meal", 20_000, earmark = food, skip = setOf(10)))

        val summary = pots(buckets, lines)

        assertTrue(summary.pots.isEmpty())
        assertEquals(100_000L, summary.base.expectedIncomeCents)
        assertEquals(90_000L, summary.base.freeCents)
    }

    @Test
    fun earmarkedLine_activeInOtherMonth_getsPot() {
        val lines = listOf(income("meal", 20_000, earmark = food, skip = setOf(8)))

        assertEquals(1, pots(listOf(bucket(dinners, 10_000)), lines).pots.size)
    }

    @Test
    fun yearlyAndOnceLines_cannotBeEarmarked() {
        val lines =
            listOf(
                income("salary", 100_000),
                income("bonus", 50_000, earmark = food, frequency = IncomeFrequency.YEARLY),
                income("gift", 30_000, earmark = food, frequency = IncomeFrequency.ONCE),
            )

        val summary = pots(listOf(bucket(dinners, 10_000)), lines)

        assertTrue(summary.pots.isEmpty())
        assertEquals(100_000L, summary.base.expectedIncomeCents)
    }

    @Test
    fun emptyInputs_zeroBase() {
        val summary = potsOf(emptyList(), emptyList(), emptyList(), emptyList(), PlanMonth(oct, 0))

        assertTrue(summary.pots.isEmpty())
        assertEquals(0L, summary.base.freeCents)
        assertNotNull(summary.base)
    }

    @Test
    fun bucketWithoutPlan_countsItsSpendingOnly() {
        val buckets = listOf(bucket(dinners, null, spent = 3_000))
        val lines = listOf(income("meal", 10_000, earmark = food))

        val pot = pots(buckets, lines).pots.single()

        assertEquals(0L, pot.plannedCents)
        assertEquals(3_000L, pot.spentCents)
        assertEquals(7_000L, pot.freeCents)
    }
}
