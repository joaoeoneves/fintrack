package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnnualPlanGoalsTest {
    private fun plan(
        goals: List<SavingsGoal>,
        recurring: List<RecurringItem> = emptyList(),
        lines: List<PlannedIncome> = emptyList(),
    ) = annualPlanOf(PLAN_YEAR, planInputs(lines = lines, recurring = recurring, goals = goals))

    private fun AnnualPlan.plannedOf(id: String) = goals.single { it.goal.id == id }.plannedCents

    @Test
    fun noGoals_isEmpty() {
        assertTrue(plan(emptyList()).goals.isEmpty())
    }

    @Test
    fun goalWithoutLinks_plansZero() {
        assertEquals(0L, plan(listOf(savingsGoal())).plannedOf("g1"))
    }

    @Test
    fun monthlyItem_countsTwelveOccurrences_yearlyOnce() {
        val goal = savingsGoal(linkedRecurringIds = setOf("m", "y"))
        val items =
            listOf(
                recurringItem("m", amountCents = 1_000),
                recurringItem("y", amountCents = 700, frequency = RecurringFrequency.YEARLY),
            )

        assertEquals(12_700L, plan(listOf(goal), items).plannedOf("g1"))
    }

    @Test
    fun itemStartingMidYear_orEndingMidYear_countsOnlyItsMonths() {
        val goal = savingsGoal(linkedRecurringIds = setOf("late", "early"))
        val items =
            listOf(
                recurringItem("late", amountCents = 1_000, startDate = LocalDate.of(2026, 7, 1)),
                recurringItem("early", amountCents = 100, endDate = LocalDate.of(2026, 3, 31)),
            )

        assertEquals(6_000L + 300L, plan(listOf(goal), items).plannedOf("g1"))
    }

    @Test
    fun pausedAndIncomeItems_andUnlinkedOnes_areIgnored() {
        val goal = savingsGoal(linkedRecurringIds = setOf("paused", "income", "live"))
        val items =
            listOf(
                recurringItem("paused", amountCents = 500, active = false),
                recurringItem("income", type = RecurringType.INCOME, amountCents = 500),
                recurringItem("live", amountCents = 10),
                recurringItem("unlinked", amountCents = 9_999),
            )

        assertEquals(120L, plan(listOf(goal), items).plannedOf("g1"))
    }

    @Test
    fun sharedItem_countsTowardsEachGoal() {
        val links = setOf("m")
        val goals = listOf(savingsGoal("a", linkedRecurringIds = links), savingsGoal("b", linkedRecurringIds = links))

        val result = plan(goals, listOf(recurringItem("m", amountCents = 100)))

        assertEquals(listOf(1_200L, 1_200L), result.goals.map { it.plannedCents })
    }

    @Test
    fun archivedGoals_areExcluded() {
        val result = plan(listOf(savingsGoal("a", archived = true), savingsGoal("b")))

        assertEquals(listOf("b"), result.goals.map { it.goal.id })
    }

    @Test
    fun goals_areSortedByOrderThenId() {
        val goals = listOf(savingsGoal("z", order = 0), savingsGoal("b", order = 1), savingsGoal("a", order = 1))

        assertEquals(listOf("z", "a", "b"), plan(goals).goals.map { it.goal.id })
    }

    @Test
    fun coversTarget_whenPlannedReachesTarget() {
        val goal = savingsGoal(targetCents = 12_000, linkedRecurringIds = setOf("m"))

        assertTrue(plan(listOf(goal), listOf(recurringItem("m", amountCents = 1_000))).goals.single().coversTarget)
        assertFalse(
            plan(listOf(goal), listOf(recurringItem("m", amountCents = 999))).goals.single().coversTarget,
        )
    }

    @Test
    fun goals_doNotChangeTheLeftoverOrPlannedTotals() {
        val lines = listOf(planFixed("salary", 500_000))
        val items = listOf(recurringItem("m", amountCents = 1_000))
        val without = plan(emptyList(), items, lines)

        val with = plan(listOf(savingsGoal(linkedRecurringIds = setOf("m"))), items, lines)

        assertEquals(without.leftoverCents, with.leftoverCents)
        assertEquals(without.plannedCents, with.plannedCents)
        assertEquals(without.envelopes, with.envelopes)
    }
}
