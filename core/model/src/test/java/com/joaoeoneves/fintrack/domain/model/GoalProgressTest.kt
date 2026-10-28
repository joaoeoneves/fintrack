package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GoalProgressTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 2) // day 183 of 365
    private val savings = userCategory("savings")
    private val linkedCategory = CategoryId("savings")

    private fun expense(
        id: String,
        cents: Long,
        date: LocalDate = LocalDate.of(2026, 3, 10),
        refund: Boolean = false,
        category: Category = BuiltInCategories.SHOPPING,
    ) = testExpense(id, cents, date.atStartOfDay(zone).toInstant(), category, refund)

    private fun progress(
        goal: SavingsGoal,
        expenses: List<Expense> = emptyList(),
        recurring: List<RecurringItem> = emptyList(),
        at: LocalDate = today,
    ) = goalProgressOf(goal, expenses, recurring, at, zone)

    @Test
    fun noContributions_isZero() {
        val result = progress(savingsGoal(linkedRecurringIds = setOf("a")))

        assertEquals(0L, result.savedCents)
        assertEquals(0f, result.fraction, 0f)
    }

    @Test
    fun recurringPrefix_countsOnlyThatItemsOccurrences() {
        val goal = savingsGoal(linkedRecurringIds = setOf("a"))
        val expenses = listOf(expense("rec_a_20260310", 1_000), expense("rec_b_20260310", 500), expense("other", 7))

        assertEquals(1_000L, progress(goal, expenses).savedCents)
    }

    @Test
    fun prefixMatch_doesNotCatchLongerIdsSharingTheStart() {
        val goal = savingsGoal(linkedRecurringIds = setOf("ab"))
        val expenses = listOf(expense("rec_abc_20260310", 900), expense("rec_ab_20260310", 100))

        assertEquals(100L, progress(goal, expenses).savedCents)
    }

    @Test
    fun category_countsExpensesFiledUnderIt() {
        val goal = savingsGoal(linkedCategoryId = linkedCategory)
        val expenses = listOf(expense("e1", 300, category = savings), expense("e2", 200))

        assertEquals(300L, progress(goal, expenses).savedCents)
    }

    @Test
    fun union_expenseMatchingBothLinks_countsOnce() {
        val goal = savingsGoal(linkedRecurringIds = setOf("a"), linkedCategoryId = linkedCategory)
        val both = expense("rec_a_20260310", 400, category = savings)
        val onlyCategory = expense("e2", 100, category = savings)
        val onlyRecurring = expense("rec_a_20260410", 50)

        assertEquals(550L, progress(goal, listOf(both, onlyCategory, onlyRecurring)).savedCents)
    }

    @Test
    fun duplicateExpenseIds_countOnce() {
        val goal = savingsGoal(linkedRecurringIds = setOf("a"))
        val expenses = listOf(expense("rec_a_20260310", 400), expense("rec_a_20260310", 400))

        assertEquals(400L, progress(goal, expenses).savedCents)
    }

    @Test
    fun refund_subtractsFromSaved() {
        val goal = savingsGoal(linkedCategoryId = linkedCategory)
        val expenses =
            listOf(
                expense("e1", 1_000, category = savings),
                expense("e2", 300, refund = true, category = savings),
            )

        assertEquals(700L, progress(goal, expenses).savedCents)
    }

    @Test
    fun refundsLargerThanContributions_goNegative() {
        val goal = savingsGoal(linkedCategoryId = linkedCategory)

        assertEquals(-50L, progress(goal, listOf(expense("e1", 50, refund = true, category = savings))).savedCents)
        assertEquals(0f, progress(goal, listOf(expense("e1", 50, refund = true, category = savings))).fraction, 0f)
    }

    @Test
    fun annual_countsOnlyTheCurrentCalendarYear_boundaries() {
        val goal = savingsGoal(linkedCategoryId = linkedCategory)
        val expenses =
            listOf(
                expense("lastDec", 10, LocalDate.of(2025, 12, 31), category = savings),
                expense("jan1", 20, LocalDate.of(2026, 1, 1), category = savings),
                expense("dec31", 40, LocalDate.of(2026, 12, 31), category = savings),
                expense("nextJan", 80, LocalDate.of(2027, 1, 1), category = savings),
            )

        assertEquals(60L, progress(goal, expenses).savedCents)
    }

    @Test
    fun annual_ignoresInitialAmount() {
        val goal = savingsGoal(initialCents = 5_000, linkedCategoryId = linkedCategory)

        assertEquals(100L, progress(goal, listOf(expense("e1", 100, category = savings))).savedCents)
    }

    @Test
    fun annual_yearBoundaryUsesTheZone() {
        val lisbonLike = ZoneOffset.ofHours(2)
        val goal = savingsGoal(linkedCategoryId = linkedCategory)
        // 2025-12-31T23:00Z is already 2026-01-01 at +02:00.
        val e = testExpense("e1", 100, java.time.Instant.parse("2025-12-31T23:00:00Z"), savings)

        assertEquals(100L, goalProgressOf(goal, listOf(e), emptyList(), today, lisbonLike).savedCents)
        assertEquals(0L, goalProgressOf(goal, listOf(e), emptyList(), today, ZoneOffset.UTC).savedCents)
    }

    @Test
    fun total_countsFromStartDate_plusInitial_acrossYears() {
        val goal =
            savingsGoal(
                targetType = GoalTargetType.TOTAL,
                startDate = LocalDate.of(2025, 6, 1),
                initialCents = 1_000,
                linkedCategoryId = linkedCategory,
            )
        val expenses =
            listOf(
                expense("before", 7, LocalDate.of(2025, 5, 31), category = savings),
                expense("onStart", 100, LocalDate.of(2025, 6, 1), category = savings),
                expense("thisYear", 200, LocalDate.of(2026, 2, 1), category = savings),
            )

        assertEquals(1_300L, progress(goal, expenses).savedCents)
    }

    @Test
    fun total_initialOnly_isSaved() {
        val goal = savingsGoal(targetType = GoalTargetType.TOTAL, initialCents = 2_500)

        assertEquals(2_500L, progress(goal).savedCents)
    }

    @Test
    fun expected_annual_isProRataByDayOfYear() {
        val goal = savingsGoal(targetCents = 36_500, startDate = LocalDate.of(2025, 1, 1))

        assertEquals(18_300L, expectedByToday(goal, today))
        assertEquals(100L, expectedByToday(goal, LocalDate.of(2026, 1, 1)))
        assertEquals(36_500L, expectedByToday(goal, LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun expected_annual_leapYearUses366() {
        val goal = savingsGoal(targetCents = 36_600, startDate = LocalDate.of(2027, 1, 1))

        assertEquals(36_600L, expectedByToday(goal, LocalDate.of(2028, 12, 31)))
        assertEquals(100L, expectedByToday(goal, LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun expected_total_withoutTargetDate_isNull() {
        assertNull(expectedByToday(savingsGoal(targetType = GoalTargetType.TOTAL), today))
        assertNull(progress(savingsGoal(targetType = GoalTargetType.TOTAL)).expectedCents)
    }

    @Test
    fun expected_total_isLinearBetweenStartAndTarget_clamped() {
        val goal =
            savingsGoal(
                targetCents = 10_000,
                targetType = GoalTargetType.TOTAL,
                startDate = LocalDate.of(2026, 1, 1),
                targetDate = LocalDate.of(2026, 1, 11),
            )

        assertEquals(0L, expectedByToday(goal, LocalDate.of(2025, 12, 1)))
        assertEquals(0L, expectedByToday(goal, LocalDate.of(2026, 1, 1)))
        assertEquals(5_000L, expectedByToday(goal, LocalDate.of(2026, 1, 6)))
        assertEquals(10_000L, expectedByToday(goal, LocalDate.of(2026, 1, 11)))
        assertEquals(10_000L, expectedByToday(goal, LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun expected_total_targetDateEqualsStart_isTargetOnceReached() {
        val day = LocalDate.of(2026, 5, 5)
        val goal = savingsGoal(targetType = GoalTargetType.TOTAL, startDate = day, targetDate = day)

        assertEquals(0L, expectedByToday(goal, day.minusDays(1)))
        assertEquals(36_500L, expectedByToday(goal, day))
    }

    @Test
    fun status_done_whenSavedReachesTarget() {
        val goal = savingsGoal(targetCents = 1_000, linkedCategoryId = linkedCategory)

        assertEquals(GoalStatus.DONE, progress(goal, listOf(expense("e", 1_000, category = savings))).status)
        assertEquals(GoalStatus.DONE, progress(goal, listOf(expense("e", 1_500, category = savings))).status)
        assertEquals(1f, progress(goal, listOf(expense("e", 1_500, category = savings))).fraction, 0f)
    }

    @Test
    fun status_behind_whenBelowExpected_andOnTrackAtOrAbove() {
        val goal = savingsGoal(linkedCategoryId = linkedCategory) // expected 18 300 today

        assertEquals(GoalStatus.BEHIND, progress(goal, listOf(expense("e", 18_299, category = savings))).status)
        assertEquals(GoalStatus.ON_TRACK, progress(goal, listOf(expense("e", 18_300, category = savings))).status)
        assertEquals(GoalStatus.ON_TRACK, progress(goal, listOf(expense("e", 30_000, category = savings))).status)
    }

    @Test
    fun status_total_withoutPace_isOnTrackUntilDone() {
        val goal = savingsGoal(targetType = GoalTargetType.TOTAL, targetCents = 1_000)

        assertEquals(GoalStatus.NOT_STARTED, progress(goal).status)
        assertEquals(GoalStatus.ON_TRACK, progress(goal.copy(initialCents = 100)).status)
        assertEquals(GoalStatus.DONE, progress(goal.copy(initialCents = 1_000)).status)
    }

    @Test
    fun fraction_isSavedOverTarget() {
        val goal = savingsGoal(targetCents = 1_000, linkedCategoryId = linkedCategory)

        assertEquals(0.25f, progress(goal, listOf(expense("e", 250, category = savings))).fraction, 0.0001f)
    }

    @Test
    fun plannedPerYear_monthlyTimesTwelve_yearlyOnce_forLinkedOnly() {
        val goal = savingsGoal(linkedRecurringIds = setOf("m", "y"))
        val items =
            listOf(
                recurringItem("m", amountCents = 1_000),
                recurringItem("y", amountCents = 700, frequency = RecurringFrequency.YEARLY),
                recurringItem("unlinked", amountCents = 99_999),
            )

        assertEquals(12_700L, plannedPerYear(goal, items, today))
        assertEquals(12_700L, progress(goal, recurring = items).plannedPerYearCents)
    }

    @Test
    fun plannedPerYear_excludesEndedPausedAndIncomeItems() {
        val goal = savingsGoal(linkedRecurringIds = setOf("ended", "paused", "income", "live"))
        val items =
            listOf(
                recurringItem("ended", amountCents = 100, endDate = LocalDate.of(2026, 6, 30)),
                recurringItem("paused", amountCents = 200, active = false),
                recurringItem("income", type = RecurringType.INCOME, amountCents = 400),
                recurringItem("live", amountCents = 10, endDate = today),
            )

        assertEquals(120L, plannedPerYear(goal, items, today))
    }

    @Test
    fun plannedPerYear_noLinks_isZero() {
        assertEquals(0L, plannedPerYear(savingsGoal(), listOf(recurringItem("a")), today))
    }

    @Test
    fun status_notStarted_whenNothingSavedAndNoPaceYet() {
        // Annual plan started this month: expected pace is 0, nothing saved.
        val goal = savingsGoal(targetCents = 36_500, startDate = LocalDate.of(2026, 7, 1))

        assertEquals(GoalStatus.NOT_STARTED, progress(goal).status)
    }

    @Test
    fun status_savedSomething_isNotNotStarted() {
        val goal =
            savingsGoal(
                targetCents = 36_500,
                startDate = LocalDate.of(2026, 7, 1),
                linkedCategoryId = linkedCategory,
            )
        val result = progress(goal, listOf(expense("e", 100, date = LocalDate.of(2026, 7, 1), category = savings)))

        assertEquals(GoalStatus.ON_TRACK, result.status)
    }

    @Test
    fun expected_annual_startedThisYear_countsFromFirstOfStartMonth() {
        val goal = savingsGoal(targetCents = 36_500, startDate = LocalDate.of(2026, 7, 20))

        assertEquals(0L, expectedByToday(goal, LocalDate.of(2026, 7, 31))) // first month
        // From Jul 1 to Dec 31 = 184 days; Aug 1 is the 32nd day.
        assertEquals(36_500L * 32 / 184, expectedByToday(goal, LocalDate.of(2026, 8, 1)))
        assertEquals(36_500L, expectedByToday(goal, LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun expected_annual_startedLastYear_countsFromJanFirst() {
        val goal = savingsGoal(targetCents = 36_500, startDate = LocalDate.of(2025, 7, 20))

        assertEquals(100L, expectedByToday(goal, LocalDate.of(2026, 1, 1)))
    }
}
