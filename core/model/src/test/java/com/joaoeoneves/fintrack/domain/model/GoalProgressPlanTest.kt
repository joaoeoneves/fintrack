package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Savings plans: the owned recurring item, "no target" goals, NO_MONTHLY and the suggested monthly amount. */
class GoalProgressPlanTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 2)
    private val monthly100 = recurringItem(id = "r1", amountCents = 10_000, dayOfMonth = 1)

    private fun expense(
        id: String,
        cents: Long,
        date: LocalDate = LocalDate.of(2026, 3, 10),
    ) = testExpense(id, cents, date.atStartOfDay(zone).toInstant())

    private fun progress(
        goal: SavingsGoal,
        expenses: List<Expense> = emptyList(),
        recurring: List<RecurringItem> = emptyList(),
        at: LocalDate = today,
    ) = goalProgressOf(goal, expenses, recurring, at, zone)

    @Test
    fun countedRecurringIds_unionOfLinkedAndOwned() {
        val both = savingsGoal(linkedRecurringIds = setOf("a"), recurringId = "r1")

        assertEquals(setOf("a", "r1"), both.countedRecurringIds)
        assertEquals(setOf("r1"), savingsGoal(recurringId = "r1").countedRecurringIds)
        assertEquals(emptySet<String>(), savingsGoal().countedRecurringIds)
    }

    @Test
    fun ownedItem_countsItsExpenses() {
        val goal = savingsGoal(targetCents = 0, recurringId = "r1")

        val expenses = listOf(expense("rec_r1_20260310", 700), expense("rec_r2_20260310", 5))

        assertEquals(700L, progress(goal, expenses).savedCents)
    }

    @Test
    fun sameItemOwnedAndLinked_isNotCountedTwice() {
        val goal = savingsGoal(linkedRecurringIds = setOf("r1"), recurringId = "r1")

        assertEquals(1_000L, progress(goal, listOf(expense("rec_r1_20260310", 1_000)), listOf(monthly100)).savedCents)
        assertEquals(120_000L, progress(goal, emptyList(), listOf(monthly100)).plannedPerYearCents)
    }

    @Test
    fun noTarget_usesTheYearlyPlannedAmountAsImplicitTarget() {
        val goal = savingsGoal(targetCents = 0, recurringId = "r1")
        val result = progress(goal, listOf(expense("rec_r1_20260310", 30_000)), listOf(monthly100))

        assertEquals(120_000L, result.effectiveTargetCents)
        assertEquals(0.25f, result.fraction, 0.0001f)
        assertNull(result.expectedCents)
        assertEquals(GoalStatus.ON_TRACK, result.status)
    }

    @Test
    fun ownTarget_winsOverTheImplicitOne() {
        val result = progress(savingsGoal(targetCents = 50_000, recurringId = "r1"), recurring = listOf(monthly100))

        assertEquals(50_000L, result.effectiveTargetCents)
    }

    @Test
    fun noTarget_neverBehind_evenWithNothingSaved() {
        val result = progress(savingsGoal(targetCents = 0, recurringId = "r1"), recurring = listOf(monthly100))

        assertEquals(GoalStatus.NOT_STARTED, result.status)
    }

    @Test
    fun noTarget_savedAtLeastYearlyPlan_isDone() {
        val goal = savingsGoal(targetCents = 0, recurringId = "r1")

        assertEquals(
            GoalStatus.DONE,
            progress(goal, listOf(expense("rec_r1_20260310", 120_000)), listOf(monthly100)).status,
        )
    }

    @Test
    fun ownedItemMissingOrInactive_isNoMonthly() {
        val goal = savingsGoal(targetCents = 0, recurringId = "r1")

        assertEquals(GoalStatus.NO_MONTHLY, progress(goal).status)
        assertEquals(GoalStatus.NO_MONTHLY, progress(goal, recurring = listOf(monthly100.copy(active = false))).status)
        assertEquals(
            GoalStatus.NO_MONTHLY,
            progress(goal, recurring = listOf(monthly100.copy(endDate = LocalDate.of(2026, 6, 1)))).status,
        )
    }

    @Test
    fun noMonthly_precedence_doneBeatsIt_andItBeatsBehind() {
        val behindOwner = savingsGoal(targetCents = 100_000, recurringId = "r1")
        val doneOwner = savingsGoal(targetCents = 1_000, recurringId = "r1")

        assertEquals(GoalStatus.NO_MONTHLY, progress(behindOwner).status) // would be BEHIND (expected > 0)
        assertEquals(
            GoalStatus.DONE,
            progress(doneOwner, listOf(expense("rec_r1_20260310", 1_000))).status,
        )
    }

    @Test
    fun goalWithoutOwnedItem_isNeverNoMonthly() {
        assertEquals(GoalStatus.BEHIND, progress(savingsGoal(targetCents = 100_000)).status)
        assertEquals(GoalStatus.NOT_STARTED, progress(savingsGoal(targetCents = 0)).status)
    }

    @Test
    fun suggested_annual_dividesTheRemainderOverTheMonthsLeftIncludingThisOne() {
        val goal = savingsGoal(targetCents = 120_000)

        assertEquals(10_000L, progress(goal).suggestedMonthlyCents(LocalDate.of(2026, 1, 15)))
        assertEquals(20_000L, progress(goal).suggestedMonthlyCents(today)) // 6 months left
        assertEquals(120_000L, progress(goal).suggestedMonthlyCents(LocalDate.of(2026, 12, 1))) // December: 1 month
    }

    @Test
    fun suggested_annual_roundsUp() {
        val october = LocalDate.of(2026, 10, 1) // 3 months left: 100 / 3 rounds up

        assertEquals(34L, progress(savingsGoal(targetCents = 100)).suggestedMonthlyCents(october))
    }

    @Test
    fun suggested_annual_subtractsWhatIsSaved() {
        val goal = savingsGoal(targetCents = 120_000, linkedRecurringIds = setOf("a"))
        val result = progress(goal, listOf(expense("rec_a_20260310", 60_000)))

        assertEquals(10_000L, result.suggestedMonthlyCents(today))
    }

    @Test
    fun suggested_total_usesMonthsToTheDate_atLeastOne() {
        val goal =
            savingsGoal(
                targetCents = 120_000,
                targetType = GoalTargetType.TOTAL,
                targetDate = LocalDate.of(2027, 7, 2),
            )

        assertEquals(10_000L, progress(goal).suggestedMonthlyCents(today)) // 12 months
        assertEquals(120_000L, progress(goal).suggestedMonthlyCents(LocalDate.of(2027, 6, 20))) // under a month: 1
        assertEquals(120_000L, progress(goal).suggestedMonthlyCents(LocalDate.of(2028, 1, 1))) // past the date: 1
    }

    @Test
    fun suggested_total_withoutDate_isNull() {
        assertNull(progress(savingsGoal(targetType = GoalTargetType.TOTAL)).suggestedMonthlyCents(today))
    }

    @Test
    fun suggested_noTarget_isNull_andDoneIsZero() {
        assertNull(progress(savingsGoal(targetCents = 0)).suggestedMonthlyCents(today))
        val goal = savingsGoal(targetCents = 100, linkedRecurringIds = setOf("a"))
        val done = progress(goal, listOf(expense("rec_a_20260310", 500)))

        assertEquals(0L, done.suggestedMonthlyCents(today))
    }

    @Test
    fun suggested_noTargetPlan_usesImplicitTarget() {
        val goal = savingsGoal(targetCents = 0, recurringId = "r1")

        assertEquals(20_000L, progress(goal, recurring = listOf(monthly100)).suggestedMonthlyCents(today))
    }
}
