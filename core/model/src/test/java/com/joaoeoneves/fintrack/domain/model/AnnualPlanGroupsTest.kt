package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualPlanGroupsTest {
    private val food = planGroup("food", order = 0)
    private val invest = planGroup("invest", order = 1)
    private val dinners = planCategory("dinners", "food")
    private val snacks = planCategory("snacks", "food")
    private val savings = planCategory("savings", "invest")
    private val loose = planCategory("loose")

    private fun plan(
        budgets: List<Budget> = emptyList(),
        recurring: List<RecurringItem> = emptyList(),
        expenses: List<Expense> = emptyList(),
        year: Int = PLAN_YEAR,
    ) = annualPlanOf(
        year,
        planInputs(
            budgets = budgets,
            recurring = recurring,
            categories = listOf(dinners, snacks, savings, loose),
            groups = listOf(food, invest),
            expenses = expenses,
        ),
    )

    private fun AnnualPlan.planned(id: String) = groups.first { it.group?.id == id }.plannedCents

    @Test
    fun foodGroup_sumsBudgetsOverTwelveMonths_honouringSkipMonths() {
        val result =
            plan(
                budgets =
                    listOf(
                        Budget(dinners.id, 16_000L),
                        Budget(snacks.id, 1_500L, skipMonths = setOf(8)),
                    ),
            )

        assertEquals(208_500L, result.planned("food"))
    }

    @Test
    fun investments_twoMonthlyRecurringItems_timesTwelve() {
        val result =
            plan(
                recurring =
                    listOf(
                        planMonthly("a", savings, 30_000L),
                        planMonthly("b", savings, 70_000L),
                    ),
            )

        assertEquals(1_200_000L, result.planned("invest"))
    }

    @Test
    fun recurring_monthlyTimesTwelve_plusYearlyOnceEach() {
        val items =
            listOf(
                planMonthly("m1", loose, 650L),
                planMonthly("m2", loose, 4_500L),
                planYearly("y1", loose, 1_999L, month = 3),
                planYearly("y2", loose, 1_255L, month = 11),
            )

        val result = plan(recurring = items)

        assertEquals(12 * (650L + 4_500L) + 1_999L + 1_255L, result.plannedCents)
        assertEquals(listOf(3, 11), result.subscriptions.map { it.month })
        assertEquals(listOf("y1", "y2"), result.subscriptions.map { it.item.id })
    }

    @Test
    fun itemStartingMidYear_countsOnlyRemainingMonths() {
        val result = plan(recurring = listOf(planMonthly("late", dinners, 1_000L, startMonth = 7)))

        assertEquals(6_000L, result.planned("food"))
    }

    @Test
    fun itemEndingMidYear_countsOnlyMonthsUntilEnd() {
        val item = planMonthly("short", dinners, 1_000L).copy(endDate = java.time.LocalDate.of(PLAN_YEAR, 4, 30))

        assertEquals(4_000L, plan(recurring = listOf(item)).planned("food"))
    }

    @Test
    fun yearlyItemStartingAfterItsMonth_isNotThisYearAndNotASubscription() {
        val item = planYearly("y", dinners, 5_000L, month = 2).copy(startDate = java.time.LocalDate.of(PLAN_YEAR, 3, 1))

        val result = plan(recurring = listOf(item))

        assertEquals(0L, result.plannedCents)
        assertTrue(result.subscriptions.isEmpty())
    }

    @Test
    fun inactiveAndIncomeItems_areIgnored() {
        val inactive = planMonthly("off", dinners, 1_000L).copy(active = false)
        val income =
            planMonthly("inc", dinners, 1_000L).copy(type = RecurringType.INCOME, categoryId = null)

        val result = plan(recurring = listOf(inactive, income))

        assertEquals(0L, result.plannedCents)
    }

    @Test
    fun leapYear_recurringOnDay29And31_stillTwelveOccurrences() {
        val items =
            listOf(
                planMonthly("d29", dinners, 100L, year = 2024).copy(dayOfMonth = 29),
                planMonthly("d31", dinners, 10L, year = 2024).copy(dayOfMonth = 31),
            )

        val result = plan(recurring = items, year = 2024)

        assertEquals(12 * 110L, result.planned("food"))
    }

    @Test
    fun budgetWinsOverRecurring_inItsMonths_andRecurringFillsSkippedMonths() {
        val result =
            plan(
                budgets = listOf(Budget(dinners.id, 1_000L, skipMonths = setOf(1, 2))),
                recurring = listOf(planMonthly("r", dinners, 300L)),
            )

        assertEquals(10 * 1_000L + 2 * 300L, result.planned("food"))
    }

    @Test
    fun otherGroup_shownOnlyWhenItHasMoney() {
        assertTrue(plan().groups.none { it.group == null })

        val planned = plan(budgets = listOf(Budget(loose.id, 500L)))
        val other = planned.groups.last()
        assertNull(other.group)
        assertEquals(6_000L, other.plannedCents)

        val spentOnly = plan(expenses = listOf(planExpense("e", loose, 700L)))
        assertEquals(700L, spentOnly.groups.last().actualCents)
        assertEquals(0L, spentOnly.groups.last().plannedCents)
    }

    @Test
    fun definedGroups_areAlwaysListed_inOrder() {
        val groups = plan().groups

        assertEquals(listOf("food", "invest"), groups.map { it.group?.id })
        assertEquals(0L, groups.sumOf { it.plannedCents })
    }

    @Test
    fun categoryWithDanglingGroup_fallsIntoOther() {
        val orphan = planCategory("orphan", "gone")
        val result =
            annualPlanOf(
                PLAN_YEAR,
                planInputs(
                    budgets = listOf(Budget(orphan.id, 100L)),
                    categories = listOf(orphan),
                    groups = listOf(food),
                ),
            )

        assertNull(result.groups.last().group)
        assertEquals(1_200L, result.groups.last().plannedCents)
    }

    @Test
    fun yearActuals_areNetOfRefunds() {
        val result =
            plan(
                expenses =
                    listOf(
                        planExpense("a", dinners, 10_000L, month = 2),
                        planExpense("b", dinners, 4_000L, month = 9, refund = true),
                        planExpense("c", snacks, 500L, month = 5),
                    ),
            )

        assertEquals(6_500L, result.groups.first { it.group?.id == "food" }.actualCents)
    }

    @Test
    fun yearActuals_categoryRefundedMoreThanSpent_isFlooredAtZero() {
        val result =
            plan(
                expenses =
                    listOf(
                        planExpense("a", dinners, 1_000L),
                        planExpense("b", dinners, 3_000L, refund = true),
                        planExpense("c", snacks, 500L),
                    ),
            )

        assertEquals(500L, result.groups.first { it.group?.id == "food" }.actualCents)
    }

    @Test
    fun isOver_onlyWhenSpentExceedsAPositivePlan() {
        val over = AnnualGroup(food, plannedCents = 100L, actualCents = 101L)

        assertTrue(over.isOver)
        assertTrue(!over.copy(actualCents = 100L).isOver)
        assertTrue(!over.copy(plannedCents = 0L).isOver)
    }
}
